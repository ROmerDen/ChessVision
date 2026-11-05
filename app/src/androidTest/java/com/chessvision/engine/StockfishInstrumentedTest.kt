package com.chessvision.engine

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Требует реальный libstockfish.so в jniLibs/arm64-v8a и arm64-устройство. */
@RunWith(AndroidJUnit4::class)
class StockfishInstrumentedTest {
    @Test fun startPositionGivesThreeLines() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val binary = try {
            EngineLocator.resolve(ctx)
        } catch (e: EngineNotFoundException) {
            assumeTrue(e.message, false); return@runBlocking
        }
        UciEngine(ProcessUciTransport(binary), EngineConfig()).use { engine ->
            engine.start()
            val r = engine.analyze(FenValidator.START_FEN, SearchLimit.Depth(12))
            assertEquals(3, r.lines.size)
            r.lines.forEach { assertTrue(it.moves.isNotEmpty()) }
            assertTrue(r.bestMove != null)
            // повторный анализ на том же процессе
            val r2 = engine.analyze(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", SearchLimit.Depth(10)
            )
            assertEquals(3, r2.lines.size)
        }
    }

    private fun engine(): UciEngine {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val binary = try { EngineLocator.resolve(ctx) } catch (e: EngineNotFoundException) {
            assumeTrue(e.message, false); throw e
        }
        return UciEngine(ProcessUciTransport(binary), EngineConfig())
    }

    @Test fun mateAndStalematePositionsHaveNoLines() = runBlocking<Unit> {
        engine().use { e ->
            e.start()
            val mate = e.analyze("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3", SearchLimit.Depth(5))
            assertTrue(mate.lines.isEmpty()); assertEquals(null, mate.bestMove)
            val stale = e.analyze("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", SearchLimit.Depth(5))
            assertTrue(stale.lines.isEmpty())
        }
    }

    @Test fun manySequentialAnalysesOnOneProcess() = runBlocking<Unit> {
        engine().use { e ->
            e.start()
            repeat(10) { assertEquals(3, e.analyze(FenValidator.START_FEN, SearchLimit.Depth(8)).lines.size) }
        }
    }

    @Test fun cancelledSearchDoesNotPolluteNextOne() = runBlocking<Unit> {
        engine().use { e ->
            e.start()
            val job = launch(Dispatchers.Default) { e.analyze(FenValidator.START_FEN, SearchLimit.Depth(40, timeoutMs = 60_000)) }
            delay(300)
            job.cancelAndJoin()
            val r = e.analyze("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", SearchLimit.Depth(8))
            assertEquals(3, r.lines.size)
            assertTrue(r.lines.all { it.depth in 1..40 })
        }
    }
}
