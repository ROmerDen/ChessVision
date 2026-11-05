package com.chessvision.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FenValidatorTest {
    @Test fun acceptsStartPosition() {
        val p = FenValidator.parse(FenValidator.START_FEN)
        assertTrue(p.whiteToMove)
        assertEquals(FenValidator.START_FEN, p.normalized)
    }

    @Test fun fillsMissingCounters() {
        val p = FenValidator.parse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3")
        assertFalse(p.whiteToMove)
        assertTrue(p.normalized.endsWith("e3 0 1"))
    }

    @Test fun rejectsBadInput() {
        val bad = listOf(
            "",
            "8/8/8/8/8/8/8/8 w - - 0 1",                                   // нет королей
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1",     // 7 вертикалей
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1",    // цвет
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e5 0 1",   // en passant
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\nquit", // инъекция команды
            "Pnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",    // пешка на 8-й
        )
        for (f in bad) {
            try {
                FenValidator.parse(f)
                throw AssertionError("should reject: $f")
            } catch (_: InvalidFenException) {
            }
        }
    }
}
