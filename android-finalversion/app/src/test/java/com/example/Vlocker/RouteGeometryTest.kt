package com.example.Vlocker

import com.example.Vlocker.network.RoutePoint
import com.example.Vlocker.ui.RouteGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RouteGeometryTest {

    @Test
    fun projectRouteToCanvas_preservesNorthUpAndWestToEastDirection() {
        // 강릉 안반데기 (동쪽) -> 서울 강남역 (서쪽, 약간 남쪽)
        val gangneungToSeoul = listOf(
            RoutePoint(37.6242, 128.7420),
            RoutePoint(37.5600, 127.8500),
            RoutePoint(37.4979, 127.0276)
        )
        val layout = RouteGeometry.projectRouteToCanvas(
            rawPoints = gangneungToSeoul,
            canvasWidth = 800f,
            canvasHeight = 320f,
            padLeft = 36f,
            padTop = 76f,
            padRight = 96f,
            padBottom = 52f,
            zoomLevel = 1.0f,
            minScaleBarPx = 50f,
            maxScaleBarPx = 130f
        )
        assertNotNull(layout)
        val pts = layout!!.points
        assertEquals(3, pts.size)
        // 출발지(동쪽)가 도착지(서쪽)보다 X가 커야 함 (실제 방위 유지)
        assertTrue(pts.first().x > pts.last().x)
        // 출발지(북쪽)가 도착지(남쪽)보다 Y가 작아야 함 (North up)
        assertTrue(pts.first().y < pts.last().y)
        assertTrue(layout.scaleBarKm >= 5.0)
        assertTrue(layout.scaleBarPx > 0f)
    }

    @Test
    fun projectRouteToCanvas_respectsUniformAspectRatioWithCosLat() {
        // 정사각형(동서 10km, 남북 10km)에 가까운 L자형 실제 도로 정점
        // 37도 위도에서 남북 0.09도 ≈ 10km, 동서 0.1125도 * cos(37°) ≈ 10km
        val lShape = listOf(
            RoutePoint(37.50, 127.00),
            RoutePoint(37.59, 127.00),
            RoutePoint(37.59, 127.1125)
        )
        val layout = RouteGeometry.projectRouteToCanvas(
            rawPoints = lShape,
            canvasWidth = 1000f,
            canvasHeight = 400f,
            padLeft = 20f,
            padTop = 20f,
            padRight = 20f,
            padBottom = 20f,
            zoomLevel = 1.0f,
            minScaleBarPx = 40f,
            maxScaleBarPx = 120f
        )!!
        val vertPx = abs(layout.points[1].y - layout.points[0].y)
        val horizPx = abs(layout.points[2].x - layout.points[1].x)
        // 등거리 aspect-preserving 투영이므로 화면 폭과 무관하게 vertPx ≈ horizPx 여야 함
        assertTrue("vertPx=$vertPx, horizPx=$horizPx", abs(vertPx - horizPx) / vertPx < 0.05f)
    }

    @Test
    fun projectRouteToCanvas_zoomInIncreasesScaleAndDecreasesScaleBarKm() {
        val sampleRoute = RouteGeometry.buildEstimatedPolyline(
            originLat = 37.6242,
            originLng = 128.7420,
            destLat = 37.4979,
            destLng = 127.0276
        )
        val outLayout = RouteGeometry.projectRouteToCanvas(
            rawPoints = sampleRoute,
            canvasWidth = 600f,
            canvasHeight = 280f,
            padLeft = 24f,
            padTop = 48f,
            padRight = 64f,
            padBottom = 40f,
            zoomLevel = 0.75f,
            minScaleBarPx = 40f,
            maxScaleBarPx = 110f
        )!!
        val inLayout = RouteGeometry.projectRouteToCanvas(
            rawPoints = sampleRoute,
            canvasWidth = 600f,
            canvasHeight = 280f,
            padLeft = 24f,
            padTop = 48f,
            padRight = 64f,
            padBottom = 40f,
            zoomLevel = 2.25f,
            minScaleBarPx = 40f,
            maxScaleBarPx = 110f
        )!!
        assertTrue(inLayout.pxPerKm > outLayout.pxPerKm * 2.5f)
        assertTrue(inLayout.scaleBarKm <= outLayout.scaleBarKm)
    }

    @Test
    fun projectRouteToCanvas_handlesEmptyOrSinglePointGracefully() {
        assertNull(
            RouteGeometry.projectRouteToCanvas(
                rawPoints = listOf(RoutePoint(37.5, 127.0)),
                canvasWidth = 400f,
                canvasHeight = 200f,
                padLeft = 10f,
                padTop = 10f,
                padRight = 10f,
                padBottom = 10f,
                zoomLevel = 1.0f,
                minScaleBarPx = 30f,
                maxScaleBarPx = 90f
            )
        )
    }
}

