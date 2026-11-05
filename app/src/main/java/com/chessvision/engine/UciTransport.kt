package com.chessvision.engine

/**
 * Канал обмена строками с движком. Реализации: [ProcessUciTransport] (отдельный процесс),
 * позже возможна JNI-реализация с тем же интерфейсом.
 * readLine() должен вызываться из одного потребителя (UciEngine держит Mutex).
 */
interface UciTransport : AutoCloseable {
    val isAlive: Boolean
    suspend fun start()
    suspend fun send(line: String)
    /** Следующая строка вывода; бросает [EngineTerminatedException], если движок завершился. */
    suspend fun readLine(): String
    override fun close()
}
