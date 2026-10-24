package com.example.susnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class ScoreZoneBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var score: Int = 0
    private var palette: StatusPalette? = null

    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val rectF = RectF()

    init {
        greenPaint.color = ContextCompat.getColor(context, R.color.gauge_zone_green)
        amberPaint.color = ContextCompat.getColor(context, R.color.gauge_zone_amber)
        redPaint.color = ContextCompat.getColor(context, R.color.gauge_zone_red)
        markerBorderPaint.color = ContextCompat.getColor(context, R.color.surface_card)
    }

    fun setScore(score: Int, palette: StatusPalette) {
        this.score = score.coerceIn(0, 100)
        this.palette = palette
        markerFillPaint.color = palette.textAccent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val barHeight = 8f * density
        val markerRadius = 7f * density
        val borderWidth = 2f * density

        markerBorderPaint.strokeWidth = borderWidth

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        if (widthF <= 0f || heightF <= 0f) return

        val barTop = (heightF - barHeight) / 2f
        val barBottom = barTop + barHeight
        val radius = barHeight / 2f

        val seg1Right = widthF * 0.35f
        val seg2Right = widthF * 0.65f

        // Draw segment 1 (Green 0..35%)
        rectF.set(0f, barTop, seg1Right, barBottom)
        canvas.drawRoundRect(rectF, radius, radius, greenPaint)

        // Draw segment 2 (Amber 35..65%)
        rectF.set(seg1Right - radius, barTop, seg2Right + radius, barBottom)
        canvas.drawRect(seg1Right, barTop, seg2Right, barBottom, amberPaint)

        // Draw segment 3 (Red 65..100%)
        rectF.set(seg2Right, barTop, widthF, barBottom)
        canvas.drawRoundRect(rectF, radius, radius, redPaint)

        // Draw score marker circle (14dp diameter)
        val availableWidth = widthF - (2 * markerRadius)
        val markerX = markerRadius + (score / 100f) * availableWidth
        val markerY = heightF / 2f

        canvas.drawCircle(markerX, markerY, markerRadius, markerFillPaint)
        canvas.drawCircle(markerX, markerY, markerRadius - (borderWidth / 2f), markerBorderPaint)
    }
}
