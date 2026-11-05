package com.chessvision.overlay

import com.chessvision.chess.Move
import com.chessvision.chess.Sq
import com.chessvision.engine.PvLine
import kotlin.math.sqrt

data class Pt(val x: Float, val y: Float)

/** Квадратная область доски на экране (в любых единицах). */
data class BoardRect(val left: Float, val top: Float, val size: Float, val whiteAtBottom: Boolean = true) {
    val cell: Float get() = size / 8f

    fun center(sq: Int): Pt {
        val f = Sq.file(sq); val r = Sq.rank(sq)
        val col = if (whiteAtBottom) f else 7 - f
        val row = if (whiteAtBottom) 7 - r else r
        return Pt(left + (col + 0.5f) * cell, top + (row + 0.5f) * cell)
    }

    /** Клетка под точкой или null, если точка вне доски. */
    fun squareAt(x: Float, y: Float): Int? {
        val col = ((x - left) / cell).toInt()
        val row = ((y - top) / cell).toInt()
        if (x < left || y < top || col !in 0..7 || row !in 0..7) return null
        return squareAtCell(row, col, whiteAtBottom)
    }

    companion object {
        /** row = 0 — верхний ряд на экране, col = 0 — левый столбец. */
        fun squareAtCell(row: Int, col: Int, whiteAtBottom: Boolean): Int =
            if (whiteAtBottom) Sq.of(col, 7 - row) else Sq.of(7 - col, row)
    }
}

data class Arrow(val rank: Int, val from: Pt, val to: Pt, val argb: Long)

object ArrowPalette {
    /** 1 — зелёный, 2 — синий, 3 — фиолетовый. */
    fun argb(rank: Int): Long = when (rank) {
        1 -> 0xE600B050L
        2 -> 0xE61E6FEBL
        else -> 0xE69B30FFL
    }
}

object ArrowPlanner {
    /** По одной стрелке на каждую реально вернувшуюся линию; линии с некорректным ходом пропускаются. */
    fun plan(lines: List<PvLine>, board: BoardRect): List<Arrow> = lines.mapNotNull { l ->
        val m = Move.fromUci(l.firstMove) ?: return@mapNotNull null
        Arrow(l.rank, board.center(m.from), board.center(m.to), ArrowPalette.argb(l.rank))
    }
}

object ArrowGeometry {
    /** Контур стрелки (7 точек): древко + наконечник. Пустой список, если стрелка вырожденная. */
    fun polygon(
        from: Pt,
        to: Pt,
        thickness: Float,
        headLength: Float = thickness * 2f,
        headWidth: Float = thickness * 2.2f,
        tipInset: Float = 0f,
    ): List<Pt> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-3f) return emptyList()
        val ux = dx / len; val uy = dy / len
        val tipDist = len - tipInset
        if (tipDist <= 1e-3f) return emptyList()
        val head = minOf(headLength, tipDist * 0.6f)
        val nx = -uy; val ny = ux
        fun at(d: Float, off: Float) = Pt(from.x + ux * d + nx * off, from.y + uy * d + ny * off)
        val half = thickness / 2f
        val hw = headWidth / 2f
        return listOf(
            at(0f, half), at(tipDist - head, half), at(tipDist - head, hw),
            at(tipDist, 0f),
            at(tipDist - head, -hw), at(tipDist - head, -half), at(0f, -half),
        )
    }
}
