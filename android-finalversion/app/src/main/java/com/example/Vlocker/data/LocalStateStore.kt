package com.example.Vlocker.data

import android.content.Context
import com.example.Vlocker.network.FavoriteItem
import com.example.Vlocker.network.RoutePoint
import com.example.Vlocker.network.UserVehicleData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.abs

/**
 * 완전 오프라인 기동 시에도 마지막으로 연결된 차량 제원과 '우리 집' 목적지를
 * 로컬에서 바로 복원할 수 있도록 저장하는 경량 SharedPreferences 캐시.
 */
class LocalStateStore(context: Context) {

    companion object {
        private const val PREF_NAME = "vlocker_local_safety_state"
        private const val KEY_VEHICLE_MODEL = "vehicle_model"
        private const val KEY_BATTERY_CAP = "battery_capacity"
        private const val KEY_CURB_WEIGHT = "curb_weight"
        private const val KEY_AVG_EFF = "avg_efficiency"
        private const val KEY_HOME_NAME = "home_name"
        private const val KEY_HOME_ADDRESS = "home_address"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LNG = "home_lng"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_CACHED_ROUTE_ORIG_LAT = "cached_route_orig_lat"
        private const val KEY_CACHED_ROUTE_ORIG_LNG = "cached_route_orig_lng"
        private const val KEY_CACHED_ROUTE_DEST_LAT = "cached_route_dest_lat"
        private const val KEY_CACHED_ROUTE_DEST_LNG = "cached_route_dest_lng"
        private const val KEY_CACHED_ROUTE_POLYLINE = "cached_route_polyline"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveUserVehicle(data: UserVehicleData) {
        prefs.edit()
            .putString(KEY_VEHICLE_MODEL, data.vehicleModel)
            .putString(KEY_BATTERY_CAP, data.batteryCapacity.toString())
            .putInt(KEY_CURB_WEIGHT, data.curbWeight)
            .putString(KEY_AVG_EFF, data.avgEfficiency.toString())
            .apply()
    }

    fun loadUserVehicle(): UserVehicleData? {
        val model = prefs.getString(KEY_VEHICLE_MODEL, null) ?: return null
        val cap = prefs.getString(KEY_BATTERY_CAP, null)?.toDoubleOrNull() ?: return null
        val weight = prefs.getInt(KEY_CURB_WEIGHT, -1).takeIf { it > 0 } ?: return null
        val eff = prefs.getString(KEY_AVG_EFF, null)?.toDoubleOrNull() ?: return null
        return UserVehicleData(
            id = 1,
            name = "캠퍼 드라이버",
            vehicleModel = model,
            batteryCapacity = cap,
            curbWeight = weight,
            avgEfficiency = eff
        )
    }

    fun saveHomeFavorite(item: FavoriteItem) {
        prefs.edit()
            .putString(KEY_HOME_NAME, item.name)
            .putString(KEY_HOME_ADDRESS, item.address.orEmpty())
            .putString(KEY_HOME_LAT, item.latitude.toString())
            .putString(KEY_HOME_LNG, item.longitude.toString())
            .apply()
    }

    fun loadHomeFavorite(): FavoriteItem? {
        val name = prefs.getString(KEY_HOME_NAME, null) ?: return null
        val lat = prefs.getString(KEY_HOME_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(KEY_HOME_LNG, null)?.toDoubleOrNull() ?: return null
        val addr = prefs.getString(KEY_HOME_ADDRESS, null)
        return FavoriteItem(
            id = 0,
            name = name,
            address = addr,
            latitude = lat,
            longitude = lng
        )
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun loadServerUrl(): String? {
        return prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }
    }

    fun saveCachedRoutePolyline(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        points: List<RoutePoint>
    ) {
        if (points.size < 2) return
        prefs.edit()
            .putString(KEY_CACHED_ROUTE_ORIG_LAT, originLat.toString())
            .putString(KEY_CACHED_ROUTE_ORIG_LNG, originLng.toString())
            .putString(KEY_CACHED_ROUTE_DEST_LAT, destLat.toString())
            .putString(KEY_CACHED_ROUTE_DEST_LNG, destLng.toString())
            .putString(KEY_CACHED_ROUTE_POLYLINE, gson.toJson(points))
            .apply()
    }

    fun loadMatchingRoutePolyline(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): List<RoutePoint>? {
        val oLat = prefs.getString(KEY_CACHED_ROUTE_ORIG_LAT, null)?.toDoubleOrNull() ?: return null
        val oLng = prefs.getString(KEY_CACHED_ROUTE_ORIG_LNG, null)?.toDoubleOrNull() ?: return null
        val dLat = prefs.getString(KEY_CACHED_ROUTE_DEST_LAT, null)?.toDoubleOrNull() ?: return null
        val dLng = prefs.getString(KEY_CACHED_ROUTE_DEST_LNG, null)?.toDoubleOrNull() ?: return null
        if (abs(oLat - originLat) > 0.015 ||
            abs(oLng - originLng) > 0.015 ||
            abs(dLat - destLat) > 0.015 ||
            abs(dLng - destLng) > 0.015
        ) {
            return null
        }
        val rawJson = prefs.getString(KEY_CACHED_ROUTE_POLYLINE, null) ?: return null
        val type = object : TypeToken<List<RoutePoint>>() {}.type
        return runCatching { gson.fromJson<List<RoutePoint>>(rawJson, type) }
            .getOrNull()
            ?.takeIf { it.size >= 2 }
    }
}

