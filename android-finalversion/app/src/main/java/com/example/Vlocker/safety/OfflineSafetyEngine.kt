package com.example.Vlocker.safety

import com.example.Vlocker.network.BreakdownInfo
import com.example.Vlocker.network.ChargerItem
import com.example.Vlocker.network.DestinationInfo
import com.example.Vlocker.network.DestinationSummary
import com.example.Vlocker.network.ExtendSessionData
import com.example.Vlocker.network.LockoutData
import com.example.Vlocker.network.MarginRecommendation
import com.example.Vlocker.network.NavigationInfo
import com.example.Vlocker.network.PlaceItem
import com.example.Vlocker.network.PredictData
import com.example.Vlocker.network.SessionStatusData
import com.example.Vlocker.network.VehicleInfo
import com.example.Vlocker.network.WeatherInfo
import com.example.Vlocker.ui.RouteGeometry
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 완전 오프라인(서버 미연결) 환경에서도 백엔드(calculator.py / v2l_controller.py)와
 * 동일한 물리·안전 로직으로 예측, 실시간 감시, 단계별 경고, 충전소 연장, V2L 릴레이 차단을 수행하는 로컬 안전 엔진.
 */
object OfflineSafetyEngine {

    const val LOCAL_OFFLINE_SESSION_ID: Int = -1

    const val GRAVITY_ACCELERATION = 9.80665
    const val JOULES_TO_KWH = 3_600_000.0
    const val DEFAULT_PASSENGER_WEIGHT = 70.0
    const val DEFAULT_LUGGAGE_WEIGHT = 40.0
    const val DEFAULT_V2L_LOAD_KW = 1.2
    const val REGEN_BRAKING_RECOVERY = 0.75
    const val MIN_BMS_PROTECT_SOC = 10.0
    const val EMERGENCY_CHARGER_RESERVE_MIN_SOC = 15.0

    const val DEFAULT_FALLBACK_TEMP_C = -5.0
    const val DEFAULT_FALLBACK_ELEVATION_GAIN_M = 640.0
    const val DEFAULT_FALLBACK_DISTANCE_KM = 86.0
    const val DEFAULT_FALLBACK_DURATION_MIN = 85

    const val DEFAULT_HOME_LAT = 37.4979
    const val DEFAULT_HOME_LNG = 127.0276

    private const val ROAD_TORTUOSITY_FACTOR = 1.35
    private const val EARTH_RADIUS_KM = 6371.0

    data class OfflineHub(
        val name: String,
        val address: String,
        val category: String,
        val lat: Double,
        val lng: Double
    )

    private val koreaEvChargerHubs = listOf(
        OfflineHub("대관령휴게소(강릉) 급속충전소", "강원 평창군 대관령면 경강로 5721", "고속도로 급속충전소", 37.6845, 128.7290),
        OfflineHub("강릉 감자원종장 전기차충전소", "강원 강릉시 성산면 유산길 12", "공공 급속충전소", 37.7052, 128.8189),
        OfflineHub("속초시청 공영 급속충전소", "강원 속초시 중앙로 183", "공공 급속충전소", 38.2070, 128.5918),
        OfflineHub("원주(부산방향)휴게소 급속충전소", "강원 원주시 호저면 원문로 1430", "고속도로 급속충전소", 37.4198, 127.9436),
        OfflineHub("춘천휴게소 급속충전소", "강원 춘천시 동내면 순환대로 1080", "고속도로 급속충전소", 37.8261, 127.7689),
        OfflineHub("여주휴게소(인천방향) E-pit", "경기 여주시 가남읍 여주남로 722", "초고속 충전소", 37.2435, 127.5892),
        OfflineHub("양평 만남의광장 급속충전소", "경기 양평군 양평읍 경강로 2060", "공공 급속충전소", 37.4917, 127.4875),
        OfflineHub("가평휴게소(서울방향) E-pit", "경기 가평군 설악면 미사리로 544", "초고속 충전소", 37.6819, 127.5268),
        OfflineHub("난지 한강공원 전기차충전소", "서울 마포구 한강난지로 162", "공원 급속충전소", 37.5665, 126.8764),
        OfflineHub("강남구청 공영주차장 급속충전소", "서울 강남구 학동로 426", "도심 급속충전소", 37.5172, 127.0473),
        OfflineHub("천안삼거리휴게소 E-pit", "충남 천안시 동남구 충절로 1419", "초고속 충전소", 36.7873, 127.1758),
        OfflineHub("대전 유성IC 공영 급속충전소", "대전 유성구 월드컵대로 299", "도심 급속충전소", 36.3647, 127.3267),
        OfflineHub("전주 월드컵경기장 급속충전소", "전북 전주시 덕진구 기린대로 1055", "공공 급속충전소", 35.8682, 127.0644),
        OfflineHub("대구 칠곡휴게소 E-pit", "경북 칠곡군 왜관읍 경부고속도로 159", "초고속 충전소", 35.9958, 128.4316),
        OfflineHub("부산 문현 E-pit 초고속충전소", "부산 남구 전포대로 133", "초고속 충전소", 35.1466, 129.0652),
        OfflineHub("광주 상무시민공원 급속충전소", "광주 서구 상무민주로 61", "공공 급속충전소", 35.1583, 126.8481)
    )

    private val koreaFallbackPlaces = listOf(
        OfflineHub("우리 집", "서울 강남구 기본 등록지", "저장 목적지", DEFAULT_HOME_LAT, DEFAULT_HOME_LNG),
        OfflineHub("강릉 안반데기", "강원 강릉시 왕산면 대기리", "차박 명소", 37.6242, 128.7420),
        OfflineHub("서울 한강 난지캠핑장", "서울 마포구 한강난지로 28", "도심 캠핑장", 37.5699, 126.8744),
        OfflineHub("양평 두물머리 주차장", "경기 양평군 양서면 두물머리길", "근교 차박지", 37.5336, 127.3164),
        OfflineHub("평창 청옥산 육백마지기", "강원 평창군 미탄면 회동리", "고지대 차박지", 37.3924, 128.4986),
        OfflineHub("강남역", "서울 강남구 강남대로 396", "주요 도심", DEFAULT_HOME_LAT, DEFAULT_HOME_LNG)
    ) + koreaEvChargerHubs

    fun calcTemperatureEfficiency(tempCelsius: Double): Double {
        return when {
            tempCelsius >= 20.0 -> 1.0
            tempCelsius >= 0.0 -> 1.0 - 0.007 * (20.0 - tempCelsius)
            tempCelsius >= -20.0 -> 0.86 - 0.0095 * abs(tempCelsius)
            else -> 0.65
        }
    }

    fun calcElevationEnergy(
        elevationGainM: Double,
        curbWeightKg: Double,
        passengers: Int = 2,
        luggageKg: Double = DEFAULT_LUGGAGE_WEIGHT,
        powertrainEfficiency: Double = 0.88,
        applyDownhillRegen: Boolean = true
    ): Double {
        if (elevationGainM <= 0.0) return 0.0
        val totalMass = curbWeightKg + (passengers.coerceAtLeast(1) * DEFAULT_PASSENGER_WEIGHT) + luggageKg
        val potentialJoules = totalMass * GRAVITY_ACCELERATION * elevationGainM
        val grossKwh = potentialJoules / (JOULES_TO_KWH * powertrainEfficiency)
        val netKwh = if (applyDownhillRegen) grossKwh * (1.0 - REGEN_BRAKING_RECOVERY) else grossKwh
        return round2(netKwh)
    }

    fun calcColdStartHvacSpike(tempCelsius: Double): Double {
        return when {
            tempCelsius >= 15.0 -> 0.3
            tempCelsius >= 0.0 -> 0.5
            tempCelsius >= -10.0 -> 0.8
            else -> 1.8
        }
    }

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLng / 2).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
        return EARTH_RADIUS_KM * c
    }

    fun estimateOfflineRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double?,
        destLng: Double?,
        distanceOverrideKm: Double? = null,
        elevationOverrideM: Double? = null
    ): Triple<Double, Double, Int> {
        val elevM = elevationOverrideM ?: DEFAULT_FALLBACK_ELEVATION_GAIN_M
        val distKm = when {
            distanceOverrideKm != null && distanceOverrideKm > 0.0 -> round1(distanceOverrideKm)
            destLat != null && destLng != null -> {
                val straightKm = haversineKm(originLat, originLng, destLat, destLng)
                if (straightKm < 0.2) 2.0 else round1(max(2.0, straightKm * ROAD_TORTUOSITY_FACTOR))
            }
            else -> DEFAULT_FALLBACK_DISTANCE_KM
        }
        val durationMin = if (destLat == null || destLng == null) {
            DEFAULT_FALLBACK_DURATION_MIN
        } else {
            max(8, (distKm * 1.15).roundToInt())
        }
        return Triple(distKm, elevM, durationMin)
    }

    fun buildOfflinePrediction(
        destinationName: String,
        originLat: Double,
        originLng: Double,
        destLat: Double?,
        destLng: Double?,
        passengers: Int,
        currentSocPercent: Double,
        batteryCapacityKwh: Double,
        avgEfficiencyKmKwh: Double,
        curbWeightKg: Double,
        v2lCurrentLoadKw: Double,
        vehicleModel: String = "IONIQ 5 Long Range",
        expectedTempCelsius: Double? = null,
        distanceOverrideKm: Double? = null,
        elevationOverrideM: Double? = null
    ): PredictData {
        val safeCap = batteryCapacityKwh.takeIf { it > 1.0 } ?: 77.4
        val safeEff = avgEfficiencyKmKwh.takeIf { it > 0.5 } ?: 5.4
        val safeCurb = curbWeightKg.takeIf { it > 500.0 } ?: 2010.0
        val safePassengers = passengers.coerceIn(1, 7)
        val loadKw = if (v2lCurrentLoadKw > 0.1) v2lCurrentLoadKw else DEFAULT_V2L_LOAD_KW

        val resolvedDestLat = destLat ?: DEFAULT_HOME_LAT
        val resolvedDestLng = destLng ?: DEFAULT_HOME_LNG
        val tempC = expectedTempCelsius ?: DEFAULT_FALLBACK_TEMP_C

        val (distKm, elevM, durMin) = estimateOfflineRoute(
            originLat = originLat,
            originLng = originLng,
            destLat = destLat,
            destLng = destLng,
            distanceOverrideKm = distanceOverrideKm,
            elevationOverrideM = elevationOverrideM
        )

        val etaTemp = calcTemperatureEfficiency(tempC)
        val baseDrivingKwh = distKm / safeEff
        val elevationKwh = calcElevationEnergy(
            elevationGainM = elevM,
            curbWeightKg = safeCurb,
            passengers = safePassengers,
            applyDownhillRegen = true
        )
        val compensatedDrivingKwh = (baseDrivingKwh + elevationKwh) / etaTemp
        val spikeKwh = calcColdStartHvacSpike(tempC)
        val pureReturnKwh = compensatedDrivingKwh + spikeKwh
        val pureReturnSoc = (pureReturnKwh / safeCap) * 100.0

        val currentKwh = (currentSocPercent.coerceIn(0.0, 100.0) / 100.0) * safeCap
        val configs = listOf(
            Triple("SAFE", "안심", 1.35 to "예측 전력의 +35% 여유 확보 · 폭설·정체·추가 난방 방어"),
            Triple("NORMAL", "균형", 1.15 to "예측 전력의 +15% 여유 확보 · 주행 및 환경 변수 반영 추천"),
            Triple("MAX_USE", "최대 사용", 1.00 to "귀가 최소 필수 전력만 보존 · V2L 가용 시간 극대화")
        )

        val recommendations = LinkedHashMap<String, MarginRecommendation>()
        for ((mode, modeKr, multAndDesc) in configs) {
            val (mult, desc) = multAndDesc
            val targetKwhRaw = pureReturnKwh * mult
            val rawSoc = (targetKwhRaw / safeCap) * 100.0
            val targetSoc = round1(min(95.0, max(MIN_BMS_PROTECT_SOC, rawSoc)))
            val finalTargetKwh = round2((targetSoc / 100.0) * safeCap)
            val usableKwh = max(0.0, currentKwh - finalTargetKwh)
            val usableHours = round1(usableKwh / loadKw)

            recommendations[mode] = MarginRecommendation(
                mode = mode,
                modeNameKr = modeKr,
                safetyMultiplier = mult,
                reserveSocPercent = targetSoc,
                requiredKwh = finalTargetKwh,
                v2lUsableKwh = round2(usableKwh),
                v2lUsableHours = usableHours,
                isSufficient = currentSocPercent > targetSoc,
                description = desc
            )
        }

        return PredictData(
            destination = DestinationInfo(
                name = destinationName.ifBlank { "우리 집" },
                originLat = originLat,
                originLng = originLng,
                destLat = resolvedDestLat,
                destLng = resolvedDestLng
            ),
            vehicle = VehicleInfo(
                vehicleModel = vehicleModel.ifBlank { "IONIQ 5 Long Range" },
                batteryCapacityKwh = safeCap,
                avgEfficiencyKmKwh = safeEff,
                curbWeightKg = safeCurb
            ),
            weather = WeatherInfo(
                temperature = tempC,
                isFallback = true,
                source = "Offline Local Safety Engine",
                description = "서버 미연결 상태: 동절기 안전 기준 기온(${tempC.toInt()}°C) 적용"
            ),
            navigation = NavigationInfo(
                distanceKm = distKm,
                elevationGainM = elevM,
                estimatedDurationMin = durMin,
                isFallback = true,
                geometryType = "ESTIMATED",
                polylinePoints = RouteGeometry.buildEstimatedPolyline(
                    originLat = originLat,
                    originLng = originLng,
                    destLat = resolvedDestLat,
                    destLng = resolvedDestLng
                ),
                source = "Offline Local Safety Engine",
                description = String.format(Locale.US, "오프라인 안전 경로 기준(%.1fkm, +%.0fm)", distKm, elevM)
            ),
            isFallback = true,
            breakdown = BreakdownInfo(
                baseDrivingKwh = round2(baseDrivingKwh),
                elevationKwh = round2(elevationKwh),
                temperatureEfficiencyFactor = round3(etaTemp),
                efficiencyLossPercent = round1((1.0 - etaTemp) * 100.0),
                coldStartHvacSpikeKwh = round2(spikeKwh),
                pureReturnKwh = round2(pureReturnKwh),
                pureReturnSocPercent = round1(pureReturnSoc)
            ),
            recommendations = recommendations
        )
    }

    fun evaluateWarningStatus(
        currentSoc: Double,
        targetSoc: Double,
        hoursLeft: Double
    ): Pair<String, Int?> {
        if (currentSoc <= targetSoc) return "LOCKED_OUT" to null
        if (hoursLeft <= 0.5 || (currentSoc - targetSoc) <= 1.0) return "WARN_30M" to 30
        if (hoursLeft <= 1.0 || (currentSoc - targetSoc) <= 2.0) return "WARN_60M" to 60
        return "ACTIVE" to null
    }

    fun computeHoursLeft(
        currentSoc: Double,
        targetSoc: Double,
        batteryCapacityKwh: Double,
        loadKw: Double
    ): Double {
        val safeCap = batteryCapacityKwh.takeIf { it > 1.0 } ?: 77.4
        val safeLoad = loadKw.takeIf { it > 0.1 } ?: DEFAULT_V2L_LOAD_KW
        val remainingKwh = max(0.0, (currentSoc - targetSoc) / 100.0 * safeCap)
        return round1(remainingKwh / safeLoad)
    }

    fun createLocalSession(
        destinationName: String,
        distanceKm: Double,
        elevationGainM: Double,
        expectedTemp: Double,
        departureTime: String,
        marginMode: String,
        targetSoc: Double,
        currentSoc: Double,
        currentV2lLoadKw: Double,
        batteryCapacityKwh: Double,
        isFallback: Boolean = true
    ): SessionStatusData {
        val clampedSoc = round1(currentSoc.coerceIn(0.0, 100.0))
        val safeLoad = if (currentV2lLoadKw > 0.1) round1(currentV2lLoadKw) else DEFAULT_V2L_LOAD_KW
        val hoursLeft = computeHoursLeft(clampedSoc, targetSoc, batteryCapacityKwh, safeLoad)
        val (status, warnLevel) = evaluateWarningStatus(clampedSoc, targetSoc, hoursLeft)
        val locked = status == "LOCKED_OUT"

        return SessionStatusData(
            sessionId = LOCAL_OFFLINE_SESSION_ID,
            destinationName = destinationName,
            currentSoc = clampedSoc,
            targetSoc = round1(targetSoc),
            remainingMarginPct = round1(max(0.0, clampedSoc - targetSoc)),
            currentV2lLoadKw = if (locked) 0.0 else safeLoad,
            estimatedHoursLeft = if (locked) 0.0 else hoursLeft,
            v2lStatus = status,
            warningLevel = warnLevel,
            relayConnected = !locked,
            utilityModeActive = locked,
            isFallback = isFallback,
            destinationSummary = DestinationSummary(
                destination = destinationName,
                departure = departureTime,
                distanceKm = round1(distanceKm),
                elevationGainM = elevationGainM,
                tempCelsius = expectedTemp,
                marginMode = marginMode
            )
        )
    }

    fun updateSessionLocally(
        session: SessionStatusData,
        newSoc: Double,
        newLoadKw: Double? = null,
        batteryCapacityKwh: Double = 77.4
    ): SessionStatusData {
        val effectiveLoad = when {
            newLoadKw != null && newLoadKw > 0.1 -> round1(newLoadKw)
            session.currentV2lLoadKw > 0.1 -> session.currentV2lLoadKw
            else -> DEFAULT_V2L_LOAD_KW
        }
        val clampedSoc = round1(newSoc.coerceIn(0.0, 100.0))
        val hoursLeft = computeHoursLeft(clampedSoc, session.targetSoc, batteryCapacityKwh, effectiveLoad)

        val (nextStatus, warnLevel, relay, utility) = if (session.v2lStatus == "LOCKED_OUT") {
            Quad("LOCKED_OUT", null, false, true)
        } else {
            val (evaluated, warn) = evaluateWarningStatus(clampedSoc, session.targetSoc, hoursLeft)
            val isLocked = evaluated == "LOCKED_OUT"
            Quad(evaluated, warn, !isLocked, isLocked)
        }

        return session.copy(
            currentSoc = clampedSoc,
            remainingMarginPct = round1(max(0.0, clampedSoc - session.targetSoc)),
            currentV2lLoadKw = if (nextStatus == "LOCKED_OUT") 0.0 else effectiveLoad,
            estimatedHoursLeft = if (nextStatus == "LOCKED_OUT") 0.0 else hoursLeft,
            v2lStatus = nextStatus,
            warningLevel = warnLevel,
            relayConnected = relay,
            utilityModeActive = utility,
            isFallback = true
        )
    }

    fun extendSessionLocally(
        session: SessionStatusData,
        chargerName: String,
        distanceKm: Double,
        elevationGainM: Double = 120.0,
        batteryCapacityKwh: Double = 77.4,
        avgEfficiencyKmKwh: Double = 5.4,
        curbWeightKg: Double = 2010.0,
        passengers: Int = 2
    ): Pair<SessionStatusData, ExtendSessionData> {
        val safeCap = batteryCapacityKwh.takeIf { it > 1.0 } ?: 77.4
        val loadKw = session.currentV2lLoadKw.takeIf { it > 0.1 } ?: DEFAULT_V2L_LOAD_KW
        val tempC = session.destinationSummary?.tempCelsius ?: DEFAULT_FALLBACK_TEMP_C

        val pred = buildOfflinePrediction(
            destinationName = chargerName,
            originLat = 0.0,
            originLng = 0.0,
            destLat = null,
            destLng = null,
            passengers = passengers,
            currentSocPercent = session.currentSoc,
            batteryCapacityKwh = safeCap,
            avgEfficiencyKmKwh = avgEfficiencyKmKwh,
            curbWeightKg = curbWeightKg,
            v2lCurrentLoadKw = loadKw,
            expectedTempCelsius = tempC,
            distanceOverrideKm = distanceKm,
            elevationOverrideM = elevationGainM
        )

        val normalSoc = pred.recommendations["NORMAL"]?.reserveSocPercent ?: EMERGENCY_CHARGER_RESERVE_MIN_SOC
        val prevTarget = session.targetSoc
        val newTarget = round1(min(prevTarget, max(EMERGENCY_CHARGER_RESERVE_MIN_SOC, normalSoc)))
        val gainedPct = max(0.0, round1(prevTarget - newTarget))
        val addedHours = round1((gainedPct / 100.0 * safeCap) / loadKw)

        val hoursLeft = computeHoursLeft(session.currentSoc, newTarget, safeCap, loadKw)
        val (nextStatus, warnLevel) = evaluateWarningStatus(session.currentSoc, newTarget, hoursLeft)
        val isLocked = nextStatus == "LOCKED_OUT"
        val newDest = "$chargerName (오프라인 충전소 경유)"

        val updatedSession = session.copy(
            destinationName = newDest,
            targetSoc = newTarget,
            remainingMarginPct = round1(max(0.0, session.currentSoc - newTarget)),
            estimatedHoursLeft = if (isLocked) 0.0 else hoursLeft,
            v2lStatus = nextStatus,
            warningLevel = warnLevel,
            relayConnected = !isLocked,
            utilityModeActive = isLocked,
            isFallback = true,
            destinationSummary = session.destinationSummary?.copy(
                destination = newDest,
                distanceKm = round1(distanceKm),
                elevationGainM = elevationGainM
            )
        )

        val extData = ExtendSessionData(
            sessionId = session.sessionId,
            newDestination = newDest,
            previousTargetSoc = prevTarget,
            newTargetSoc = newTarget,
            gainedSocPct = gainedPct,
            additionalHours = addedHours,
            v2lStatus = nextStatus
        )
        return updatedSession to extData
    }

    fun executeLocalLockout(
        sessionId: Int,
        currentSoc: Double,
        batteryCapacityKwh: Double = 77.4,
        avgEfficiencyKmKwh: Double = 5.4,
        expectedTempCelsius: Double = DEFAULT_FALLBACK_TEMP_C
    ): LockoutData {
        val safeCap = batteryCapacityKwh.takeIf { it > 1.0 } ?: 77.4
        val safeEff = avgEfficiencyKmKwh.takeIf { it > 0.5 } ?: 5.4
        val clampedSoc = round1(currentSoc.coerceIn(0.0, 100.0))
        val preservedKwh = (clampedSoc / 100.0) * safeCap
        val etaTemp = calcTemperatureEfficiency(expectedTempCelsius)
        val spikeKwh = calcColdStartHvacSpike(expectedTempCelsius)
        val guaranteedKm = round1(max(0.0, preservedKwh - spikeKwh) * etaTemp * safeEff)

        return LockoutData(
            sessionId = sessionId,
            v2lStatus = "LOCKED_OUT",
            relayConnected = false,
            utilityModeActive = true,
            cabinTargetTempCelsius = 21.0,
            bmsPreservedSoc = clampedSoc,
            returnDistanceGuaranteedKm = guaranteedKm
        )
    }

    fun searchOfflineNearbyChargers(lat: Double, lng: Double, limit: Int = 5): List<ChargerItem> {
        return koreaEvChargerHubs
            .map { hub ->
                val straight = haversineKm(lat, lng, hub.lat, hub.lng)
                ChargerItem(
                    name = hub.name,
                    address = "${hub.address} (오프라인 기준)",
                    distanceKm = round1(max(0.5, straight * ROAD_TORTUOSITY_FACTOR)),
                    lat = hub.lat,
                    lng = hub.lng
                )
            }
            .sortedBy { it.distanceKm }
            .take(limit.coerceIn(1, 10))
    }

    fun searchOfflinePlaces(query: String, originLat: Double, originLng: Double, limit: Int = 5): List<PlaceItem> {
        val clean = query.trim().lowercase(Locale.KOREA)
        if (clean.isEmpty()) return emptyList()

        val matched = koreaFallbackPlaces.filter { hub ->
            hub.name.lowercase(Locale.KOREA).contains(clean) ||
                hub.address.lowercase(Locale.KOREA).contains(clean) ||
                hub.category.lowercase(Locale.KOREA).contains(clean)
        }
        val pool = if (matched.isNotEmpty()) {
            matched.sortedBy { haversineKm(originLat, originLng, it.lat, it.lng) }
        } else {
            emptyList()
        }
        return pool.take(limit.coerceIn(1, 10)).map { hub ->
            PlaceItem(
                name = hub.name,
                address = "${hub.address} (오프라인 저장 목록)",
                category = hub.category,
                lat = hub.lat,
                lng = hub.lng
            )
        }
    }

    private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0
    private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
    private fun round3(value: Double): Double = (value * 1000.0).roundToInt() / 1000.0

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
