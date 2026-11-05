package com.chessvision.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Проверяет транспорт на shell-скриптах (Linux/macOS). Реальный Stockfish проверяется в androidTest. */
class ProcessUciTransportTest {
    private fun script(body: String): File {
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
        val f = File.createTempFile("fakeengine", ".sh")
        f.deleteOnExit()
        f.writeText("#!/bin/sh\n$body\n")
        f.setExecutable(true)
        return f
    }

    @Test fun exchangesLinesAndKeepsLastLineAfterQuit() = runBlocking<Unit> {
        val f = script(
            """while IFS= read -r line; do
  case "${'$'}line" in
    uci) echo "id name sh"; echo uciok;;
    quit) echo "last line"; exit 0;;
  esac
done"""
        )
        val t = ProcessUciTransport(f)
        t.start()
        t.send("uci")
        withTimeout(5_000) {
            assertEquals("id name sh", t.readLine())
            assertEquals("uciok", t.readLine())
            t.send("quit")
            assertEquals("last line", t.readLine())     // последняя строка не теряется
            try { t.readLine(); throw AssertionError("expected EngineTerminatedException") }
            catch (_: EngineTerminatedException) {}
        }
        t.close()
    }

    @Test fun closeKillsProcessThatIgnoresQuit() = runBlocking<Unit> {
        val f = script("exec sleep 30")
        val t = ProcessUciTransport(f)
        t.start()
        t.close()
        t.close()                                         // идемпотентно
        withTimeout(5_000) {
            try { t.readLine(); throw AssertionError("expected EngineTerminatedException") }
            catch (_: EngineTerminatedException) {}
        }
        assertFalse(t.isAlive)
    }

    @Test fun startFailureForMissingBinary() = runBlocking<Unit> {
        val t = ProcessUciTransport(File("/nonexistent/stockfish"))
        try { t.start(); throw AssertionError("expected EngineTerminatedException") }
        catch (_: EngineTerminatedException) {}
    }
}
