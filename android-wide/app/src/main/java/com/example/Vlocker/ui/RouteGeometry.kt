package com.example.Vlocker.ui

import com.example.Vlocker.network.RoutePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class ProjectedCanvasPoint(
    val x: Float,
    val y: Float
)

data class ProjectedRouteLayout(
    val points: List<ProjectedCanvasPoint>,
    val pxPerKm: Float,
    val gridSpacingPx: Float,
    val centerX: Float,
    val centerY: Float,
    val scaleBarKm: Double,
    val scaleBarPx: Float
)

/**
 * 위경도 도로 폴리라인을 한국 위도(cos(lat)) 보정 등거리 평면에 투영하고,
 * 비율 왜곡 없는 화면 좌표·줌·축척 막대를 산출하는 순수 Kotlin 지오메트리 헬퍼.
 */
object RouteGeometry {

    private const val KM_PER_DEG_LAT = 111.32
    private val NICE_SCALE_KM_STEPS = doubleArrayOf(
        0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 50.0, 100.0, 200.0
    )

    fun buildEstimatedPolyline(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        segments: Int = 8
    ): List<RoutePoint> {
        val dLat = destLat - originLat
        val dLng = destLng - originLng
        val span = hypot(dLat, dLng)
        if (span < 1e-6) {
            return listOf(
                RoutePoint(round5(originLat), round5(originLng)),
                RoutePoint(round5(destLat), round5(destLng))
            )
        }

        val perpLat = -dLng / span
        val perpLng = dLat / span
        val amplitude = min(0.18, span * 0.07)

        val result = ArrayList<RoutePoint>(segments + 1)
        for (i in 0..segments) {
            val t = i.toDouble() / segments.toDouble()
            val bend = sin(t * PI) * sin(t * PI * 1.5) * amplitude
            val lat = originLat + dLat * t + perpLat * bend
            val lng = originLng + dLng * t + perpLng * bend
            result.add(RoutePoint(round5(lat), round5(lng)))
        }
        return result
    }

    fun chooseScaleBarKm(pxPerKm: Float, minPx: Float, maxPx: Float): Double {
        if (pxPerKm <= 0f) return 10.0
        val targetMid = (minPx + maxPx) * 0.5f
        var best = NICE_SCALE_KM_STEPS.last()
        var bestDiff = Float.MAX_VALUE
        for (stepKm in NICE_SCALE_KM_STEPS) {
            val widthPx = (stepKm * pxPerKm).toFloat()
            if (widthPx in minPx..maxPx) {
                return stepKm
            }
            val diff = abs(widthPx - targetMid)
            if (diff < bestDiff) {
                bestDiff = diff
                best = stepKm
            }
        }
        return best
    }

    fun projectRouteToCanvas(
        rawPoints: List<RoutePoint>,
        canvasWidth: Float,
        canvasHeight: Float,
        padLeft: Float,
        padTop: Float,
        padRight: Float,
        padBottom: Float,
        zoomLevel: Float,
        minScaleBarPx: Float,
        maxScaleBarPx: Float
    ): ProjectedRouteLayout? {
        val valid = rawPoints.filter { it.lat.isFinite() && it.lng.isFinite() }
        if (valid.size < 2 || canvasWidth <= 0f || canvasHeight <= 0f) return null

        val minLat = valid.minOf { it.lat }
        val maxLat = valid.maxOf { it.lat }
        val minLng = valid.minOf { it.lng }
        val maxLng = valid.maxOf { it.lng }

        val midLat = (minLat + maxLat) * 0.5
        val midLng = (minLng + maxLng) * 0.5
        val cosLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.2)

        val eastKmList = ArrayList<Double>(valid.size)
        val northKmList = ArrayList<Double>(valid.size)
        var minEast = Double.POSITIVE_INFINITY
        var maxEast = Double.NEGATIVE_INFINITY
        var minNorth = Double.POSITIVE_INFINITY
        var maxNorth = Double.NEGATIVE_INFINITY

        for (pt in valid) {
            val eastKm = (pt.lng - midLng) * KM_PER_DEG_LAT * cosLat
            val northKm = (pt.lat - midLat) * KM_PER_DEG_LAT
            eastKmList.add(eastKm)
            northKmList.add(northKm)
            if (eastKm < minEast) minEast = eastKm
            if (eastKm > maxEast) maxEast = eastKm
            if (northKm < minNorth) minNorth = northKm
            if (northKm > maxNorth) maxNorth = northKm
        }

        val spanEast = max(0.35, maxEast - minEast)
        val spanNorth = max(0.35, maxNorth - minNorth)

        val availW = (canvasWidth - padLeft - padRight).coerceAtLeast(24f)
        val availH = (canvasHeight - padTop - padBottom).coerceAtLeast(24f)
        val basePxPerKm = min(availW / spanEast, availH / spanNorth).toFloat()
        val pxPerKm = (basePxPerKm * zoomLevel.coerceIn(0.75f, 2.5f)).coerceAtLeast(0.1f)

        val centerX = padLeft + availW * 0.5f
        val centerY = padTop + availH * 0.5f

        val projected = ArrayList<ProjectedCanvasPoint>(valid.size)
        for (i in valid.indices) {
            val sx = centerX + (eastKmList[i] * pxPerKm).toFloat()
            val sy = centerY - (northKmList[i] * pxPerKm).toFloat()
            projected.add(ProjectedCanvasPoint(sx, sy))
        }

        val scaleKm = chooseScaleBarKm(pxPerKm, minScaleBarPx, maxScaleBarPx)
        val scalePx = (scaleKm * pxPerKm).toFloat().coerceIn(minScaleBarPx * 0.6f, maxScaleBarPx * 1.25f)
        val gridSpacingPx = (scalePx * 1.1f).coerceAtLeast(24f)

        return ProjectedRouteLayout(
            points = projected,
            pxPerKm = pxPerKm,
            gridSpacingPx = gridSpacingPx,
            centerX = centerX,
            centerY = centerY,
            scaleBarKm = scaleKm,
            scaleBarPx = scalePx
        )
    }

    fun formatScaleLabel(scaleKm: Double): String {
        return if (scaleKm < 1.0) {
            "${(scaleKm * 1000.0).roundToInt()} m"
        } else {
            "${scaleKm.roundToInt()} km"
        }
    }

    private fun round5(value: Double): Double = (value * 100_000.0).roundToInt() / 100_000.0
}

