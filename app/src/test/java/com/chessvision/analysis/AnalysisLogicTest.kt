package com.chessvision.analysis

import com.chessvision.analysis.MoveClassifier.toCp
import com.chessvision.engine.Score
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisLogicTest {
    @Test fun limitsForDevice() {
        val big = EngineLimits.forDevice(cores = 8, totalMemMb = 6_000)
        assertEquals(4, big.maxThreads); assertEquals(256, big.maxHashMb)
        val small = EngineLimits.forDevice(cores = 2, totalMemMb = 1_500)
        assertEquals(1, small.maxThreads); assertEquals(93, small.maxHashMb)
        val tiny = EngineLimits.forDevice(cores = 1, totalMemMb = 100)
        assertEquals(16, tiny.maxHashMb)
    }

    @Test fun clampKeepsSettingsInsideLimits() {
        val l = EngineLimits.forDevice(4, 4_000)
        val s = l.clamp(EngineSettings(threads = 99, hashMb = 5_000, moveTimeMs = 10))
        assertEquals(EngineSettings(threads = 2, hashMb = 250, moveTimeMs = 500), s)
    }

    @Test fun mateScoresMapToLargeCp() {
        assertEquals(9_997, Score.Mate(3).toCp())
        assertEquals(-9_997, Score.Mate(-3).toCp())
    }

    @Test fun lossIsMeasuredAgainstBestMoveBeforeAndNeverNegative() {
        assertEquals(120, MoveClassifier.lossCp(Score.Cp(150), Score.Cp(30)))
        assertEquals(0, MoveClassifier.lossCp(Score.Cp(10), Score.Cp(40)))
        assertEquals(9_900, MoveClassifier.lossCp(Score.Mate(2), Score.Cp(100)))
    }

    @Test fun classificationThresholds() {
        assertEquals(MoveQuality.BEST, MoveClassifier.classify(0))
        assertEquals(MoveQuality.BEST, MoveClassifier.classify(500, "e2e4", "e2e4"))
        assertEquals(MoveQuality.GOOD, MoveClassifier.classify(30))
        assertEquals(MoveQuality.INACCURACY, MoveClassifier.classify(80))
        assertEquals(MoveQuality.MISTAKE, MoveClassifier.classify(200))
        assertEquals(MoveQuality.BLUNDER, MoveClassifier.classify(400))
    }
}
