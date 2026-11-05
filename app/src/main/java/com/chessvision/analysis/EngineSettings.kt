package com.chessvision.analysis

import com.chessvision.engine.EngineConfig

data class EngineSettings(
    val threads: Int = 1,
    val hashMb: Int = 64,
    val moveTimeMs: Long = 1_500,
) {
    fun toConfig() = EngineConfig(threads = threads, hashMb = hashMb)
}

/** Безопасные пределы настроек для конкретного устройства (нагрев, память). */
data class EngineLimits(
    val maxThreads: Int,
    val maxHashMb: Int,
    val minMoveTimeMs: Long = 500,
    val maxMoveTimeMs: Long = 10_000,
) {
    fun clamp(s: EngineSettings) = EngineSettings(
        threads = s.threads.coerceIn(1, maxThreads),
        hashMb = s.hashMb.coerceIn(16, maxHashMb),
        moveTimeMs = s.moveTimeMs.coerceIn(minMoveTimeMs, maxMoveTimeMs),
    )

    companion object {
        /** Не больше половины ядер (максимум 4) и не больше 1/16 оперативной памяти (максимум 256 МБ). */
        fun forDevice(cores: Int, totalMemMb: Long) = EngineLimits(
            maxThreads = (cores / 2).coerceIn(1, 4),
            maxHashMb = (totalMemMb / 16).coerceIn(16L, 256L).toInt(),
        )
    }
}
