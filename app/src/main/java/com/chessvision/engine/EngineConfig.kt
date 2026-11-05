package com.chessvision.engine

/** Настройки движка. Значения проверяются при создании. */
data class EngineConfig(
    val binaryName: String = DEFAULT_BINARY,
    val threads: Int = 1,
    val hashMb: Int = 64,
    val multiPv: Int = 3,
    val startupTimeoutMs: Long = 10_000,
    val readyTimeoutMs: Long = 10_000,
) {
    init {
        require(threads in 1..64) { "threads must be in 1..64" }
        require(hashMb in 1..4096) { "hashMb must be in 1..4096" }
        require(multiPv in 1..10) { "multiPv must be in 1..10" }
    }

    /** UCI-команды, которые отправляются после `uciok`. */
    fun setOptionCommands(): List<String> = listOf(
        "setoption name Threads value $threads",
        "setoption name Hash value $hashMb",
        "setoption name MultiPV value $multiPv",
        "setoption name UCI_AnalyseMode value true",
    )

    companion object {
        const val DEFAULT_BINARY = "libstockfish.so"
    }
}

/** Ограничение поиска для команды `go`. */
sealed interface SearchLimit {
    fun toGoCommand(): String
    /** Верхняя граница ожидания `bestmove` на стороне приложения. */
    fun timeoutMs(): Long

    data class Depth(val plies: Int, val timeoutMs: Long = 60_000) : SearchLimit {
        init { require(plies in 1..60) }
        override fun toGoCommand() = "go depth $plies"
        override fun timeoutMs() = timeoutMs
    }

    data class MoveTimeMs(val ms: Long) : SearchLimit {
        init { require(ms in 50..600_000) }
        override fun toGoCommand() = "go movetime $ms"
        override fun timeoutMs() = ms + 5_000
    }
}
