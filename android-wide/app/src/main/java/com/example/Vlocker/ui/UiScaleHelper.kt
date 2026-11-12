package com.example.Vlocker.ui

import kotlin.math.roundToInt

object UiScaleHelper {
    fun computeDialogWidthPx(
        screenWidthPx: Int,
        maxDialogWidthPx: Int,
        isLandscape: Boolean
    ): Int {
        if (screenWidthPx <= 0) return maxDialogWidthPx.coerceAtLeast(0)
        val marginRatio = if (isLandscape) 0.58f else 0.92f
        val preferred = (screenWidthPx * marginRatio).roundToInt()
        val safeUpper = (screenWidthPx * 0.94f).roundToInt().coerceAtLeast(1)
        return if (maxDialogWidthPx > 0) {
            minOf(preferred, maxDialogWidthPx, safeUpper)
        } else {
            minOf(preferred, safeUpper)
        }
    }
}

