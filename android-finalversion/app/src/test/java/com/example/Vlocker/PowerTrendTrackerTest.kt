package com.example.Vlocker

import com.example.Vlocker.ui.PowerSample
import com.example.Vlocker.ui.PowerTrendTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PowerTrendTrackerTest {

    private val kst: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

    @Test
    fun `초기 트래커에는 더미 샘플이 없으며 대기 라벨을 반환한다`() {
        val tracker = PowerTrendTracker()
        assertTrue(tracker.samples().isEmpty())
        assertTrue(tracker.loadValues().isEmpty())
        assertEquals("실시간 전력 대기 중", PowerTrendTracker.formatTrendLeftLabel(tracker.samples(), false, kst))
        assertEquals("대기", PowerTrendTracker.formatTrendRightLabel(tracker.samples(), false, kst))
    }

    @Test
    fun `세션 시작 시 실제 초기 샘플 1건만 기록되고 가짜 파형을 생성하지 않는다`() {
        val tracker = PowerTrendTracker()
        val ts0 = 1_760_000_000_000L // Asia/Seoul 고정 타임존 기준 시각

        tracker.resetWithInitial(1.2, ts0)
        assertEquals(listOf(1.2), tracker.loadValues())

        val expectedClock = PowerTrendTracker.formatClockTime(ts0, kst)
        assertEquals(
            "$expectedClock 시작 · 1회 측정",
            PowerTrendTracker.formatTrendLeftLabel(tracker.samples(), isLockedOut = false, timeZone = kst)
        )
        assertEquals(
            "현재 $expectedClock",
            PowerTrendTracker.formatTrendRightLabel(tracker.samples(), isLockedOut = false, timeZone = kst)
        )
    }

    @Test
    fun `실시간 폴링 샘플이 누적되면 최대 개수까지만 유지하고 범위 밖 값은 안전하게 보정한다`() {
        val tracker = PowerTrendTracker(maxSamples = 4)
        val baseTs = 1_760_000_000_000L

        tracker.resetWithInitial(1.2, baseTs)
        tracker.recordSample(2.4, baseTs + 4_000L)
        tracker.recordSample(5.8, baseTs + 8_000L) // V2L 최대 3.6 kW 클램핑
        tracker.recordSample(Double.NaN, baseTs + 12_000L) // 비정상 수치 -> 0.0 보정
        tracker.recordSample(1.5, baseTs + 16_000L) // 용량 4개 초과로 최초 1.2 제거

        assertEquals(listOf(2.4, 3.6, 0.0, 1.5), tracker.loadValues())
        assertEquals(baseTs + 4_000L, tracker.samples().first().timestampMs)
        assertEquals(baseTs + 16_000L, tracker.samples().last().timestampMs)

        val firstClock = PowerTrendTracker.formatClockTime(baseTs + 4_000L, kst)
        val lastClock = PowerTrendTracker.formatClockTime(baseTs + 16_000L, kst)
        assertEquals(
            "$firstClock ~ 최근 4회 실시간",
            PowerTrendTracker.formatTrendLeftLabel(tracker.samples(), isLockedOut = false, timeZone = kst)
        )
        assertEquals(
            "현재 $lastClock",
            PowerTrendTracker.formatTrendRightLabel(tracker.samples(), isLockedOut = false, timeZone = kst)
        )
    }

    @Test
    fun `V2L 차단 시 0kW 샘플이 기록되고 차단 라벨을 표시한다`() {
        val baseTs = 1_760_000_000_000L
        val tracker = PowerTrendTracker()
        tracker.resetWithInitial(1.2, baseTs)
        tracker.recordSample(1.8, baseTs + 4_000L)
        tracker.recordSample(0.0, baseTs + 8_000L)

        assertEquals(listOf(1.2, 1.8, 0.0), tracker.loadValues())
        val firstClock = PowerTrendTracker.formatClockTime(baseTs, kst)
        val lockoutClock = PowerTrendTracker.formatClockTime(baseTs + 8_000L, kst)
        assertEquals(
            "$firstClock ~ 최근 3회 실시간",
            PowerTrendTracker.formatTrendLeftLabel(tracker.samples(), isLockedOut = true, timeZone = kst)
        )
        assertEquals(
            "$lockoutClock 차단 (0.0 kW)",
            PowerTrendTracker.formatTrendRightLabel(tracker.samples(), isLockedOut = true, timeZone = kst)
        )

        tracker.clear()
        assertEquals(emptyList<PowerSample>(), tracker.samples())
    }
}

