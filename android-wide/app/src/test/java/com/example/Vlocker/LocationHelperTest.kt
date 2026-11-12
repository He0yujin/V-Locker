package com.example.Vlocker

import com.example.Vlocker.location.ActiveLocationResult
import com.example.Vlocker.location.LocationCandidate
import com.example.Vlocker.location.LocationHelper
import com.example.Vlocker.location.UnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationHelperTest {

    private val nowEpochMs = 1_760_000_000_000L
    private val nowElapsedMs = 5_000_000L

    @Test
    fun `신선한 국내 캐시 좌표는 30초 이내이고 정확도가 양호하면 스타트업 캐시로 허용된다`() {
        val freshSeoul = LocationCandidate(
            provider = "gps",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 15_000L,
            accuracyMeters = 18f,
            elapsedRealtimeMs = nowElapsedMs - 15_000L
        )
        val agedSeoul = LocationCandidate(
            provider = "gps",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 45_000L,
            accuracyMeters = 18f,
            elapsedRealtimeMs = nowElapsedMs - 45_000L
        )
        val staleSeoul = LocationCandidate(
            provider = "gps",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 300_000L,
            accuracyMeters = 18f,
            elapsedRealtimeMs = nowElapsedMs - 300_000L
        )
        val lowAccSeoul = LocationCandidate(
            provider = "network",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 10_000L,
            accuracyMeters = 1200f,
            elapsedRealtimeMs = nowElapsedMs - 10_000L
        )
        val freshOverseas = LocationCandidate(
            provider = "gps",
            latitude = 37.4220,
            longitude = -122.0841,
            timeMs = nowEpochMs - 5_000L,
            accuracyMeters = 10f,
            elapsedRealtimeMs = nowElapsedMs - 5_000L
        )

        assertTrue(
            LocationHelper.isFreshKoreanCandidate(
                freshSeoul, nowEpochMs, nowElapsedMs,
                LocationHelper.MAX_STARTUP_CACHE_AGE_MS,
                LocationHelper.MAX_CACHE_ACCURACY_METERS
            )
        )
        assertFalse(
            LocationHelper.isFreshKoreanCandidate(
                agedSeoul, nowEpochMs, nowElapsedMs,
                LocationHelper.MAX_STARTUP_CACHE_AGE_MS,
                LocationHelper.MAX_CACHE_ACCURACY_METERS
            )
        )
        assertFalse(
            LocationHelper.isFreshKoreanCandidate(
                staleSeoul, nowEpochMs, nowElapsedMs,
                LocationHelper.MAX_STARTUP_CACHE_AGE_MS,
                LocationHelper.MAX_CACHE_ACCURACY_METERS
            )
        )
        assertFalse(
            LocationHelper.isFreshKoreanCandidate(
                lowAccSeoul, nowEpochMs, nowElapsedMs,
                LocationHelper.MAX_STARTUP_CACHE_AGE_MS,
                LocationHelper.MAX_CACHE_ACCURACY_METERS
            )
        )
        assertFalse(
            LocationHelper.isFreshKoreanCandidate(
                freshOverseas, nowEpochMs, nowElapsedMs,
                LocationHelper.MAX_STARTUP_CACHE_AGE_MS,
                LocationHelper.MAX_CACHE_ACCURACY_METERS
            )
        )

        val result = LocationHelper.selectFreshStartupCache(
            listOf(staleSeoul, agedSeoul, freshOverseas, lowAccSeoul, freshSeoul),
            nowEpochMs,
            nowElapsedMs
        )
        assertEquals("현재 스타트업 캐시는 30초 이내 신선한 국내 좌표만 선택해야 함", freshSeoul, result?.candidate)
        assertEquals(false, result?.fromActiveFix)
    }

    @Test
    fun `오래된 캐시는 스타트업에서 건너뛰지만 능동 GPS 요청 후 한국 좌표가 오면 즉시 적용된다`() {
        val staleSeoul = LocationCandidate(
            provider = "gps",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 600_000L,
            accuracyMeters = 15f,
            elapsedRealtimeMs = nowElapsedMs - 600_000L
        )
        assertNull(LocationHelper.selectFreshStartupCache(listOf(staleSeoul), nowEpochMs, nowElapsedMs))

        val activeBusan = LocationCandidate(
            provider = "gps",
            latitude = 35.1796,
            longitude = 129.0756,
            timeMs = nowEpochMs,
            accuracyMeters = 12f,
            elapsedRealtimeMs = nowElapsedMs
        )
        val overseasActive = LocationCandidate(
            provider = "network",
            latitude = 37.4220,
            longitude = -122.0841,
            timeMs = nowEpochMs,
            accuracyMeters = 100f,
            elapsedRealtimeMs = nowElapsedMs
        )

        val res = LocationHelper.resolveAfterActiveFetch(
            activeCandidates = listOf(overseasActive, activeBusan),
            cachedCandidates = listOf(staleSeoul),
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs
        )

        assertTrue(res is ActiveLocationResult.KoreanLocation)
        val korean = res as ActiveLocationResult.KoreanLocation
        assertEquals(activeBusan, korean.candidate)
        assertTrue(korean.fromActiveFix)
        assertEquals("GPS 실시간 확인", korean.sourceNote)
    }

    @Test
    fun `능동 요청에서 비슷한 시각의 국내 좌표가 여럿이면 GPS를 Network보다 우선한다`() {
        val networkSeoul = LocationCandidate(
            provider = "network",
            latitude = 37.5650,
            longitude = 126.9750,
            timeMs = nowEpochMs - 500L,
            accuracyMeters = 600f,
            elapsedRealtimeMs = nowElapsedMs - 500L
        )
        val gpsSeoul = LocationCandidate(
            provider = "gps",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 2_000L,
            accuracyMeters = 10f,
            elapsedRealtimeMs = nowElapsedMs - 2_000L
        )

        val picked = LocationHelper.pickBestCandidate(
            listOf(networkSeoul, gpsSeoul),
            nowEpochMs,
            nowElapsedMs,
            preferKorean = true
        )
        assertEquals(gpsSeoul, picked)
    }

    @Test
    fun `능동 요청이 해외 좌표를 반환하면 한국 캐시로 덮어쓰지 않고 해외 좌표로 알린다`() {
        val recentKoreaCache = LocationCandidate(
            provider = "gps",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 600_000L,
            accuracyMeters = 15f,
            elapsedRealtimeMs = nowElapsedMs - 600_000L
        )
        val emulatorOverseas = LocationCandidate(
            provider = "gps",
            latitude = 37.4220,
            longitude = -122.0841,
            timeMs = nowEpochMs,
            accuracyMeters = 5f,
            elapsedRealtimeMs = nowElapsedMs
        )

        val res = LocationHelper.resolveAfterActiveFetch(
            activeCandidates = listOf(emulatorOverseas),
            cachedCandidates = listOf(recentKoreaCache),
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs
        )
        assertTrue(res is ActiveLocationResult.OverseasLocation)
        assertEquals(emulatorOverseas, (res as ActiveLocationResult.OverseasLocation).candidate)
    }

    @Test
    fun `능동 요청이 시간 초과일 때 30분 이내 국내 캐시가 있으면 최근 위치로 보완하고 없으면 TIMEOUT을 반환한다`() {
        val cache20MinAgo = LocationCandidate(
            provider = "gps",
            latitude = 37.5665,
            longitude = 126.9780,
            timeMs = nowEpochMs - 20 * 60_000L,
            accuracyMeters = 25f,
            elapsedRealtimeMs = nowElapsedMs - 20 * 60_000L
        )
        val resWithFallback = LocationHelper.resolveAfterActiveFetch(
            activeCandidates = emptyList(),
            cachedCandidates = listOf(cache20MinAgo),
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs
        )
        assertTrue(resWithFallback is ActiveLocationResult.KoreanLocation)
        val fallback = resWithFallback as ActiveLocationResult.KoreanLocation
        assertFalse(fallback.fromActiveFix)
        assertEquals("최근 GPS 확인", fallback.sourceNote)

        val cache45MinAgo = cache20MinAgo.copy(
            timeMs = nowEpochMs - 45 * 60_000L,
            elapsedRealtimeMs = nowElapsedMs - 45 * 60_000L
        )
        val resExpired = LocationHelper.resolveAfterActiveFetch(
            activeCandidates = emptyList(),
            cachedCandidates = listOf(cache45MinAgo),
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs
        )
        assertEquals(ActiveLocationResult.Unavailable(UnavailableReason.TIMEOUT_OR_EMPTY), resExpired)
    }

    @Test
    fun `대략적 위치 권한만 허용되면 SecurityException 방지를 위해 gps 공급자를 제외한다`() {
        val available = listOf("gps", "fused", "network")
        val allEnabled = { _: String -> true }

        val fineProviders31 = LocationHelper.candidateProviders(
            fineGranted = true,
            coarseGranted = true,
            availableProviders = available,
            isProviderEnabled = allEnabled,
            sdkInt = 34
        )
        assertEquals(listOf("gps", "fused", "network"), fineProviders31)

        val coarseOnly31 = LocationHelper.candidateProviders(
            fineGranted = false,
            coarseGranted = true,
            availableProviders = available,
            isProviderEnabled = allEnabled,
            sdkInt = 34
        )
        assertEquals(listOf("fused", "network"), coarseOnly31)

        val coarseOnly28 = LocationHelper.candidateProviders(
            fineGranted = false,
            coarseGranted = true,
            availableProviders = available,
            isProviderEnabled = allEnabled,
            sdkInt = 28
        )
        assertEquals(listOf("network"), coarseOnly28)

        val denied = LocationHelper.candidateProviders(
            fineGranted = false,
            coarseGranted = false,
            availableProviders = available,
            isProviderEnabled = allEnabled,
            sdkInt = 34
        )
        assertTrue(denied.isEmpty())
    }
}
