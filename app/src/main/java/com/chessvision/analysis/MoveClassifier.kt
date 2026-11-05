package com.chessvision.analysis

import com.chessvision.engine.Score

enum class MoveQuality { BEST, GOOD, INACCURACY, MISTAKE, BLUNDER }

/**
 * Учебная оценка сыгранного хода. MultiPV-линии 2 и 3 — это альтернативы, а не «ошибки»:
 * качество хода определяется только потерей оценки относительно лучшего хода ДО хода.
 * Пороги условные и настраиваемые.
 */
object MoveClassifier {
    const val MATE_CP = 10_000

    fun Score.toCp(): Int = when (this) {
        is Score.Cp -> centipawns.coerceIn(-MATE_CP, MATE_CP)
        is Score.Mate -> if (moves > 0) MATE_CP - moves else -MATE_CP - moves
    }

    /**
     * bestBefore — оценка лучшей линии до хода (с точки зрения ходящего).
     * afterMover — оценка позиции после сыгранного хода, тоже с точки зрения ходившего
     * (если анализировалась позиция соперника — предварительно перевернуть через flip()).
     */
    fun lossCp(bestBefore: Score, afterMover: Score): Int =
        (bestBefore.toCp() - afterMover.toCp()).coerceAtLeast(0)

    fun classify(lossCp: Int, playedMove: String? = null, bestMove: String? = null): MoveQuality = when {
        playedMove != null && playedMove == bestMove -> MoveQuality.BEST
        lossCp <= 10 -> MoveQuality.BEST
        lossCp <= 50 -> MoveQuality.GOOD
        lossCp <= 100 -> MoveQuality.INACCURACY
        lossCp <= 250 -> MoveQuality.MISTAKE
        else -> MoveQuality.BLUNDER
    }
}
