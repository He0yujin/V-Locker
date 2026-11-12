package com.example.Vlocker.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PowerSample(
    val timestampMs: Long,
    val loadKw: Double
)

/**
 * 실시간 V2L 소비 전력 시계열 샘플을 보관하고 그래프/라벨 포맷을 계산하는 순수 Kotlin 트래커.
 */
class PowerTrendTracker(
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES
) {
    private val records = mutableListOf<PowerSample>()

    fun resetWithInitial(loadKw: Double, timestampMs: Long = System.currentTimeMillis()) {
        records.clear()
        records.add(PowerSample(timestampMs = timestampMs, loadKw = clampLoadKw(loadKw)))
    }

    fun recordSample(loadKw: Double, timestampMs: Long = System.currentTimeMillis()) {
        val sample = PowerSample(timestampMs = timestampMs, loadKw = clampLoadKw(loadKw))
        if (records.size >= maxSamples) {
            records.removeAt(0)
        }
        records.add(sample)
    }

    fun clear() {
        records.clear()
    }

    fun samples(): List<PowerSample> = records.toList()

    fun loadValues(): List<Double> = records.map { it.loadKw }

    fun size(): Int = records.size

    companion object {
        const val DEFAULT_MAX_SAMPLES = 20
        const val MAX_V2L_LOAD_KW = 3.6

        fun clampLoadKw(loadKw: Double): Double {
            if (!loadKw.isFinite()) return 0.0
            return loadKw.coerceIn(0.0, MAX_V2L_LOAD_KW)
        }

        fun formatClockTime(
            timestampMs: Long,
            timeZone: TimeZone = TimeZone.getDefault()
        ): String {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
            fmt.timeZone = timeZone
            return fmt.format(Date(timestampMs))
        }

        fun formatTrendLeftLabel(
            samples: List<PowerSample>,
            isLockedOut: Boolean,
            timeZone: TimeZone = TimeZone.getDefault()
        ): String {
            if (samples.isEmpty()) {
                return if (isLockedOut) "V2L 차단 완료" else "실시간 전력 대기 중"
            }
            val firstTime = formatClockTime(samples.first().timestampMs, timeZone)
            return if (samples.size == 1) {
                "$firstTime 시작 · 1회 측정"
            } else {
                "$firstTime ~ 최근 ${samples.size}회 실시간"
            }
        }

        fun formatTrendRightLabel(
            samples: List<PowerSample>,
            isLockedOut: Boolean,
            timeZone: TimeZone = TimeZone.getDefault()
        ): String {
            val latest = samples.lastOrNull()
            if (latest == null) {
                return if (isLockedOut) "차단됨" else "대기"
            }
            val lastTime = formatClockTime(latest.timestampMs, timeZone)
            return if (isLockedOut) {
                "$lastTime 차단 (0.0 kW)"
            } else {
                "현재 $lastTime"
            }
        }
    }
}

