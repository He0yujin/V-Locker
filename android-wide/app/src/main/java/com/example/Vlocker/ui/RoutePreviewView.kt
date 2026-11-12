package com.example.Vlocker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DimenRes
import com.example.Vlocker.R
import com.example.Vlocker.network.RoutePoint
import kotlin.math.abs
import kotlin.math.hypot

class RoutePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var originLabel: String = "현재 위치"
    private var destLabel: String = "우리 집"
    private var distanceKm: Double = 86.0
    private var elevationGainM: Double = 640.0
    private var isTealTheme: Boolean = false
    private var zoomLevel: Float = 1.0f
    private var geometryType: String = "ESTIMATED"
    private var routeSignature: String = ""

    private var polylinePoints: List<RoutePoint> = RouteGeometry.buildEstimatedPolyline(
        originLat = 37.6242,
        originLng = 128.7420,
        destLat = 37.4979,
        destLng = 127.0276
    )

    private fun dim(@DimenRes resId: Int): Float = resources.getDimension(resId)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EDF2EE")
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DCE6E0")
        style = Paint.Style.STROKE
        strokeWidth = dim(R.dimen.vl_cv_map_grid_stroke)
    }
    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dim(R.dimen.vl_cv_map_major_grid_stroke)
    }
    private val routeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22087F70")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#182725")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val estimatedDashEffect = DashPathEffect(
        floatArrayOf(dim(R.dimen.vl_cv_map_dash_on), dim(R.dimen.vl_cv_map_dash_off)),
        0f
    )
    private val dotOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val originDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#182725")
        style = Paint.Style.FILL
    }
    private val destDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#087F70")
        style = Paint.Style.FILL
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#182725")
        textSize = dim(R.dimen.vl_cv_map_label_text_normal)
        isFakeBoldText = true
    }
    private val labelPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5FFFFFF")
        style = Paint.Style.FILL
    }
    private val labelPillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22182725")
        style = Paint.Style.STROKE
        strokeWidth = dim(R.dimen.vl_cv_map_pill_stroke)
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF")
        style = Paint.Style.FILL
    }
    private val compassTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475854")
        textSize = dim(R.dimen.vl_cv_map_compass_text)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val compassArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#087F70")
        style = Paint.Style.FILL
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546460")
        textSize = dim(R.dimen.vl_cv_map_caption_text)
    }
    private val scaleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475854")
        style = Paint.Style.STROKE
        strokeWidth = dim(R.dimen.vl_cv_map_scale_stroke)
        strokeCap = Paint.Cap.SQUARE
    }

    private val clipPath = Path()
    private val routePath = Path()
    private val compassTriangle = Path()

    fun setRouteInfo(
        origin: String,
        dest: String,
        distance: Double,
        elevation: Double,
        polylinePoints: List<RoutePoint> = emptyList(),
        originLat: Double = 37.6242,
        originLng: Double = 128.7420,
        destLat: Double = 37.4979,
        destLng: Double = 127.0276,
        geometryType: String = "ESTIMATED",
        tealAccent: Boolean = false
    ) {
        originLabel = origin.ifBlank { "현재 위치" }
        destLabel = dest.ifBlank { "목적지" }
        distanceKm = distance
        elevationGainM = elevation
        isTealTheme = tealAccent

        val effectivePoints = if (polylinePoints.size >= 2) {
            polylinePoints
        } else {
            RouteGeometry.buildEstimatedPolyline(originLat, originLng, destLat, destLng)
        }
        val effectiveGeomType = if (polylinePoints.size >= 2) geometryType else "ESTIMATED"

        val newSig = buildString {
            append(effectivePoints.first().lat)
            append(',')
            append(effectivePoints.first().lng)
            append("->")
            append(effectivePoints.last().lat)
            append(',')
            append(effectivePoints.last().lng)
            append('#')
            append(effectivePoints.size)
            append('#')
            append(effectiveGeomType)
        }
        if (routeSignature.isNotEmpty() && routeSignature != newSig) {
            zoomLevel = 1.0f
        }
        routeSignature = newSig
        this.polylinePoints = effectivePoints
        this.geometryType = effectiveGeomType

        val accentHex = if (tealAccent) "#087F70" else "#182725"
        routePaint.color = Color.parseColor(accentHex)
        routePaint.pathEffect = if (effectiveGeomType == "REAL_ROAD" || effectiveGeomType == "CACHED_ROAD") {
            null
        } else {
            estimatedDashEffect
        }
        originDotPaint.color = Color.parseColor(accentHex)
        destDotRingPaint.color = Color.parseColor(if (tealAccent) "#182725" else "#087F70")
        invalidate()
    }

    fun zoomIn() {
        zoomLevel = (zoomLevel + 0.25f).coerceAtMost(2.5f)
        invalidate()
    }

    fun zoomOut() {
        zoomLevel = (zoomLevel - 0.25f).coerceAtLeast(0.75f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = dim(R.dimen.vl_cv_map_radius)
        clipPath.reset()
        clipPath.addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val isCompactMonitor = h < dim(R.dimen.vl_cv_map_compact_threshold)
        val hasTopLeftBadge = tag?.toString() == "has_overlay_title"
        val padLeft = dim(R.dimen.vl_cv_map_pad_left)
        val padRight = dim(R.dimen.vl_cv_map_pad_right)
        val padTop = when {
            hasTopLeftBadge -> dim(R.dimen.vl_cv_map_pad_top_overlay)
            isCompactMonitor -> dim(R.dimen.vl_cv_map_pad_top_compact)
            else -> dim(R.dimen.vl_cv_map_pad_top_normal)
        }
        val padBottom = dim(R.dimen.vl_cv_map_pad_bottom)

        val layout = RouteGeometry.projectRouteToCanvas(
            rawPoints = polylinePoints,
            canvasWidth = w,
            canvasHeight = h,
            padLeft = padLeft,
            padTop = padTop,
            padRight = padRight,
            padBottom = padBottom,
            zoomLevel = zoomLevel,
            minScaleBarPx = dim(R.dimen.vl_cv_map_scale_min),
            maxScaleBarPx = dim(R.dimen.vl_cv_map_scale_max)
        )

        if (layout == null || layout.points.size < 2) {
            canvas.restore()
            return
        }

        drawProportionalGrid(canvas, w, h, layout)

        routePath.reset()
        layout.points.forEachIndexed { idx, pt ->
            if (idx == 0) routePath.moveTo(pt.x, pt.y) else routePath.lineTo(pt.x, pt.y)
        }

        routeShadowPaint.strokeWidth = dim(
            if (isCompactMonitor) R.dimen.vl_cv_map_shadow_compact else R.dimen.vl_cv_map_shadow_normal
        )
        routePaint.strokeWidth = dim(
            if (isCompactMonitor) R.dimen.vl_cv_map_route_compact else R.dimen.vl_cv_map_route_normal
        )
        canvas.drawPath(routePath, routeShadowPaint)
        canvas.drawPath(routePath, routePaint)

        val startPt = layout.points.first()
        val endPt = layout.points.last()

        canvas.drawCircle(startPt.x, startPt.y, dim(R.dimen.vl_cv_map_dot_origin_outer), dotOuterPaint)
        canvas.drawCircle(startPt.x, startPt.y, dim(R.dimen.vl_cv_map_dot_origin_inner), originDotPaint)

        canvas.drawCircle(endPt.x, endPt.y, dim(R.dimen.vl_cv_map_dot_dest_outer), dotOuterPaint)
        canvas.drawCircle(endPt.x, endPt.y, dim(R.dimen.vl_cv_map_dot_dest_ring), destDotRingPaint)
        canvas.drawCircle(endPt.x, endPt.y, dim(R.dimen.vl_cv_map_dot_dest_core), dotOuterPaint)

        drawSmartEndpointLabels(canvas, w, h, padTop, padRight, startPt, endPt, isCompactMonitor)
        drawNorthIndicator(canvas, w)
        drawBottomScaleAndSource(canvas, w, h, layout)

        canvas.restore()
    }

    private fun drawProportionalGrid(
        canvas: Canvas,
        w: Float,
        h: Float,
        layout: ProjectedRouteLayout
    ) {
        val minStep = dim(R.dimen.vl_cv_map_scale_min)
        val maxStep = dim(R.dimen.vl_cv_map_scale_max) * 1.5f
        val step = layout.gridSpacingPx.coerceIn(minStep, maxStep)
        var x = layout.centerX % step
        var colIndex = 0
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, if (colIndex % 2 == 0) majorGridPaint else gridPaint)
            x += step
            colIndex++
        }
        var y = layout.centerY % step
        var rowIndex = 0
        while (y < h) {
            canvas.drawLine(0f, y, w, y, if (rowIndex % 2 == 0) majorGridPaint else gridPaint)
            y += step
            rowIndex++
        }
    }

    private fun drawSmartEndpointLabels(
        canvas: Canvas,
        w: Float,
        h: Float,
        topSafeY: Float,
        rightZoomClearance: Float,
        startPt: ProjectedCanvasPoint,
        endPt: ProjectedCanvasPoint,
        isCompact: Boolean
    ) {
        labelTextPaint.textSize = dim(
            if (isCompact) R.dimen.vl_cv_map_label_text_compact else R.dimen.vl_cv_map_label_text_normal
        )
        val boxH = dim(
            if (isCompact) R.dimen.vl_cv_map_label_box_h_compact else R.dimen.vl_cv_map_label_box_h_normal
        )
        val padH = dim(R.dimen.vl_cv_map_label_pad_h)
        val unitGap = dim(R.dimen.vl_cv_map_badge_margin) * 0.75f
        val maxX = w - unitGap
        val safeRightForBottom = w - rightZoomClearance - unitGap * 0.75f
        val minY = if (isCompact) unitGap * 0.75f else topSafeY - unitGap * 0.5f
        val maxY = h - dim(R.dimen.vl_cv_map_pad_bottom) - boxH
        val maxLabelChars = if (isCompact) 9 else 12

        fun measureLabelBox(text: String): Pair<String, Float> {
            val clipped = if (text.length > maxLabelChars) text.take(maxLabelChars) + ".." else text
            val tw = labelTextPaint.measureText(clipped)
            return clipped to (tw + padH * 2f)
        }

        val bottomZoomZone = h - dim(R.dimen.vl_cv_map_pad_bottom) - boxH * 2.1f

        fun buildRect(pt: ProjectedCanvasPoint, boxW: Float, preferAbove: Boolean, preferRight: Boolean): RectF {
            val leftCandidate = if (preferRight) pt.x + unitGap else pt.x - boxW - unitGap
            val topCandidate = if (preferAbove) pt.y - boxH - unitGap else pt.y + unitGap
            val preliminaryTop = topCandidate.coerceIn(minY, maxY.coerceAtLeast(minY))
            val rightBound = if (preliminaryTop + boxH > bottomZoomZone) safeRightForBottom else maxX
            val safeMaxLeft = (rightBound - boxW).coerceAtLeast(unitGap * 0.75f)
            val clampedLeft = leftCandidate.coerceIn(unitGap * 0.75f, safeMaxLeft)
            return RectF(clampedLeft, preliminaryTop, clampedLeft + boxW, preliminaryTop + boxH)
        }

        val (origText, origW) = measureLabelBox(originLabel)
        val (destText, destW) = measureLabelBox(destLabel)

        val dx = endPt.x - startPt.x
        val dy = endPt.y - startPt.y

        val origPreferAbove = startPt.y > minY + boxH + unitGap * 1.4f && (dy >= 0f || startPt.y > h * 0.55f)
        val origPreferRight = startPt.x < w * 0.30f && dx > 0f
        val origRect = buildRect(startPt, origW, origPreferAbove, origPreferRight)

        val destPreferAbove = endPt.y > minY + boxH + unitGap * 1.4f && (dy <= 0f || endPt.y > h * 0.65f)
        val destPreferRight = endPt.x < w * 0.52f && dx >= 0f
        var destRect = buildRect(endPt, destW, destPreferAbove, destPreferRight)

        if (RectF.intersects(origRect, destRect)) {
            val flippedAbove = !destPreferAbove
            val alt = buildRect(endPt, destW, flippedAbove, !destPreferRight)
            destRect = if (!RectF.intersects(origRect, alt)) {
                alt
            } else {
                val shiftedTop = if (origRect.top > h * 0.5f) {
                    (origRect.top - boxH - unitGap * 0.75f).coerceAtLeast(minY)
                } else {
                    (origRect.bottom + unitGap * 0.75f).coerceAtMost(maxY.coerceAtLeast(minY))
                }
                RectF(destRect.left, shiftedTop, destRect.right, shiftedTop + boxH)
            }
        }

        drawPillRect(canvas, origRect, origText, padH)
        if (hypot((endPt.x - startPt.x).toDouble(), (endPt.y - startPt.y).toDouble()) >= unitGap * 0.5f ||
            abs(origRect.centerY() - destRect.centerY()) >= unitGap * 0.75f
        ) {
            drawPillRect(canvas, destRect, destText, padH)
        }
    }

    private fun drawPillRect(canvas: Canvas, rect: RectF, text: String, padH: Float) {
        val corner = dim(R.dimen.vl_cv_map_pill_corner)
        canvas.drawRoundRect(rect, corner, corner, labelPillPaint)
        canvas.drawRoundRect(rect, corner, corner, labelPillStrokePaint)
        val fm = labelTextPaint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(text, rect.left + padH, baseline, labelTextPaint)
    }

    private fun drawNorthIndicator(canvas: Canvas, w: Float) {
        val margin = dim(R.dimen.vl_cv_map_badge_margin)
        val badgeW = dim(R.dimen.vl_cv_map_compass_w)
        val badgeH = dim(R.dimen.vl_cv_map_compass_h)
        val left = w - margin - badgeW
        val top = margin * 0.8f
        val rect = RectF(left, top, left + badgeW, top + badgeH)
        canvas.drawRoundRect(rect, dim(R.dimen.vl_cv_map_pill_corner), dim(R.dimen.vl_cv_map_pill_corner), badgeBgPaint)
        val cx = rect.centerX()
        compassTriangle.reset()
        compassTriangle.moveTo(cx, top + badgeH * 0.17f)
        compassTriangle.lineTo(cx - badgeW * 0.16f, top + badgeH * 0.44f)
        compassTriangle.lineTo(cx + badgeW * 0.16f, top + badgeH * 0.44f)
        compassTriangle.close()
        canvas.drawPath(compassTriangle, compassArrowPaint)
        canvas.drawText("N", cx, top + badgeH * 0.84f, compassTextPaint)
    }

    private fun drawBottomScaleAndSource(
        canvas: Canvas,
        w: Float,
        h: Float,
        layout: ProjectedRouteLayout
    ) {
        val sourceLabel = when (geometryType) {
            "REAL_ROAD" -> "실제 도로 경로"
            "CACHED_ROAD" -> "저장된 도로 경로"
            else -> "오프라인 추정 경로"
        }
        val scaleLabel = RouteGeometry.formatScaleLabel(layout.scaleBarKm)
        val margin = dim(R.dimen.vl_cv_map_badge_margin)
        val pillPadH = dim(R.dimen.vl_cv_map_label_pad_h) * 0.85f
        val pillPadV = margin * 0.35f
        val fm = captionPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val pillH = textH + pillPadV * 2f
        val pillBottom = h - margin * 0.45f
        val pillTop = pillBottom - pillH

        val textWidth = captionPaint.measureText(sourceLabel)
        val pillRect = RectF(margin, pillTop, margin + textWidth + pillPadH * 2f, pillBottom)
        val corner = dim(R.dimen.vl_cv_map_pill_corner) * 0.85f
        canvas.drawRoundRect(pillRect, corner, corner, badgeBgPaint)

        val baseline = pillRect.centerY() - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(sourceLabel, pillRect.left + pillPadH, baseline, captionPaint)

        val barStartX = pillRect.right + margin * 0.8f
        val maxAllowedBarEnd = w - dim(R.dimen.vl_cv_map_scale_right_reserve)
        val barWidth = layout.scaleBarPx.coerceAtMost(maxAllowedBarEnd - barStartX)
        if (barWidth >= dim(R.dimen.vl_cv_map_scale_min) * 0.7f) {
            val barY = pillRect.centerY() + textH * 0.15f
            val tickH = pillPadV * 0.9f
            canvas.drawLine(barStartX, barY - tickH, barStartX, barY, scaleLinePaint)
            canvas.drawLine(barStartX, barY, barStartX + barWidth, barY, scaleLinePaint)
            canvas.drawLine(barStartX + barWidth, barY - tickH, barStartX + barWidth, barY, scaleLinePaint)
            canvas.drawText(scaleLabel, barStartX + barWidth + pillPadH * 0.7f, baseline, captionPaint)
        }
    }
}
