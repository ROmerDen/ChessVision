package com.chessvision.engine

import java.util.Locale

sealed interface Score {
    /** Сантипешки. */
    data class Cp(val centipawns: Int) : Score
    /** Мат за N ходов; знак: > 0 — выигрывает сторона, чья оценка, < 0 — проигрывает. */
    data class Mate(val moves: Int) : Score
}

fun Score.flip(): Score = when (this) {
    is Score.Cp -> Score.Cp(-centipawns)
    is Score.Mate -> Score.Mate(-moves)
}

fun Score.format(): String = when (this) {
    is Score.Cp -> String.format(Locale.US, "%+.2f", centipawns / 100.0)
    is Score.Mate -> when {
        moves > 0 -> "#$moves"
        moves < 0 -> "-#${-moves}"
        else -> "#0"
    }
}

/** Одна линия MultiPV. Ходы — в UCI-нотации (e2e4, e7e8q). */
data class PvLine(
    val rank: Int,
    val depth: Int,
    /** Оценка с точки зрения стороны, которая ходит (как в UCI). */
    val scoreSideToMove: Score,
    /** Оценка с точки зрения белых. */
    val scoreWhite: Score,
    val moves: List<String>,
) {
    val firstMove: String get() = moves.first()
}

data class AnalysisResult(
    val fen: String,
    val whiteToMove: Boolean,
    /** Отсортировано по rank (1 = лучший), не больше MultiPV. */
    val lines: List<PvLine>,
    val bestMove: String?,
)

open class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
class EngineNotFoundException(message: String) : EngineException(message)
class EngineTerminatedException(message: String, cause: Throwable? = null) : EngineException(message, cause)
class EngineTimeoutException(message: String) : EngineException(message)
class InvalidFenException(message: String) : IllegalArgumentException(message)
