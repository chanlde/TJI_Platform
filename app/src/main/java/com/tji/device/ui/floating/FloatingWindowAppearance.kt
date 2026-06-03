package com.tji.device.ui.floating

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FloatingWindowAppearance {
    private const val PREFS = "floating_window_appearance"
    private const val KEY_BACKGROUND_ALPHA = "background_alpha"
    const val DEFAULT_BACKGROUND_ALPHA = 0f

    private val _backgroundAlpha = MutableStateFlow(DEFAULT_BACKGROUND_ALPHA)
    val backgroundAlpha: StateFlow<Float> = _backgroundAlpha.asStateFlow()

    fun load(context: Context) {
        _backgroundAlpha.value = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_BACKGROUND_ALPHA, DEFAULT_BACKGROUND_ALPHA)
            .coerceIn(0f, 1f)
    }

    fun setBackgroundAlpha(context: Context, alpha: Float) {
        val safeAlpha = alpha.coerceIn(0f, 1f)
        _backgroundAlpha.value = safeAlpha
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putFloat(KEY_BACKGROUND_ALPHA, safeAlpha)
            }
    }
}
