package com.example.Vlocker

import com.example.Vlocker.ui.UiFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFormattersTest {

    @Test
    fun `한국 좌표 판별이 서울 강릉은 허용하고 해외 에뮬레이터 기본좌표는 제외한다`() {
        assertTrue(UiFormatters.isKoreanCoordinate(37.5665, 126.9780)) // 서울 시청
        assertTrue(UiFormatters.isKoreanCoordinate(37.6242, 128.7420)) // 강릉 안반데기
        assertTrue(UiFormatters.isKoreanCoordinate(33.4996, 126.5312)) // 제주
        assertFalse(UiFormatters.isKoreanCoordinate(37.4220, -122.0841)) // 에뮬레이터 기본 Mountain View
        assertFalse(UiFormatters.isKoreanCoordinate(35.6762, 139.6503)) // 도쿄
    }

    @Test
    fun `시간 포맷팅은 쉬운 한국어 시 분 표기로 변환한다`() {
        assertEquals("6시간 40분", UiFormatters.formatDurationHours(6.67))
        assertEquals("1시간 25분", UiFormatters.formatDurationMinutes(85))
        assertEquals("30분", UiFormatters.formatDurationHours(0.5))
        assertEquals("2시간", UiFormatters.formatDurationMinutes(120))
        assertEquals("0분", UiFormatters.formatDurationHours(-1.0))
    }

    @Test
    fun `퍼센트와 거리와 기온 포맷팅이 깔끔하게 표시된다`() {
        assertEquals("28%", UiFormatters.formatPercentWholeOrDecimal(28.0))
        assertEquals("28.5%", UiFormatters.formatPercentWholeOrDecimal(28.5))
        assertEquals("86 km", UiFormatters.formatDistanceKm(86.0))
        assertEquals("12.4 km", UiFormatters.formatDistanceKm(12.4))
        assertEquals("-5°C", UiFormatters.formatTemperature(-5.0))
        assertEquals("-4.5°C", UiFormatters.formatTemperature(-4.5))
        assertEquals("균형", UiFormatters.marginModeTitle("NORMAL"))
        assertEquals("안심", UiFormatters.marginModeTitle("SAFE"))
        assertEquals("최대 사용", UiFormatters.marginModeTitle("MAX_USE"))
        assertEquals("균형 (+15% 여유)", UiFormatters.formatSafetyMarginBonus("NORMAL", 1.15))
        assertEquals("안심 (+35% 여유)", UiFormatters.formatSafetyMarginBonus("SAFE", 1.35))
        assertEquals("최대 사용 (필수치만 보존)", UiFormatters.formatSafetyMarginBonus("MAX_USE", 1.0))
    }
}

