package com.chessvision

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenCaptureState {
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame.asStateFlow()

    internal fun publish(bitmap: Bitmap) {
        _frame.value = bitmap
    }

    internal fun clear() {
        _frame.value = null
    }
}
