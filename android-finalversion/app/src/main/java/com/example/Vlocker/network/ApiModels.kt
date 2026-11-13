package com.example.Vlocker.network

import com.google.gson.annotations.SerializedName

/**
 * FastAPI 공통 응답 래퍼: {"status": 200, "data": {...}, "message": "..."}
 */
data class APIResponse<T>(
    @SerializedName("status") val status: Int,
    @SerializedName("data") val data: T?,
    @SerializedName("message") val message: String
)

// =========================================================
// 1. 카카오 로컬 검색 & 주변 충전소 탐색 (/places/search, /chargers/nearby)
// =========================================================
data class PlacesSearchData(
    @SerializedName("places") val places: List<PlaceItem>,
    @SerializedName("is_fallback") val isFallback: Boolean,
    @SerializedName("source") val source: String? = null
)

data class PlaceItem(
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double
)

data class ChargersNearbyData(
    @SerializedName("chargers") val chargers: List<ChargerItem>,
    @SerializedName("is_fallback") val isFallback: Boolean,
    @SerializedName("source") val source: String? = null
)

data class ChargerItem(
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double
)

// =========================================================
// 2. 차량 스펙 & 운전자 평소 전비 (/user)
// =========================================================
data class UserVehicleData(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String? = null,
    @SerializedName("vehicle_model") val vehicleModel: String,
    @SerializedName("battery_capacity") val batteryCapacity: Double,
    @SerializedName("curb_weight") val curbWeight: Int,
    @SerializedName("avg_efficiency") val avgEfficiency: Double
)

data class UserVehicleUpdateRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("vehicle_model") val vehicleModel: String,
    @SerializedName("battery_capacity") val batteryCapacity: Double,
    @SerializedName("curb_weight") val curbWeight: Int,
    @SerializedName("avg_efficiency") val avgEfficiency: Double
)

// =========================================================
// 3. AI 배터리 소모량 예측 (/predict)
// =========================================================
data class PredictRequest(
    @SerializedName("destination_name") val destinationName: String,
    @SerializedName("origin_lat") val originLat: Double = 37.6242,
    @SerializedName("origin_lng") val originLng: Double = 128.7420,
    @SerializedName("dest_lat") val destLat: Double? = null,
    @SerializedName("dest_lng") val destLng: Double? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("elevation_gain_m") val elevationGainM: Double? = null,
    @SerializedName("departure_time") val departureTime: String = "내일 오전 10:00",
    @SerializedName("passengers") val passengers: Int = 2,
    @SerializedName("current_soc_percent") val currentSocPercent: Double = 61.0,
    @SerializedName("expected_temp_celsius") val expectedTempCelsius: Double? = null,
    @SerializedName("avg_efficiency_km_kwh") val avgEfficiencyKmKwh: Double? = null,
    @SerializedName("battery_capacity_kwh") val batteryCapacityKwh: Double? = null,
    @SerializedName("curb_weight_kg") val curbWeightKg: Double? = null,
    @SerializedName("v2l_current_load_kw") val v2lCurrentLoadKw: Double = 1.2,
    @SerializedName("force_fallback") val forceFallback: Boolean = false
)

data class PredictData(
    @SerializedName("destination") val destination: DestinationInfo,
    @SerializedName("vehicle") val vehicle: VehicleInfo,
    @SerializedName("weather") val weather: WeatherInfo,
    @SerializedName("navigation") val navigation: NavigationInfo,
    @SerializedName("is_fallback") val isFallback: Boolean,
    @SerializedName("breakdown") val breakdown: BreakdownInfo,
    @SerializedName("recommendations") val recommendations: Map<String, MarginRecommendation>
)

data class DestinationInfo(
    @SerializedName("name") val name: String,
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double,
    @SerializedName("dest_lat") val destLat: Double,
    @SerializedName("dest_lng") val destLng: Double
)

data class VehicleInfo(
    @SerializedName("vehicle_model") val vehicleModel: String,
    @SerializedName("battery_capacity_kwh") val batteryCapacityKwh: Double,
    @SerializedName("avg_efficiency_km_kwh") val avgEfficiencyKmKwh: Double,
    @SerializedName("curb_weight_kg") val curbWeightKg: Double
)

data class WeatherInfo(
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("grid_nx") val gridNx: Int? = null,
    @SerializedName("grid_ny") val gridNy: Int? = null,
    @SerializedName("is_fallback") val isFallback: Boolean,
    @SerializedName("source") val source: String? = null,
    @SerializedName("description") val description: String? = null
)

data class RoutePoint(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double
)

data class NavigationInfo(
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("elevation_gain_m") val elevationGainM: Double,
    @SerializedName("estimated_duration_min") val estimatedDurationMin: Int? = null,
    @SerializedName("is_fallback") val isFallback: Boolean,
    @SerializedName("geometry_type") val geometryType: String? = null,
    @SerializedName("polyline_points") val polylinePoints: List<RoutePoint>? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("description") val description: String? = null
)

data class BreakdownInfo(
    @SerializedName("base_driving_kwh") val baseDrivingKwh: Double,
    @SerializedName("elevation_kwh") val elevationKwh: Double,
    @SerializedName("temperature_efficiency_factor") val temperatureEfficiencyFactor: Double,
    @SerializedName("efficiency_loss_percent") val efficiencyLossPercent: Double,
    @SerializedName("cold_start_hvac_spike_kwh") val coldStartHvacSpikeKwh: Double,
    @SerializedName("pure_return_kwh") val pureReturnKwh: Double,
    @SerializedName("pure_return_soc_percent") val pureReturnSocPercent: Double
)

data class MarginRecommendation(
    @SerializedName("mode") val mode: String,
    @SerializedName("mode_name_kr") val modeNameKr: String,
    @SerializedName("safety_multiplier") val safetyMultiplier: Double,
    @SerializedName("reserve_soc_percent") val reserveSocPercent: Double,
    @SerializedName("required_kwh") val requiredKwh: Double,
    @SerializedName("v2l_usable_kwh") val v2lUsableKwh: Double,
    @SerializedName("v2l_usable_hours") val v2lUsableHours: Double,
    @SerializedName("is_sufficient") val isSufficient: Boolean,
    @SerializedName("description") val description: String
)

// =========================================================
// 4. V2L 모니터링 세션 & 제어 (/sessions, /tick, /extend, /lockout)
// =========================================================
data class CreateSessionRequest(
    @SerializedName("destination_name") val destinationName: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("elevation_gain_m") val elevationGainM: Double,
    @SerializedName("expected_temp") val expectedTemp: Double,
    @SerializedName("departure_time") val departureTime: String,
    @SerializedName("passengers") val passengers: Int = 2,
    @SerializedName("margin_mode") val marginMode: String = "NORMAL",
    @SerializedName("target_soc") val targetSoc: Double,
    @SerializedName("current_soc") val currentSoc: Double,
    @SerializedName("current_v2l_load_kw") val currentV2lLoadKw: Double = 1.2,
    @SerializedName("is_fallback") val isFallback: Boolean = false
)

data class SessionStatusData(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("destination_name") val destinationName: String? = null,
    @SerializedName("current_soc") val currentSoc: Double,
    @SerializedName("target_soc") val targetSoc: Double,
    @SerializedName("remaining_margin_pct") val remainingMarginPct: Double,
    @SerializedName("current_v2l_load_kw") val currentV2lLoadKw: Double,
    @SerializedName("estimated_hours_left") val estimatedHoursLeft: Double,
    @SerializedName("v2l_status") val v2lStatus: String,
    @SerializedName("warning_level") val warningLevel: Int? = null,
    @SerializedName("relay_connected") val relayConnected: Boolean,
    @SerializedName("utility_mode_active") val utilityModeActive: Boolean,
    @SerializedName("is_fallback") val isFallback: Boolean = false,
    @SerializedName("destination_summary") val destinationSummary: DestinationSummary? = null
)

data class DestinationSummary(
    @SerializedName("destination") val destination: String,
    @SerializedName("departure") val departure: String,
    @SerializedName("distance_km") val distanceKm: Double,
    @SerializedName("elevation_gain_m") val elevationGainM: Double,
    @SerializedName("temp_celsius") val tempCelsius: Double,
    @SerializedName("margin_mode") val marginMode: String
)

data class TickRequest(
    @SerializedName("soc_decrement") val socDecrement: Double = 2.0,
    @SerializedName("load_kw") val loadKw: Double? = null
)

data class SocSyncRequest(
    @SerializedName("current_soc") val currentSoc: Double,
    @SerializedName("load_kw") val loadKw: Double? = null
)

data class FavoriteItem(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("user_id") val userId: Int? = null
)

data class FavoritesListData(
    @SerializedName("favorites") val favorites: List<FavoriteItem>
)

data class FavoriteCreateRequest(
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class FavoriteUpdateRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class SessionEventItem(
    @SerializedName("id") val id: Int,
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("previous_soc") val previousSoc: Double? = null,
    @SerializedName("current_soc") val currentSoc: Double? = null,
    @SerializedName("previous_target_soc") val previousTargetSoc: Double? = null,
    @SerializedName("new_target_soc") val newTargetSoc: Double? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class SessionEventsData(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("events") val events: List<SessionEventItem>
)

data class ExtendRequest(
    @SerializedName("origin_lat") val originLat: Double = 37.6242,
    @SerializedName("origin_lng") val originLng: Double = 128.7420,
    @SerializedName("nearest_charger_name") val nearestChargerName: String? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("elevation_gain_m") val elevationGainM: Double = 80.0,
    @SerializedName("dest_lat") val destLat: Double? = null,
    @SerializedName("dest_lng") val destLng: Double? = null
)

data class ExtendSessionData(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("new_destination") val newDestination: String,
    @SerializedName("previous_target_soc") val previousTargetSoc: Double,
    @SerializedName("new_target_soc") val newTargetSoc: Double,
    @SerializedName("gained_soc_pct") val gainedSocPct: Double,
    @SerializedName("additional_hours") val additionalHours: Double,
    @SerializedName("v2l_status") val v2lStatus: String,
    @SerializedName("dest_lat") val destLat: Double? = null,
    @SerializedName("dest_lng") val destLng: Double? = null,
    @SerializedName("geometry_type") val geometryType: String? = null,
    @SerializedName("polyline_points") val polylinePoints: List<RoutePoint>? = null
)

data class LockoutData(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("v2l_status") val v2lStatus: String,
    @SerializedName("relay_connected") val relayConnected: Boolean,
    @SerializedName("utility_mode_active") val utilityModeActive: Boolean,
    @SerializedName("cabin_target_temp_celsius") val cabinTargetTempCelsius: Double,
    @SerializedName("bms_preserved_soc") val bmsPreservedSoc: Double,
    @SerializedName("return_distance_guaranteed_km") val returnDistanceGuaranteedKm: Double
)