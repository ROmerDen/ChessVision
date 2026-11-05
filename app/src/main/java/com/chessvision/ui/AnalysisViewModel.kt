package com.chessvision.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessvision.analysis.EngineLimits
import com.chessvision.analysis.EngineSettings
import com.chessvision.chess.Position
import com.chessvision.engine.EngineConfig
import com.chessvision.engine.EngineLocator
import com.chessvision.engine.FenValidator
import com.chessvision.engine.InvalidFenException
import com.chessvision.engine.ProcessUciTransport
import com.chessvision.engine.PvLine
import com.chessvision.engine.SearchLimit
import com.chessvision.engine.UciEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EngineStatus { IDLE, STARTING, ANALYZING, ERROR }

data class AnalysisUiState(
    val fen: String = FenValidator.START_FEN,
    val position: Position = Position.START,
    val status: EngineStatus = EngineStatus.IDLE,
    val lines: List<PvLine> = emptyList(),
    val error: String? = null,
    val settings: EngineSettings = EngineSettings(),
    val limits: EngineLimits = EngineLimits(maxThreads = 1, maxHashMb = 64),
)

class AnalysisViewModel(app: Application) : AndroidViewModel(app) {
    private val limits: EngineLimits = run {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        EngineLimits.forDevice(Runtime.getRuntime().availableProcessors(), info.totalMem / (1024 * 1024))
    }
    private val _state = MutableStateFlow(
        AnalysisUiState(settings = limits.clamp(EngineSettings()), limits = limits)
    )
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    private var engine: UciEngine? = null
    private var activeConfig: EngineConfig? = null
    private var job: Job? = null

    /** Номер актуального запроса: результаты и статусы устаревших задач отбрасываются. */
    private var requestId = 0L

    fun onFenChange(fen: String) {
        try {
            val pos = Position.fromFen(fen)
            _state.update { it.copy(fen = fen, position = pos, lines = emptyList(), error = null) }
        } catch (e: InvalidFenException) {
            _state.update { it.copy(fen = fen, error = "FEN: ${e.message}") }
        }
    }

    fun onSettingsChange(s: EngineSettings) {
        // Движок перезапустится при следующем анализе, если изменились потоки или Hash.
        _state.update { it.copy(settings = limits.clamp(s)) }
    }

    fun cancel() {
        requestId++              // поздние публикации старой задачи будут отброшены
        job?.cancel()
        _state.update { it.copy(status = EngineStatus.IDLE) }
    }

    fun analyze() {
        job?.cancel()
        val id = ++requestId
        val fen = _state.value.fen
        val settings = _state.value.settings
        job = viewModelScope.launch {
            try {
                val position = Position.fromFen(fen)
                publish(id) { copy(status = EngineStatus.STARTING, error = null) }
                val e = ensureEngine(settings.toConfig())
                publish(id) { copy(status = EngineStatus.ANALYZING) }
                val result = e.analyze(position.toFen(), SearchLimit.MoveTimeMs(settings.moveTimeMs))
                publish(id) {
                    copy(status = EngineStatus.IDLE, lines = result.lines, position = position, error = null)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                publish(id) {
                    copy(status = EngineStatus.ERROR, lines = emptyList(), error = t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }

    private fun publish(id: Long, change: AnalysisUiState.() -> AnalysisUiState) {
        if (id == requestId) _state.update { it.change() }
    }

    private suspend fun ensureEngine(desired: EngineConfig): UciEngine {
        val current = engine
        if (current != null && current.isRunning && activeConfig == desired) return current
        current?.close()
        engine = null
        val binary = EngineLocator.resolve(getApplication(), desired.binaryName)
        val e = UciEngine(ProcessUciTransport(binary), desired)
        e.start()                       // при ошибке или отмене UciEngine сам закрывает процесс
        engine = e
        activeConfig = desired
        return e
    }

    override fun onCleared() {
        engine?.close()
    }
}
