package com.chessvision.engine

/**
 * Структурная проверка FEN. НЕ проверяет легальность позиции
 * (шах стороне, не имеющей хода, согласованность рокировок и т.п.).
 */
object FenValidator {
    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    data class ParsedFen(val normalized: String, val whiteToMove: Boolean)

    private val castling = Regex("^(-|K?Q?k?q?)$")
    private val enPassant = Regex("^(-|[a-h][36])$")

    fun parse(fen: String): ParsedFen {
        if (fen.any { it == '\n' || it == '\r' }) throw InvalidFenException("FEN must be a single line")
        val p = fen.trim().split(Regex("\\s+"))
        if (p.size !in 4..6) throw InvalidFenException("FEN must have 4..6 fields")

        val ranks = p[0].split('/')
        if (ranks.size != 8) throw InvalidFenException("Placement must have 8 ranks")
        var whiteKings = 0
        var blackKings = 0
        ranks.forEachIndexed { idx, rank ->
            var files = 0
            for (c in rank) {
                when {
                    c in '1'..'8' -> files += c - '0'
                    c in "pnbrqkPNBRQK" -> {
                        files++
                        if (c == 'K') whiteKings++
                        if (c == 'k') blackKings++
                        if ((c == 'p' || c == 'P') && (idx == 0 || idx == 7)) {
                            throw InvalidFenException("Pawn on first/last rank")
                        }
                    }
                    else -> throw InvalidFenException("Bad character '$c' in placement")
                }
            }
            if (files != 8) throw InvalidFenException("Rank ${8 - idx} does not have 8 files")
        }
        if (whiteKings != 1 || blackKings != 1) throw InvalidFenException("Need exactly one king per side")

        val white = when (p[1]) {
            "w" -> true
            "b" -> false
            else -> throw InvalidFenException("Active color must be w or b")
        }
        if (p[2].isEmpty() || !castling.matches(p[2])) throw InvalidFenException("Bad castling field")
        if (!enPassant.matches(p[3])) throw InvalidFenException("Bad en passant field")
        val half = p.getOrElse(4) { "0" }.toIntOrNull()
        val full = p.getOrElse(5) { "1" }.toIntOrNull()
        if (half == null || half < 0) throw InvalidFenException("Bad halfmove clock")
        if (full == null || full < 1) throw InvalidFenException("Bad fullmove number")

        return ParsedFen("${p[0]} ${p[1]} ${p[2]} ${p[3]} $half $full", white)
    }
}
