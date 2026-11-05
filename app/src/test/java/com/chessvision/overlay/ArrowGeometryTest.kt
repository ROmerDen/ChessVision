package com.chessvision.overlay

import com.chessvision.chess.Sq
import com.chessvision.engine.PvLine
import com.chessvision.engine.Score
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowGeometryTest {
    private val board = BoardRect(left = 100f, top = 50f, size = 800f)       // клетка 100

    @Test fun centersForWhiteAtBottom() {
        assertEquals(Pt(150f, 800f), board.center(Sq.parse("a1")))
        assertEquals(Pt(850f, 100f), board.center(Sq.parse("h8")))
        assertEquals(Pt(550f, 500f), board.center(Sq.parse("e4")))
    }

    @Test fun centersForBlackAtBottom() {
        val b = board.copy(whiteAtBottom = false)
        assertEquals(Pt(850f, 100f), b.center(Sq.parse("a1")))
        assertEquals(Pt(150f, 800f), b.center(Sq.parse("h8")))
    }

    @Test fun squareAtIsInverseOfCenter() {
        for (flip in listOf(true, false)) {
            val b = board.copy(whiteAtBottom = flip)
            for (sq in 0..63) {
                val c = b.center(sq)
                assertEquals(sq, b.squareAt(c.x, c.y))
            }
        }
        assertNull(board.squareAt(10f, 10f))
        assertNull(board.squareAt(950f, 500f))
    }

    @Test fun polygonShapeForHorizontalArrow() {
        val pts = ArrowGeometry.polygon(Pt(0f, 0f), Pt(100f, 0f), thickness = 10f)
        assertEquals(7, pts.size)
        assertEquals(100f, pts.maxOf { it.x }, 1e-3f)               // кончик
        assertEquals(80f, pts[1].x, 1e-3f)                           // основание наконечника: длина 2*толщина
        assertTrue(ArrowGeometry.polygon(Pt(5f, 5f), Pt(5f, 5f), 10f).isEmpty())
    }

    @Test fun plannerDrawsOnlyReturnedLinesWithCorrectColours() {
        fun line(rank: Int, uci: String) = PvLine(rank, 10, Score.Cp(0), Score.Cp(0), listOf(uci))
        val arrows = ArrowPlanner.plan(listOf(line(1, "e2e4"), line(2, "zzzz"), line(3, "g1f3")), board)
        assertEquals(2, arrows.size)                                  // некорректный ход пропущен
        assertEquals(board.center(Sq.parse("e2")), arrows[0].from)
        assertEquals(board.center(Sq.parse("e4")), arrows[0].to)
        assertEquals(ArrowPalette.argb(1), arrows[0].argb)
        assertEquals(ArrowPalette.argb(3), arrows[1].argb)
        assertNotNull(ArrowPalette.argb(2))
        assertTrue(ArrowPlanner.plan(emptyList(), board).isEmpty())
    }
}
