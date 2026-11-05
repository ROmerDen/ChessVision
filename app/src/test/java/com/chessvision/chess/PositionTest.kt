package com.chessvision.chess

import com.chessvision.engine.FenValidator
import com.chessvision.engine.InvalidFenException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {
    private fun perft(p: Position, depth: Int): Long {
        val moves = p.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(p.play(it), depth - 1) }
    }

    private fun perft(fen: String, depth: Int) = perft(Position.fromFen(fen), depth)

    @Test fun startFenRoundTrips() {
        assertEquals(FenValidator.START_FEN, Position.START.toFen())
    }

    @Test fun perftStartPosition() {
        assertEquals(20L, perft(FenValidator.START_FEN, 1))
        assertEquals(400L, perft(FenValidator.START_FEN, 2))
        assertEquals(8902L, perft(FenValidator.START_FEN, 3))
    }

    @Test fun perftKiwipete() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(48L, perft(fen, 1))
        assertEquals(2039L, perft(fen, 2))
        assertEquals(97862L, perft(fen, 3))
    }

    @Test fun perftEndgameWithEnPassantPins() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        assertEquals(14L, perft(fen, 1))
        assertEquals(191L, perft(fen, 2))
        assertEquals(2812L, perft(fen, 3))
    }

    @Test fun perftPromotionsAndCastlingRights() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        assertEquals(6L, perft(fen, 1))
        assertEquals(264L, perft(fen, 2))
        assertEquals(9467L, perft(fen, 3))
    }

    @Test fun perftPosition5() {
        val fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        assertEquals(44L, perft(fen, 1))
        assertEquals(1486L, perft(fen, 2))
        assertEquals(62379L, perft(fen, 3))
    }

    @Test fun detectsCheckmateAndStalemate() {
        val mate = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertTrue(mate.isCheckmate())
        assertFalse(mate.isStalemate())
        val stale = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(stale.isStalemate())
        assertFalse(stale.isCheckmate())
    }

    @Test fun rejectsIllegalPositions() {
        val bad = listOf(
            "4k3/8/8/8/8/8/4R3/4K3 w - - 0 1",                                // король противника под шахом
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN1 w KQkq - 0 1",       // K-рокировка без ладьи h1
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e6 0 1",      // ep без пешки
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1",      // ep на «не той» стороне
            "kk6/8/8/8/8/8/8/K7 w - - 0 1",                                   // два чёрных короля
        )
        for (f in bad) {
            try {
                Position.fromFen(f)
                throw AssertionError("should reject: $f")
            } catch (_: InvalidFenException) {
            }
        }
    }
}
