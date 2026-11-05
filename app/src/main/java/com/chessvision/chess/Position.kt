package com.chessvision.chess

import com.chessvision.engine.FenValidator
import com.chessvision.engine.InvalidFenException
import kotlin.math.abs

/**
 * Неизменяемая шахматная позиция с генерацией легальных ходов.
 * Не реализует правила ничьей (троекратное повторение, 50 ходов, недостаток материала).
 * Поле en passant ставится после любого двойного хода пешки (как делает и сам Stockfish в выводе FEN).
 */
class Position private constructor(
    private val cells: CharArray,       // 64 символа, ' ' = пусто, заглавные = белые
    val whiteToMove: Boolean,
    val castling: String,               // подмножество "KQkq" в этом порядке
    val epSquare: Int,                  // -1, если нет
    val halfmove: Int,
    val fullmove: Int,
) {
    private val legal: List<Move> by lazy(LazyThreadSafetyMode.NONE) { generateLegal() }

    fun pieceAt(sq: Int): Char = cells[sq]

    fun legalMoves(): List<Move> = legal

    fun isCheck(): Boolean = isAttacked(kingSquare(whiteToMove), !whiteToMove)
    fun isCheckmate(): Boolean = legal.isEmpty() && isCheck()
    fun isStalemate(): Boolean = legal.isEmpty() && !isCheck()

    /** Применяет легальный ход; для нелегального бросает [IllegalMoveException]. */
    fun play(move: Move): Position {
        if (move !in legal) throw IllegalMoveException("Нелегальный ход ${move.uci()} в позиции ${toFen()}")
        return makeMove(move)
    }

    fun placement(): String = buildString {
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val c = cells[Sq.of(f, r)]
                if (c == ' ') {
                    empty++
                } else {
                    if (empty > 0) { append(empty); empty = 0 }
                    append(c)
                }
            }
            if (empty > 0) append(empty)
            if (r > 0) append('/')
        }
    }

    fun toFen(): String {
        val side = if (whiteToMove) "w" else "b"
        val cr = castling.ifEmpty { "-" }
        val ep = if (epSquare >= 0) Sq.name(epSquare) else "-"
        return "${placement()} $side $cr $ep $halfmove $fullmove"
    }

    // ---------------- атаки и ходы ----------------

    internal fun kingSquare(white: Boolean): Int = cells.indexOf(if (white) 'K' else 'k')

    internal fun isAttacked(sq: Int, byWhite: Boolean): Boolean {
        val f = Sq.file(sq)
        val r = Sq.rank(sq)
        val pawnRank = if (byWhite) r - 1 else r + 1
        if (pawnRank in 0..7) {
            val p = if (byWhite) 'P' else 'p'
            if (f > 0 && cells[Sq.of(f - 1, pawnRank)] == p) return true
            if (f < 7 && cells[Sq.of(f + 1, pawnRank)] == p) return true
        }
        val knight = if (byWhite) 'N' else 'n'
        for (d in KNIGHT) {
            val nf = f + d[0]; val nr = r + d[1]
            if (nf in 0..7 && nr in 0..7 && cells[Sq.of(nf, nr)] == knight) return true
        }
        val king = if (byWhite) 'K' else 'k'
        for (d in KING) {
            val nf = f + d[0]; val nr = r + d[1]
            if (nf in 0..7 && nr in 0..7 && cells[Sq.of(nf, nr)] == king) return true
        }
        val rook = if (byWhite) 'R' else 'r'
        val bishop = if (byWhite) 'B' else 'b'
        val queen = if (byWhite) 'Q' else 'q'
        for (d in ROOK) if (rayHits(f, r, d, rook, queen)) return true
        for (d in BISHOP) if (rayHits(f, r, d, bishop, queen)) return true
        return false
    }

    private fun rayHits(f: Int, r: Int, d: IntArray, a: Char, b: Char): Boolean {
        var nf = f + d[0]
        var nr = r + d[1]
        while (nf in 0..7 && nr in 0..7) {
            val c = cells[Sq.of(nf, nr)]
            if (c != ' ') return c == a || c == b
            nf += d[0]; nr += d[1]
        }
        return false
    }

    private fun generateLegal(): List<Move> {
        val mover = whiteToMove
        return pseudoLegal().filter { m ->
            val next = makeMove(m)
            !next.isAttacked(next.kingSquare(mover), !mover)
        }
    }

    private fun canLand(sq: Int, white: Boolean): Boolean {
        val t = cells[sq]
        return t == ' ' || t.isUpperCase() != white
    }

    private fun pseudoLegal(): List<Move> {
        val out = ArrayList<Move>(64)
        val white = whiteToMove
        for (sq in 0..63) {
            val c = cells[sq]
            if (c == ' ' || c.isUpperCase() != white) continue
            when (c.uppercaseChar()) {
                'P' -> pawnMoves(out, sq, white)
                'N' -> stepMoves(out, sq, KNIGHT, white)
                'K' -> { stepMoves(out, sq, KING, white); castlingMoves(out, sq, white) }
                'B' -> slideMoves(out, sq, BISHOP, white)
                'R' -> slideMoves(out, sq, ROOK, white)
                'Q' -> { slideMoves(out, sq, BISHOP, white); slideMoves(out, sq, ROOK, white) }
            }
        }
        return out
    }

    private fun stepMoves(out: MutableList<Move>, sq: Int, deltas: Array<IntArray>, white: Boolean) {
        val f = Sq.file(sq); val r = Sq.rank(sq)
        for (d in deltas) {
            val nf = f + d[0]; val nr = r + d[1]
            if (nf !in 0..7 || nr !in 0..7) continue
            val to = Sq.of(nf, nr)
            if (canLand(to, white)) out.add(Move(sq, to))
        }
    }

    private fun slideMoves(out: MutableList<Move>, sq: Int, dirs: Array<IntArray>, white: Boolean) {
        val f = Sq.file(sq); val r = Sq.rank(sq)
        for (d in dirs) {
            var nf = f + d[0]; var nr = r + d[1]
            while (nf in 0..7 && nr in 0..7) {
                val to = Sq.of(nf, nr)
                val t = cells[to]
                if (t == ' ') {
                    out.add(Move(sq, to))
                } else {
                    if (t.isUpperCase() != white) out.add(Move(sq, to))
                    break
                }
                nf += d[0]; nr += d[1]
            }
        }
    }

    private fun pawnMoves(out: MutableList<Move>, sq: Int, white: Boolean) {
        val f = Sq.file(sq); val r = Sq.rank(sq)
        val dir = if (white) 1 else -1
        val startRank = if (white) 1 else 6
        val promoRank = if (white) 7 else 0
        val nr = r + dir
        if (nr !in 0..7) return
        val fwd = Sq.of(f, nr)
        if (cells[fwd] == ' ') {
            addPawnMove(out, sq, fwd, nr == promoRank)
            if (r == startRank) {
                val fwd2 = Sq.of(f, r + 2 * dir)
                if (cells[fwd2] == ' ') out.add(Move(sq, fwd2))
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7) continue
            val to = Sq.of(nf, nr)
            val t = cells[to]
            if ((t != ' ' && t.isUpperCase() != white) || (t == ' ' && to == epSquare)) {
                addPawnMove(out, sq, to, nr == promoRank)
            }
        }
    }

    private fun addPawnMove(out: MutableList<Move>, from: Int, to: Int, promo: Boolean) {
        if (promo) for (p in "qrbn") out.add(Move(from, to, p)) else out.add(Move(from, to))
    }

    private fun castlingMoves(out: MutableList<Move>, sq: Int, white: Boolean) {
        if (white && sq == 4) {
            if ('K' in castling && cells[5] == ' ' && cells[6] == ' ' && cells[7] == 'R' &&
                !isAttacked(4, false) && !isAttacked(5, false)) out.add(Move(4, 6))
            if ('Q' in castling && cells[3] == ' ' && cells[2] == ' ' && cells[1] == ' ' && cells[0] == 'R' &&
                !isAttacked(4, false) && !isAttacked(3, false)) out.add(Move(4, 2))
        } else if (!white && sq == 60) {
            if ('k' in castling && cells[61] == ' ' && cells[62] == ' ' && cells[63] == 'r' &&
                !isAttacked(60, true) && !isAttacked(61, true)) out.add(Move(60, 62))
            if ('q' in castling && cells[59] == ' ' && cells[58] == ' ' && cells[57] == ' ' && cells[56] == 'r' &&
                !isAttacked(60, true) && !isAttacked(59, true)) out.add(Move(60, 58))
        }
    }

    /** Применяет ход без проверки легальности (для псевдолегальных ходов). */
    internal fun makeMove(m: Move): Position {
        val c = cells.copyOf()
        val piece = c[m.from]
        val white = piece.isUpperCase()
        val isPawn = piece == 'P' || piece == 'p'
        var capture = c[m.to] != ' '
        c[m.to] = piece
        c[m.from] = ' '
        var ep = -1
        if (isPawn) {
            if (m.to == epSquare && !capture && Sq.file(m.from) != Sq.file(m.to)) {
                c[Sq.of(Sq.file(m.to), Sq.rank(m.from))] = ' '
                capture = true
            }
            if (abs(Sq.rank(m.to) - Sq.rank(m.from)) == 2) {
                ep = Sq.of(Sq.file(m.from), (Sq.rank(m.from) + Sq.rank(m.to)) / 2)
            }
            m.promotion?.let { c[m.to] = if (white) it.uppercaseChar() else it.lowercaseChar() }
        }
        if ((piece == 'K' || piece == 'k') && abs(Sq.file(m.to) - Sq.file(m.from)) == 2) {
            val r = Sq.rank(m.from)
            if (Sq.file(m.to) == 6) {
                c[Sq.of(5, r)] = c[Sq.of(7, r)]; c[Sq.of(7, r)] = ' '
            } else {
                c[Sq.of(3, r)] = c[Sq.of(0, r)]; c[Sq.of(0, r)] = ' '
            }
        }
        var cr = castling
        if (piece == 'K') cr = cr.filter { it != 'K' && it != 'Q' }
        if (piece == 'k') cr = cr.filter { it != 'k' && it != 'q' }
        for (s in intArrayOf(m.from, m.to)) {
            cr = when (s) {
                0 -> cr.filter { it != 'Q' }
                7 -> cr.filter { it != 'K' }
                56 -> cr.filter { it != 'q' }
                63 -> cr.filter { it != 'k' }
                else -> cr
            }
        }
        return Position(
            cells = c,
            whiteToMove = !whiteToMove,
            castling = cr,
            epSquare = ep,
            halfmove = if (isPawn || capture) 0 else halfmove + 1,
            fullmove = if (white) fullmove else fullmove + 1,
        )
    }

    companion object {
        private val KNIGHT = arrayOf(
            intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
            intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2),
        )
        private val KING = arrayOf(
            intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
            intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        )
        private val ROOK = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        private val BISHOP = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))

        val START: Position by lazy { fromFen(FenValidator.START_FEN) }

        /**
         * Строгий разбор FEN: структура + базовая легальность (короли, пешки, рокировки,
         * en passant, сторона, не имеющая хода, не под шахом). Бросает [InvalidFenException].
         */
        fun fromFen(fen: String): Position {
            val p = FenValidator.parse(fen).normalized.split(' ')
            val cells = CharArray(64) { ' ' }
            p[0].split('/').forEachIndexed { idx, rankStr ->
                var f = 0
                for (ch in rankStr) {
                    if (ch in '1'..'8') f += ch - '0' else { cells[Sq.of(f, 7 - idx)] = ch; f++ }
                }
            }
            val white = p[1] == "w"
            val castling = "KQkq".filter { it in p[2] }

            var wPawns = 0; var bPawns = 0; var wTotal = 0; var bTotal = 0
            for (c in cells) {
                if (c == ' ') continue
                if (c.isUpperCase()) { wTotal++; if (c == 'P') wPawns++ } else { bTotal++; if (c == 'p') bPawns++ }
            }
            if (wPawns > 8 || bPawns > 8 || wTotal > 16 || bTotal > 16) {
                throw InvalidFenException("Слишком много фигур или пешек")
            }

            fun bad(msg: String): Nothing = throw InvalidFenException(msg)
            if ('K' in castling && !(cells[4] == 'K' && cells[7] == 'R')) bad("Рокировка K без короля e1/ладьи h1")
            if ('Q' in castling && !(cells[4] == 'K' && cells[0] == 'R')) bad("Рокировка Q без короля e1/ладьи a1")
            if ('k' in castling && !(cells[60] == 'k' && cells[63] == 'r')) bad("Рокировка k без короля e8/ладьи h8")
            if ('q' in castling && !(cells[60] == 'k' && cells[56] == 'r')) bad("Рокировка q без короля e8/ладьи a8")

            var ep = -1
            if (p[3] != "-") {
                ep = Sq.parse(p[3])
                val f = Sq.file(ep)
                val ok = if (white) {
                    Sq.rank(ep) == 5 && cells[Sq.of(f, 4)] == 'p' && cells[ep] == ' ' && cells[Sq.of(f, 6)] == ' '
                } else {
                    Sq.rank(ep) == 2 && cells[Sq.of(f, 3)] == 'P' && cells[ep] == ' ' && cells[Sq.of(f, 1)] == ' '
                }
                if (!ok) bad("Поле en passant не согласовано с расстановкой")
            }

            val pos = Position(cells, white, castling, ep, p[4].toInt(), p[5].toInt())
            // сторона, не имеющая хода, не может стоять под шахом
            if (pos.isAttacked(pos.kingSquare(!white), white)) bad("Король стороны, не имеющей хода, под шахом")
            return pos
        }
    }
}

class IllegalMoveException(message: String) : IllegalArgumentException(message)
