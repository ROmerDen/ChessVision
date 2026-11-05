package com.chessvision.engine

import com.chessvision.chess.Position
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Клиент UCI. start()/analyze() сериализуются Mutex-ом.
 *
 * Инварианты:
 *  - `go` отправляется только после `position`, обе команды — в NonCancellable-блоке, чтобы флаг
 *    `searching` точно отражал состояние движка;
 *  - при отмене или таймауте во время поиска отправляется `stop`, и вывод вычитывается до `bestmove`,
 *    поэтому следующий анализ не видит «хвост» предыдущего. Если движок не отвечает, он закрывается
 *    (isRunning станет false), а вызывающий создаёт новый;
 *  - в движок попадают только позиции, прошедшие строгую проверку [Position.fromFen].
 */
class UciEngine(
    private val transport: UciTransport,
    private val config: EngineConfig = EngineConfig(),
) : AutoCloseable {
    private val mutex = Mutex()
    @Volatile private var ready = false

    val isRunning: Boolean get() = ready && transport.isAlive

    suspend fun start() = mutex.withLock {
        check(!ready) { "already started" }
        try {
            transport.start()
            transport.send("uci")
            awaitLine(config.startupTimeoutMs, "uciok") { it == "uciok" }
            config.setOptionCommands().forEach { transport.send(it) }
            transport.send("isready")
            awaitLine(config.readyTimeoutMs, "readyok") { it == "readyok" }
            ready = true
        } catch (t: Throwable) {
            // любая ошибка инициализации (в том числе отмена): не оставляем висящий процесс
            ready = false
            transport.close()
            throw t
        }
    }

    suspend fun analyze(fen: String, limit: SearchLimit): AnalysisResult = mutex.withLock {
        check(isRunning) { "engine is not running" }
        val position = Position.fromFen(fen)          // InvalidFenException до обращения к движку
        val normalized = position.toFen()

        val best = HashMap<Int, PvLine>()
        var bestMove: String? = null
        var searching = false

        try {
            withContext(NonCancellable) {
                transport.send("position fen $normalized")
                searching = true
                transport.send(limit.toGoCommand())
            }
            withTimeout(limit.timeoutMs()) {
                while (true) {
                    val line = transport.readLine()
                    if (UciParser.isBestMove(line)) {
                        bestMove = UciParser.parseBestMove(line)
                        searching = false
                        break
                    }
                    val info = UciParser.parseInfo(line) ?: continue
                    if (info.isBound || info.multipv !in 1..config.multiPv) continue
                    val depth = info.depth ?: 0
                    val prev = best[info.multipv]
                    if (prev == null || depth >= prev.depth) {
                        best[info.multipv] = PvLine(
                            rank = info.multipv,
                            depth = depth,
                            scoreSideToMove = info.score,
                            scoreWhite = if (position.whiteToMove) info.score else info.score.flip(),
                            moves = info.pv,
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            if (searching) stopAndDrain()
            throw EngineTimeoutException("Движок не ответил за ${limit.timeoutMs()} мс")
        } catch (e: CancellationException) {
            if (searching) stopAndDrain()
            throw e
        } catch (e: EngineException) {
            // процесс умер или канал сломан
            ready = false
            transport.close()
            throw e
        }

        AnalysisResult(
            fen = normalized,
            whiteToMove = position.whiteToMove,
            lines = best.values.sortedBy { it.rank },
            bestMove = bestMove,
        )
    }

    private suspend fun stopAndDrain() = withContext(NonCancellable) {
        val drained = try {
            transport.send("stop")
            withTimeoutOrNull(2_000) {
                while (!UciParser.isBestMove(transport.readLine())) { /* пропускаем info */ }
                true
            }
        } catch (_: EngineException) {
            null
        }
        if (drained == null) {
            ready = false
            transport.close()
        }
    }

    private suspend fun awaitLine(timeoutMs: Long, what: String, predicate: (String) -> Boolean) {
        try {
            withTimeout(timeoutMs) {
                while (!predicate(transport.readLine())) { /* пропускаем */ }
            }
        } catch (e: TimeoutCancellationException) {
            throw EngineTimeoutException("Не получен $what за $timeoutMs мс")
        }
    }

    override fun close() {
        ready = false
        transport.close()
    }
}
