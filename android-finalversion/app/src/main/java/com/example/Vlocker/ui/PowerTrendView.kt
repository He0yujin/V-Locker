package com.example.Vlocker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DimenRes
import com.example.Vlocker.R

class PowerTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var samples: MutableList<Float> = mutableListOf()

    private fun dim(@DimenRes resId: Int): Float = resources.getDimension(resId)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3E8E4")
        strokeWidth = dim(R.dimen.vl_cv_power_grid_stroke)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#087F70")
        style = Paint.Style.STROKE
        strokeWidth = dim(R.dimen.vl_cv_power_line_stroke)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#087F70")
        style = Paint.Style.FILL
    }
    private val latestPointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val linePath = Path()
    private val fillPath = Path()

    fun setSamples(values: List<Double>) {
        samples = values
            .filter { it.isFinite() }
            .map { it.toFloat().coerceIn(0f, 3.6f) }
            .toMutableList()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat(),
            Color.parseColor("#2A087F70"),
            Color.parseColor("#00087F70"),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padV = dim(R.dimen.vl_cv_power_pad_v)
        val padH = dim(R.dimen.vl_cv_power_pad_h)
        val plotH = (h - padV * 2).coerceAtLeast(1f)
        val plotW = (w - padH * 2).coerceAtLeast(1f)

        canvas.drawLine(0f, padV, w, padV, gridPaint)
        canvas.drawLine(0f, padV + plotH * 0.5f, w, padV + plotH * 0.5f, gridPaint)
        canvas.drawLine(0f, h - padV, w, h - padV, gridPaint)

        if (samples.isEmpty()) return

        val plotValues = if (samples.size == 1) {
            listOf(samples.first(), samples.first())
        } else {
            samples.toList()
        }

        val maxObserved = plotValues.maxOrNull() ?: 1.2f
        val maxScale = 2.5f.coerceAtLeast(maxObserved * 1.25f)
        val stepX = plotW / (plotValues.size - 1)

        linePath.reset()
        fillPath.reset()

        val points = ArrayList<Pair<Float, Float>>(plotValues.size)
        plotValues.forEachIndexed { idx, v ->
            val x = padH + idx * stepX
            val normalized = (v / maxScale).coerceIn(0f, 1f)
            val y = (h - padV) - normalized * plotH
            points.add(x to y)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h - padV)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(padH + plotW, h - padV)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        val dotOuter = dim(R.dimen.vl_cv_power_dot_outer)
        val dotInner = dim(R.dimen.vl_cv_power_dot_inner)
        val dotMinor = dim(R.dimen.vl_cv_power_dot_minor)

        if (samples.size == 1) {
            val (lx, ly) = points.last()
            canvas.drawCircle(lx, ly, dotOuter, latestPointRingPaint)
            canvas.drawCircle(lx, ly, dotInner, pointPaint)
        } else {
            points.forEachIndexed { idx, (px, py) ->
                val isLast = idx == points.lastIndex
                if (isLast) {
                    canvas.drawCircle(px, py, dotOuter, latestPointRingPaint)
                    canvas.drawCircle(px, py, dotInner, pointPaint)
                } else {
                    canvas.drawCircle(px, py, dotMinor, pointPaint)
                }
            }
        }
    }
}
