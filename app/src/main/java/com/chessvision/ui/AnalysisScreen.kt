
package com.chessvision.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chessvision.ScreenCaptureService
import com.chessvision.ScreenCaptureState
import com.chessvision.engine.format
import com.chessvision.overlay.ArrowPalette
import kotlin.math.roundToInt

@Composable
fun AnalysisScreen(vm: AnalysisViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val frame by ScreenCaptureState.frame.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val projectionManager = remember {
        context.getSystemService(MediaProjectionManager::class.java)
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(
                context,
                ScreenCaptureService::class.java
            ).apply {
                putExtra(
                    ScreenCaptureService.EXTRA_RESULT_CODE,
                    result.resultCode
                )
                putExtra(
                    ScreenCaptureService.EXTRA_DATA,
                    result.data
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    val busy = s.status == EngineStatus.STARTING ||
        s.status == EngineStatus.ANALYZING

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "ChessVision — Stockfish MultiPV=3",
            style = MaterialTheme.typography.titleMedium
        )

        BoardView(s.position, s.lines, Modifier.fillMaxWidth())

        Text(
            "Захват экрана",
            style = MaterialTheme.typography.titleMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    captureLauncher.launch(
                        projectionManager.createScreenCaptureIntent()
                    )
                }
            ) {
                Text("Начать захват")
            }

            OutlinedButton(
                onClick = {
                    context.stopService(
                        Intent(context, ScreenCaptureService::class.java)
                    )
                }
            ) {
                Text("Остановить")
            }
        }

        if (frame != null) {
            Text("Последний захваченный кадр")

            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = "Захваченный экран",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("Ожидание изображения экрана…")
        }

        OutlinedTextField(
            value = s.fen,
            onValueChange = vm::onFenChange,
            label = { Text("FEN") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::analyze) {
                Text(if (busy) "Перезапустить" else "Анализировать")
            }

            OutlinedButton(
                onClick = vm::cancel,
                enabled = busy
            ) {
                Text("Стоп")
            }
        }

        Text(
            when (s.status) {
                EngineStatus.IDLE ->
                    if (s.lines.isEmpty()) "Готов" else "Готово"

                EngineStatus.STARTING -> "Движок запускается…"
                EngineStatus.ANALYZING -> "Анализ…"
                EngineStatus.ERROR -> "Ошибка"
            }
        )

        s.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        s.lines.forEach { l ->
            Text(
                "#${l.rank}  ${l.scoreWhite.format()}  d${l.depth}  ${l.moves.take(8).joinToString(" ")}",
                color = Color(ArrowPalette.argb(l.rank)),
            )
        }

        Text(
            "Настройки (в пределах, безопасных для этого устройства)",
            style = MaterialTheme.typography.labelLarge
        )

        val lim = s.limits

        if (lim.maxThreads > 1) {
            Text("Потоки: ${s.settings.threads}")

            Slider(
                value = s.settings.threads.toFloat(),
                onValueChange = {
                    vm.onSettingsChange(
                        s.settings.copy(threads = it.roundToInt())
                    )
                },
                valueRange = 1f..lim.maxThreads.toFloat(),
                steps = (lim.maxThreads - 2).coerceAtLeast(0),
            )
        } else {
            Text("Потоки: 1 (максимум для этого устройства)")
        }

        Text("Hash: ${s.settings.hashMb} МБ")

        Slider(
            value = s.settings.hashMb.toFloat(),
            onValueChange = {
                vm.onSettingsChange(
                    s.settings.copy(hashMb = it.roundToInt())
                )
            },
            valueRange = 16f..lim.maxHashMb.toFloat().coerceAtLeast(17f),
        )

        Text("Время анализа: ${s.settings.moveTimeMs} мс")

        Slider(
            value = s.settings.moveTimeMs.toFloat(),
            onValueChange = {
                vm.onSettingsChange(
                    s.settings.copy(moveTimeMs = it.roundToInt().toLong())
                )
            },
            valueRange = lim.minMoveTimeMs.toFloat()..
                lim.maxMoveTimeMs.toFloat(),
        )
    }
}

