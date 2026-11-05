package com.chessvision.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {
    @Test fun castlingProducesCorrectFen() {
        val g = GameState.fromUciMoves(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1"))
        assertEquals("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 5 4", g.fen())
    }

    @Test fun enPassantCapturesThePawn() {
        val g = GameState.fromUciMoves(listOf("e2e4", "a7a6", "e4e5", "d7d5"))
        assertEquals("rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3", g.fen())
        val after = g.playUci("e5d6")
        assertEquals('P', after.current.pieceAt(Sq.parse("d6")))
        assertEquals(' ', after.current.pieceAt(Sq.parse("d5")))
        assertEquals(0, after.current.halfmove)
    }

    @Test fun promotionOffersFourChoicesAndPlaysQueen() {
        val g = GameState.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        val promos = g.current.legalMoves().filter { it.from == Sq.parse("a7") }.map { it.uci() }.toSet()
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), promos)
        assertEquals('Q', g.playUci("a7a8q").current.pieceAt(Sq.parse("a8")))
    }

    @Test fun rookCaptureRemovesCastlingRight() {
        val g = GameState.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").playUci("a1a8")
        assertEquals("Kk", g.current.castling)
    }

    @Test fun illegalMoveIsRejected() {
        try {
            GameState.start().playUci("e2e5")
            throw AssertionError("expected IllegalMoveException")
        } catch (_: IllegalMoveException) {
        }
    }

    @Test fun undoRestoresPreviousPosition() {
        val g = GameState.start().playUci("e2e4").playUci("e7e5")
        assertEquals(GameState.start().playUci("e2e4").fen(), g.undo().fen())
    }

    @Test fun resolveTransitionFindsSingleLegalMove() {
        val g = GameState.start()
        val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
        assertEquals(MoveMatch.Matched(Move.fromUci("e2e4")!!), g.resolveTransition(afterE4))
        assertEquals(MoveMatch.NoChange, g.resolveTransition(Position.START.placement()))
        assertEquals(MoveMatch.NoMatch, g.resolveTransition("rnbqkbnr/pppppppp/8/8/4P3/4P3/PPPP2PP/RNBQKBNR"))
        assertEquals(MoveMatch.NoMatch, g.resolveTransition("мусор"))
    }

    @Test fun resolveTransitionHandlesCastlingAndPromotion() {
        val castle = GameState.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val m = castle.resolveTransition("r3k2r/8/8/8/8/8/8/R4RK1") as MoveMatch.Matched
        assertEquals("e1g1", m.move.uci())
        val promo = GameState.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        val p = promo.resolveTransition("Q7/7k/8/8/8/8/8/K7") as MoveMatch.Matched
        assertEquals("a7a8q", p.move.uci())
    }

    @Test fun everyHistoryFenIsAcceptedByStrictParser() {
        val g = GameState.fromUciMoves(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5c6", "d7c6", "e1g1"))
        g.positions.forEach { assertTrue(Position.fromFen(it.toFen()).toFen() == it.toFen()) }
    }
}
