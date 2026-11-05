package com.chessvision.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Запускает исполняемый файл Stockfish как дочерний процесс и обменивается строками через stdin/stdout. */
class ProcessUciTransport(private val binary: File) : UciTransport {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val lines = Channel<String>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)

    override val isAlive: Boolean get() = process?.isAlive == true && !closed.get()

    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        check(process == null) { "already started" }
        val p = try {
            ProcessBuilder(binary.absolutePath).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw EngineTerminatedException("Не удалось запустить ${binary.path}: ${e.message}", e)
        }
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
        thread(name = "uci-reader", isDaemon = true) {
            try {
                p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { lines.trySend(it) }
            } catch (_: IOException) {
                // поток закрыт вместе с процессом
            } finally {
                // Канал закрывается только по EOF процесса: уже полученные строки (включая последнюю)
                // остаются доступны readLine(), после них readLine() бросает EngineTerminatedException.
                lines.close()
            }
        }
    }

    override suspend fun send(line: String): Unit = withContext(Dispatchers.IO) {
        val w = writer
        if (w == null || closed.get()) throw EngineTerminatedException("Движок не запущен или закрыт")
        try {
            w.write(line)
            w.write("\n")
            w.flush()
        } catch (e: IOException) {
            throw EngineTerminatedException("Не удалось отправить команду: ${e.message}", e)
        }
    }

    override suspend fun readLine(): String =
        lines.receiveCatching().getOrNull() ?: throw EngineTerminatedException("Процесс движка завершился")

    /**
     * Неблокирующее закрытие (можно вызывать с главного потока): просит движок выйти командой `quit`,
     * ждёт до 1 с и при необходимости убивает процесс. Повторные вызовы игнорируются.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val p = process ?: return
        val w = writer
        thread(name = "uci-reaper", isDaemon = true) {
            try {
                w?.apply { write("quit\n"); flush() }
            } catch (_: IOException) {
            }
            try { w?.close() } catch (_: IOException) {}
            try {
                if (!p.waitFor(1, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                    p.waitFor(1, TimeUnit.SECONDS)
                }
            } catch (_: InterruptedException) {
                p.destroyForcibly()
            }
        }
    }
}
