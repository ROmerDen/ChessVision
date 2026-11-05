package com.chessvision.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

val DEFAULT_GO_REPLY = listOf(
    "info string NNUE ok",
    "info depth 10 multipv 1 score cp 20 pv e2e4 e7e5",
    "info depth 10 multipv 2 score cp 10 pv d2d4 d7d5",
    "info depth 10 multipv 3 score cp 5 pv c2c4",
    "info depth 12 multipv 1 score cp 30 nodes 1 pv e2e4 c7c5",
    "info depth 12 multipv 2 score cp 12 lowerbound pv d2d4 d7d5",
    "info depth 12 multipv 3 score cp 8 pv g1f3",
    "info depth 12 multipv 4 score cp -50 pv a2a3",
    "bestmove e2e4 ponder c7c5",
)

/** Скриптованный «движок» с настраиваемыми сбоями. */
class ScriptedTransport : UciTransport {
    val sent = CopyOnWriteArrayList<String>()
    private val out = Channel<String>(Channel.UNLIMITED)
    @Volatile override var isAlive = true
    @Volatile var searching = false
    @Volatile var goReply: List<String>? = DEFAULT_GO_REPLY   // null = движок «зависает» на go
    @Volatile var dieOnGo = false
    @Volatile var failStart = false
    @Volatile var silentOnUci = false
    @Volatile var ignoreStop = false

    private fun emit(vararg l: String) = l.forEach { out.trySend(it) }

    override suspend fun start() {
        if (failStart) throw EngineTerminatedException("cannot start")
    }

    override suspend fun send(line: String) {
        sent += line
        when {
            line == "uci" -> if (!silentOnUci) emit("id name Fake", "uciok")
            line == "isready" -> emit("readyok")
            line.startsWith("go") -> {
                if (dieOnGo) { isAlive = false; out.close(); return }
                searching = true
                goReply?.let { emit(*it.toTypedArray()); searching = false }
            }
            line == "stop" -> if (searching && !ignoreStop) { emit("bestmove e2e4"); searching = false }
        }
    }

    override suspend fun readLine(): String =
        out.receiveCatching().getOrNull() ?: throw EngineTerminatedException("closed")

    override fun close() { isAlive = false; out.close() }
}

private suspend inline fun <reified T : Throwable> expect(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw e
    }
    throw AssertionError("expected ${T::class.simpleName}")
}

class UciEngineTest {
    private val start = FenValidator.START_FEN
    private val blackFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

    private suspend fun started(
        t: ScriptedTransport = ScriptedTransport(),
        cfg: EngineConfig = EngineConfig(),
    ): Pair<UciEngine, ScriptedTransport> {
        val e = UciEngine(t, cfg)
        e.start()
        return e to t
    }

    @Test fun startSendsHandshakeAndOptions() = runBlocking<Unit> {
        val (_, t) = started()
        assertEquals("uci", t.sent.first())
        assertTrue("setoption name MultiPV value 3" in t.sent)
        assertTrue("setoption name Hash value 64" in t.sent)
        assertEquals("isready", t.sent.last())
    }

    @Test fun analyzeReturnsTopThreeSorted() = runBlocking<Unit> {
        val (e, t) = started()
        val r = e.analyze(start, SearchLimit.Depth(12))
        assertTrue("position fen $start" in t.sent)
        assertTrue("go depth 12" in t.sent)
        assertEquals(listOf(1, 2, 3), r.lines.map { it.rank })      // multipv 4 отброшен
        assertEquals(Score.Cp(30), r.lines[0].scoreWhite)            // глубина 12 заменила 10
        assertEquals(Score.Cp(10), r.lines[1].scoreWhite)            // lowerbound проигнорирован
        assertEquals("g1f3", r.lines[2].firstMove)
        assertEquals("e2e4", r.bestMove)
    }

    @Test fun scoreIsFlippedToWhiteWhenBlackToMove() = runBlocking<Unit> {
        val (e, _) = started()
        val r = e.analyze(blackFen, SearchLimit.Depth(12))
        assertFalse(r.whiteToMove)
        assertEquals(Score.Cp(30), r.lines[0].scoreSideToMove)
        assertEquals(Score.Cp(-30), r.lines[0].scoreWhite)
    }

    @Test fun illegalPositionNeverReachesEngine() = runBlocking<Unit> {
        val (e, t) = started()
        // чёрный король под шахом ладьи, ход белых — нелегально
        expect<InvalidFenException> { e.analyze("4k3/8/8/8/8/8/4R3/4K3 w - - 0 1", SearchLimit.Depth(5)) }
        expect<InvalidFenException> { e.analyze("garbage", SearchLimit.Depth(5)) }
        assertTrue(t.sent.none { it.startsWith("go") || it.startsWith("position") })
    }

    @Test fun fewerThanThreeLinesAreNotInvented() = runBlocking<Unit> {
        val t = ScriptedTransport().apply {
            goReply = listOf(
                "info depth 8 multipv 1 score cp 15 pv e2e4",
                "info depth 8 multipv 2 score cp 5 pv d2d4",
                "bestmove e2e4",
            )
        }
        val (e, _) = started(t)
        assertEquals(2, e.analyze(start, SearchLimit.Depth(8)).lines.size)
    }

    @Test fun emptyPvIsIgnoredAndBestmoveNoneGivesNull() = runBlocking<Unit> {
        val t = ScriptedTransport().apply {
            goReply = listOf("info depth 5 multipv 1 score cp 10 pv", "bestmove (none)")
        }
        val (e, _) = started(t)
        val r = e.analyze("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3", SearchLimit.Depth(5))
        assertTrue(r.lines.isEmpty())
        assertEquals(null, r.bestMove)
    }

    @Test fun timeoutStopsSearchAndEngineStaysUsable() = runBlocking<Unit> {
        val t = ScriptedTransport().apply { goReply = null }
        val (e, _) = started(t)
        expect<EngineTimeoutException> { e.analyze(start, SearchLimit.Depth(5, timeoutMs = 200)) }
        assertTrue("stop" in t.sent)
        assertTrue(e.isRunning)
        t.goReply = DEFAULT_GO_REPLY
        assertEquals(3, e.analyze(start, SearchLimit.Depth(5)).lines.size)
    }

    @Test fun cancellationSendsStopAndLeavesNoStaleOutput() = runBlocking<Unit> {
        val t = ScriptedTransport().apply { goReply = null }
        val (e, _) = started(t)
        val job = launch(Dispatchers.Default) { e.analyze(start, SearchLimit.Depth(5, timeoutMs = 30_000)) }
        var waited = 0
        while (t.sent.none { it == "go depth 5" } && waited < 2_000) { delay(10); waited += 10 }
        job.cancelAndJoin()
        assertTrue("stop" in t.sent)
        assertTrue(e.isRunning)
        t.goReply = DEFAULT_GO_REPLY
        val r = e.analyze(blackFen, SearchLimit.Depth(5))
        assertEquals(3, r.lines.size)
        assertEquals("e2e4", r.bestMove)
    }

    @Test fun unresponsiveEngineAfterStopIsClosed() = runBlocking<Unit> {
        val t = ScriptedTransport().apply { goReply = null; ignoreStop = true }
        val (e, _) = started(t)
        expect<EngineTimeoutException> { e.analyze(start, SearchLimit.Depth(5, timeoutMs = 100)) }
        assertFalse(e.isRunning)
        assertFalse(t.isAlive)
    }

    @Test fun processDeathDuringAnalysisMarksEngineStopped() = runBlocking<Unit> {
        val t = ScriptedTransport().apply { dieOnGo = true }
        val (e, _) = started(t)
        expect<EngineTerminatedException> { e.analyze(start, SearchLimit.Depth(5)) }
        assertFalse(e.isRunning)
    }

    @Test fun startFailureClosesTransport() = runBlocking<Unit> {
        val t = ScriptedTransport().apply { failStart = true }
        expect<EngineTerminatedException> { UciEngine(t).start() }
        assertFalse(t.isAlive)
    }

    @Test fun handshakeTimeoutClosesTransport() = runBlocking<Unit> {
        val t = ScriptedTransport().apply { silentOnUci = true }
        expect<EngineTimeoutException> { UciEngine(t, EngineConfig(startupTimeoutMs = 150)).start() }
        assertFalse(t.isAlive)
    }

    @Test fun repeatedAnalysesOnOneProcess() = runBlocking<Unit> {
        val (e, _) = started()
        repeat(5) { assertEquals(3, e.analyze(if (it % 2 == 0) start else blackFen, SearchLimit.Depth(5)).lines.size) }
    }
}
