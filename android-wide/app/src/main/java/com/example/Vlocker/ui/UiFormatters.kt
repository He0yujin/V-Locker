package com.example.Vlocker.ui

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object UiFormatters {

    fun isKoreanCoordinate(lat: Double, lng: Double): Boolean {
        return lat in 33.0..38.9 && lng in 124.5..132.0
    }

    fun formatDurationHours(hours: Double): String {
        val totalMinutes = (hours.coerceAtLeast(0.0) * 60.0).roundToInt()
        return formatDurationMinutes(totalMinutes)
    }

    fun formatDurationMinutes(minutes: Int?): String {
        if (minutes == null || minutes < 0) return "--분"
        val hrs = minutes / 60
        val mins = minutes % 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs}시간 ${mins}분"
            hrs > 0 -> "${hrs}시간"
            else -> "${mins}분"
        }
    }

    fun formatPercentWholeOrDecimal(pct: Double): String {
        val rounded1 = (pct * 10.0).roundToInt() / 10.0
        return if (abs(rounded1 - rounded1.roundToInt()) < 0.05) {
            "${rounded1.roundToInt()}%"
        } else {
            String.format(Locale.US, "%.1f%%", rounded1)
        }
    }

    fun formatDistanceKm(distanceKm: Double): String {
        val rounded1 = (distanceKm * 10.0).roundToInt() / 10.0
        return if (abs(rounded1 - rounded1.roundToInt()) < 0.05) {
            "${rounded1.roundToInt()} km"
        } else {
            String.format(Locale.US, "%.1f km", rounded1)
        }
    }

    fun formatTemperature(tempCelsius: Double): String {
        val rounded1 = (tempCelsius * 10.0).roundToInt() / 10.0
        return if (abs(rounded1 - rounded1.roundToInt()) < 0.05) {
            "${rounded1.roundToInt()}°C"
        } else {
            String.format(Locale.US, "%.1f°C", rounded1)
        }
    }

    fun marginModeTitle(mode: String): String {
        return when (mode) {
            "SAFE" -> "안심"
            "MAX_USE" -> "최대 사용"
            else -> "균형"
        }
    }

    fun formatSafetyMarginBonus(mode: String, safetyMultiplier: Double): String {
        val bonusPct = ((safetyMultiplier - 1.0) * 100.0).roundToInt()
        val title = marginModeTitle(mode)
        return if (bonusPct > 0) {
            "$title (+${bonusPct}% 여유)"
        } else {
            "$title (필수치만 보존)"
        }
    }
}

