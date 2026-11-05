package com.chessvision.chess

sealed interface MoveMatch {
    /** Расстановка совпадает с текущей позицией. */
    data object NoChange : MoveMatch
    data class Matched(val move: Move) : MoveMatch
    /** Расстановка не получается ни одним легальным ходом — нужна ручная коррекция. */
    data object NoMatch : MoveMatch
    data class Ambiguous(val moves: List<Move>) : MoveMatch
}

/**
 * История партии: начальная позиция + подтверждённые легальные ходы.
 * FEN строится из истории, а не по картинке. Неизменяемый.
 */
class GameState private constructor(
    val initial: Position,
    val moves: List<Move>,
    val positions: List<Position>,   // positions[0] = initial, positions[i] = после i-го хода
) {
    val current: Position get() = positions.last()
    fun fen(): String = current.toFen()

    fun play(move: Move): GameState {
        val next = current.play(move)   // бросает IllegalMoveException
        return GameState(initial, moves + move, positions + next)
    }

    fun playUci(uci: String): GameState =
        play(Move.fromUci(uci) ?: throw IllegalMoveException("Не UCI-ход: '$uci'"))

    fun undo(): GameState {
        check(moves.isNotEmpty()) { "Нет ходов для отмены" }
        return GameState(initial, moves.dropLast(1), positions.dropLast(1))
    }

    /**
     * Сопоставляет распознанную расстановку фигур (первое поле FEN) с одним легальным ходом
     * из текущей позиции. Не пытается угадать несколько ходов подряд.
     */
    fun resolveTransition(recognizedPlacement: String): MoveMatch {
        val target = expand(recognizedPlacement) ?: return MoveMatch.NoMatch
        if (target == expand(current.placement())) return MoveMatch.NoChange
        val hits = current.legalMoves().filter { expand(current.play(it).placement()) == target }
        return when (hits.size) {
            0 -> MoveMatch.NoMatch
            1 -> MoveMatch.Matched(hits[0])
            else -> MoveMatch.Ambiguous(hits)
        }
    }

    companion object {
        fun start(initial: Position = Position.START) = GameState(initial, emptyList(), listOf(initial))
        fun fromFen(fen: String) = start(Position.fromFen(fen))
        fun fromUciMoves(uciMoves: List<String>, initial: Position = Position.START): GameState =
            uciMoves.fold(start(initial)) { g, m -> g.playUci(m) }

        /** "rnbqkbnr/8/..." -> 64 символа ('.' = пусто); null, если строка некорректна. */
        private fun expand(placement: String): String? {
            val sb = StringBuilder()
            for (ch in placement) {
                when {
                    ch == '/' -> {}
                    ch in '1'..'8' -> repeat(ch - '0') { sb.append('.') }
                    ch in "pnbrqkPNBRQK" -> sb.append(ch)
                    else -> return null
                }
            }
            return if (sb.length == 64) sb.toString() else null
        }
    }
}
