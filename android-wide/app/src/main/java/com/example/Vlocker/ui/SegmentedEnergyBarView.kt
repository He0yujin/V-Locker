package com.example.Vlocker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DimenRes
import com.example.Vlocker.R

class SegmentedEnergyBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var reservePct: Float = 28f
    private var currentPct: Float = 61f

    private fun dim(@DimenRes resId: Int): Float = resources.getDimension(resId)

    private val reservePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B736C")
        style = Paint.Style.FILL
    }
    private val usablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6EDAC1")
        style = Paint.Style.FILL
    }
    private val remainingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#32423D")
        style = Paint.Style.FILL
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F6F2")
        strokeWidth = dim(R.dimen.vl_cv_bar_divider_stroke)
    }
    private val clipPath = Path()

    fun setLevels(currentSoc: Double, targetSoc: Double) {
        currentPct = currentSoc.toFloat().coerceIn(0f, 100f)
        reservePct = targetSoc.toFloat().coerceIn(0f, 100f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val barBottom = h - dim(R.dimen.vl_cv_bar_bottom_inset)
        val barTop = dim(R.dimen.vl_cv_bar_top_inset)
        val barRadius = dim(R.dimen.vl_cv_bar_radius)

        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(RectF(0f, barTop, w, barBottom), barRadius, barRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        canvas.drawRect(0f, barTop, w, barBottom, remainingPaint)

        val currentX = (currentPct / 100f) * w
        val reserveX = (reservePct / 100f) * w

        val actualProtectedEnd = minOf(reserveX, currentX)
        if (actualProtectedEnd > 0f) {
            canvas.drawRect(0f, barTop, actualProtectedEnd, barBottom, reservePaint)
        }

        if (currentX > reserveX) {
            canvas.drawRect(reserveX, barTop, currentX, barBottom, usablePaint)
        }

        canvas.restore()

        if (reserveX > 0f && reserveX < w) {
            canvas.drawLine(reserveX, barTop, reserveX, h, dividerPaint)
        }
    }
}
