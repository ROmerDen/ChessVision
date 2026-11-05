package com.chessvision.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UciParserTest {
    @Test fun parsesCpInfo() {
        val i = UciParser.parseInfo(
            "info depth 18 seldepth 25 multipv 2 score cp -34 nodes 1000 nps 500000 hashfull 3 time 20 pv e7e5 g1f3 b8c6"
        )!!
        assertEquals(18, i.depth)
        assertEquals(2, i.multipv)
        assertEquals(Score.Cp(-34), i.score)
        assertFalse(i.isBound)
        assertEquals(listOf("e7e5", "g1f3", "b8c6"), i.pv)
    }

    @Test fun parsesMateAndDefaultsMultipv() {
        val i = UciParser.parseInfo("info depth 5 score mate 3 pv d1h5 g6h5")!!
        assertEquals(1, i.multipv)
        assertEquals(Score.Mate(3), i.score)
    }

    @Test fun detectsBounds() {
        assertTrue(UciParser.parseInfo("info depth 9 multipv 1 score cp 10 lowerbound pv e2e4")!!.isBound)
    }

    @Test fun ignoresNonScoreLines() {
        assertNull(UciParser.parseInfo("info string NNUE evaluation using nn-xxxx.nnue"))
        assertNull(UciParser.parseInfo("info depth 3 currmove e2e4 currmovenumber 1"))
        assertNull(UciParser.parseInfo("readyok"))
    }

    @Test fun parsesBestMove() {
        assertEquals("e2e4", UciParser.parseBestMove("bestmove e2e4 ponder e7e5"))
        assertNull(UciParser.parseBestMove("bestmove (none)"))
    }

    @Test fun formatsScores() {
        assertEquals("+0.35", Score.Cp(35).format())
        assertEquals("-1.20", Score.Cp(-120).format())
        assertEquals("#3", Score.Mate(3).format())
        assertEquals("-#2", Score.Mate(-2).format())
        assertEquals(Score.Cp(-35), Score.Cp(35).flip())
    }
}
