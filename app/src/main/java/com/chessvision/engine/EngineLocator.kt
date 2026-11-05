package com.chessvision.engine

import android.content.Context
import android.os.Build
import java.io.File

/** Находит бинарник Stockfish, упакованный как libstockfish.so в jniLibs/arm64-v8a. */
object EngineLocator {
    fun resolve(context: Context, name: String = EngineConfig.DEFAULT_BINARY): File {
        if (Build.SUPPORTED_64_BIT_ABIS.none { it == "arm64-v8a" }) {
            throw EngineNotFoundException(
                "Поддерживается только arm64-v8a. ABI устройства: ${Build.SUPPORTED_ABIS.joinToString()}"
            )
        }
        val f = File(context.applicationInfo.nativeLibraryDir, name)
        if (!f.exists()) {
            throw EngineNotFoundException(
                "Не найден ${f.path}. Соберите Stockfish: scripts/build_stockfish.sh (см. README)."
            )
        }
        if (!f.canExecute()) throw EngineNotFoundException("Файл ${f.path} не исполняемый")
        return f
    }
}
