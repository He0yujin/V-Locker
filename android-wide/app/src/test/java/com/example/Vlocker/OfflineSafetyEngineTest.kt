package com.example.Vlocker

import com.example.Vlocker.safety.OfflineSafetyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSafetyEngineTest {

    @Test
    fun offlinePrediction_matchesBackendWinterSafetyPhysics() {
        val pred = OfflineSafetyEngine.buildOfflinePrediction(
            destinationName = "우리 집",
            originLat = 37.6242,
            originLng = 128.7420,
            destLat = null,
            destLng = null,
            passengers = 2,
            currentSocPercent = 61.0,
            batteryCapacityKwh = 77.4,
            avgEfficiencyKmKwh = 5.4,
            curbWeightKg = 2010.0,
            v2lCurrentLoadKw = 1.2
        )

        assertTrue(pred.isFallback)
        assertEquals(-5.0, pred.weather.temperature, 0.01)
        assertEquals(640.0, pred.navigation.elevationGainM, 0.01)
        assertEquals(86.0, pred.navigation.distanceKm, 0.01)
        assertEquals(15.93, pred.breakdown.baseDrivingKwh, 0.05)

        val safe = pred.recommendations["SAFE"]!!
        val normal = pred.recommendations["NORMAL"]!!
        val maxUse = pred.recommendations["MAX_USE"]!!
        assertTrue(safe.reserveSocPercent > normal.reserveSocPercent)
        assertTrue(normal.reserveSocPercent >= maxUse.reserveSocPercent)
        assertTrue(maxUse.reserveSocPercent >= 10.0)
        assertTrue(normal.isSufficient)
    }

    @Test
    fun offlinePrediction_usesHaversineEstimateWhenCoordinatesPresent() {
        // 강릉 안반데기 -> 서울 강남 (~152km 직선 -> 약 205km 도로 추정, 86km보다 크게 보존해야 안전함)
        val gangneungToSeoul = OfflineSafetyEngine.buildOfflinePrediction(
            destinationName = "우리 집",
            originLat = 37.6242,
            originLng = 128.7420,
            destLat = 37.4979,
            destLng = 127.0276,
            passengers = 2,
            currentSocPercent = 61.0,
            batteryCapacityKwh = 77.4,
            avgEfficiencyKmKwh = 5.4,
            curbWeightKg = 2010.0,
            v2lCurrentLoadKw = 1.2
        )
        assertTrue(gangneungToSeoul.navigation.distanceKm > 150.0)

        // 서울 시청 -> 강남역 (~10km)
        val seoulToGangnam = OfflineSafetyEngine.buildOfflinePrediction(
            destinationName = "우리 집",
            originLat = 37.5665,
            originLng = 126.9780,
            destLat = 37.4979,
            destLng = 127.0276,
            passengers = 2,
            currentSocPercent = 61.0,
            batteryCapacityKwh = 77.4,
            avgEfficiencyKmKwh = 5.4,
            curbWeightKg = 2010.0,
            v2lCurrentLoadKw = 1.2
        )
        assertTrue(seoulToGangnam.navigation.distanceKm in 8.0..20.0)
    }

    @Test
    fun offlineSession_evaluatesWarningsAndLockoutIrreversibly() {
        val initial = OfflineSafetyEngine.createLocalSession(
            destinationName = "우리 집",
            distanceKm = 86.0,
            elevationGainM = 640.0,
            expectedTemp = -5.0,
            departureTime = "내일 오전 10:00",
            marginMode = "NORMAL",
            targetSoc = 28.0,
            currentSoc = 61.0,
            currentV2lLoadKw = 1.2,
            batteryCapacityKwh = 77.4
        )
        assertEquals("ACTIVE", initial.v2lStatus)
        assertTrue(initial.relayConnected)
        assertFalse(initial.utilityModeActive)

        val warn60 = OfflineSafetyEngine.updateSessionLocally(initial, newSoc = 29.4, batteryCapacityKwh = 77.4)
        assertEquals("WARN_60M", warn60.v2lStatus)
        assertEquals(60, warn60.warningLevel)

        val warn30 = OfflineSafetyEngine.updateSessionLocally(warn60, newSoc = 28.6, batteryCapacityKwh = 77.4)
        assertEquals("WARN_30M", warn30.v2lStatus)
        assertEquals(30, warn30.warningLevel)

        val locked = OfflineSafetyEngine.updateSessionLocally(warn30, newSoc = 27.9, batteryCapacityKwh = 77.4)
        assertEquals("LOCKED_OUT", locked.v2lStatus)
        assertFalse(locked.relayConnected)
        assertTrue(locked.utilityModeActive)
        assertEquals(0.0, locked.currentV2lLoadKw, 0.001)

        // 차단 이후 SoC가 다시 높게 들어와도 임의 복귀하지 않아야 함
        val stillLocked = OfflineSafetyEngine.updateSessionLocally(locked, newSoc = 45.0, batteryCapacityKwh = 77.4)
        assertEquals("LOCKED_OUT", stillLocked.v2lStatus)
        assertFalse(stillLocked.relayConnected)
    }

    @Test
    fun offlineChargerExtensionAndLocalLockout_workWithoutBackend() {
        val session = OfflineSafetyEngine.createLocalSession(
            destinationName = "우리 집",
            distanceKm = 86.0,
            elevationGainM = 640.0,
            expectedTemp = -5.0,
            departureTime = "내일 오전 10:00",
            marginMode = "NORMAL",
            targetSoc = 28.0,
            currentSoc = 28.5,
            currentV2lLoadKw = 1.2,
            batteryCapacityKwh = 77.4
        )
        val nearby = OfflineSafetyEngine.searchOfflineNearbyChargers(37.6242, 128.7420)
        assertTrue(nearby.isNotEmpty())
        assertTrue(nearby.first().name.contains("대관령") || nearby.first().name.contains("강릉"))

        val (extended, extInfo) = OfflineSafetyEngine.extendSessionLocally(
            session = session,
            chargerName = nearby.first().name,
            distanceKm = nearby.first().distanceKm,
            batteryCapacityKwh = 77.4
        )
        assertTrue(extended.targetSoc < session.targetSoc)
        assertTrue(extended.targetSoc >= OfflineSafetyEngine.EMERGENCY_CHARGER_RESERVE_MIN_SOC)
        assertTrue(extInfo.additionalHours > 0.0)
        assertEquals("ACTIVE", extended.v2lStatus)

        val lockout = OfflineSafetyEngine.executeLocalLockout(
            sessionId = session.sessionId,
            currentSoc = 28.0,
            batteryCapacityKwh = 77.4,
            avgEfficiencyKmKwh = 5.4,
            expectedTempCelsius = -5.0
        )
        assertEquals("LOCKED_OUT", lockout.v2lStatus)
        assertEquals(21.0, lockout.cabinTargetTempCelsius, 0.01)
        assertTrue(lockout.returnDistanceGuaranteedKm > 50.0)
    }

    @Test
    fun offlinePlaceSearch_returnsKoreanResultsForMatchingKeywords() {
        val homeMatches = OfflineSafetyEngine.searchOfflinePlaces("우리 집", 37.5665, 126.9780)
        assertTrue(homeMatches.any { it.name.contains("우리 집") })

        val chargerMatches = OfflineSafetyEngine.searchOfflinePlaces("강릉", 37.5665, 126.9780)
        assertTrue(chargerMatches.any { it.name.contains("강릉") })
    }
}

