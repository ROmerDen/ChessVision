package com.chessvision.engine

/** Разбор строк, которые пишет движок. Не бросает исключений на неизвестных строках. */
object UciParser {
    data class Info(
        val depth: Int?,
        val multipv: Int,
        val score: Score,
        /** true для `lowerbound`/`upperbound` — такие оценки не окончательные. */
        val isBound: Boolean,
        val pv: List<String>,
    )

    private val ws = Regex("\\s+")

    /** Возвращает null для `info string ...`, `info currmove ...` и любых строк без score и pv. */
    fun parseInfo(line: String): Info? {
        val t = line.trim().split(ws)
        if (t.firstOrNull() != "info" || t.getOrNull(1) == "string") return null
        var depth: Int? = null
        var multipv = 1
        var score: Score? = null
        var bound = false
        var pv: List<String> = emptyList()
        var i = 1
        while (i < t.size) {
            when (t[i]) {
                "depth" -> { depth = t.getOrNull(i + 1)?.toIntOrNull(); i += 2 }
                "multipv" -> { multipv = t.getOrNull(i + 1)?.toIntOrNull() ?: 1; i += 2 }
                "score" -> {
                    val v = t.getOrNull(i + 2)?.toIntOrNull()
                    if (v != null) {
                        score = when (t[i + 1]) {
                            "cp" -> Score.Cp(v)
                            "mate" -> Score.Mate(v)
                            else -> null
                        }
                    }
                    i += 3
                    if (t.getOrNull(i) == "lowerbound" || t.getOrNull(i) == "upperbound") { bound = true; i++ }
                }
                "pv" -> { pv = t.subList(i + 1, t.size); i = t.size }
                else -> i++
            }
        }
        if (score == null || pv.isEmpty()) return null
        return Info(depth, multipv, score, bound, pv)
    }

    fun isBestMove(line: String) = line.startsWith("bestmove")

    /** `bestmove e2e4 ponder e7e5` -> "e2e4"; `bestmove (none)` -> null. */
    fun parseBestMove(line: String): String? {
        val t = line.trim().split(ws)
        if (t.firstOrNull() != "bestmove") return null
        return t.getOrNull(1)?.takeIf { it != "(none)" && it != "0000" }
    }
}
