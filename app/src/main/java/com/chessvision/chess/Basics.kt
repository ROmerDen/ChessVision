package com.chessvision.chess

/** Клетки: 0 = a1, 7 = h1, 56 = a8, 63 = h8. */
object Sq {
    fun file(s: Int) = s and 7
    fun rank(s: Int) = s shr 3
    fun of(file: Int, rank: Int) = rank * 8 + file
    fun name(s: Int): String = "${'a' + file(s)}${'1' + rank(s)}"

    /** "e4" -> 28; -1, если строка не клетка. */
    fun parse(n: String): Int {
        if (n.length != 2) return -1
        val f = n[0] - 'a'
        val r = n[1] - '1'
        return if (f in 0..7 && r in 0..7) of(f, r) else -1
    }
}

/** Ход в UCI-терминах. promotion — строчная буква q/r/b/n либо null. */
data class Move(val from: Int, val to: Int, val promotion: Char? = null) {
    fun uci(): String = Sq.name(from) + Sq.name(to) + (promotion?.toString() ?: "")

    companion object {
        fun fromUci(s: String): Move? {
            if (s.length != 4 && s.length != 5) return null
            val from = Sq.parse(s.substring(0, 2))
            val to = Sq.parse(s.substring(2, 4))
            if (from < 0 || to < 0) return null
            val promo = if (s.length == 5) s[4].lowercaseChar() else null
            if (promo != null && promo !in "qrbn") return null
            return Move(from, to, promo)
        }
    }
}

object Pieces {
    fun glyph(c: Char): String = when (c) {
        'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
        'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
        else -> ""
    }
}
