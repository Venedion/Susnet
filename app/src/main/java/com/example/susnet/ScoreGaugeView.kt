package com.example.susnet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

class ScoreGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentScore: Float = 0f
    private var targetScore: Int = 0
    private var scoreAnimator: ValueAnimator? = null

    private var palette: StatusPalette? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val valueArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val bounds = RectF()

    fun setScore(score: Int, palette: StatusPalette, animate: Boolean = true) {
        this.palette = palette
        this.targetScore = score.coerceIn(0, 100)

        scoreAnimator?.cancel()

        if (animate) {
            scoreAnimator = ValueAnimator.ofFloat(currentScore, targetScore.toFloat()).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    currentScore = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            currentScore = targetScore.toFloat()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentPalette = palette ?: StatusPalette.from(StatusLevel.fromScore(currentScore.toInt()), context)

        val density = resources.displayMetrics.density
        val strokeWidth = 14f * density
        val padding = strokeWidth / 2f + 8f * density

        val width = width.toFloat()
        val height = height.toFloat()

        bounds.set(padding, padding, width - padding, height - padding)

        trackPaint.strokeWidth = strokeWidth
        valueArcPaint.strokeWidth = strokeWidth

        val startAngle = 150f
        val sweepAngle0To35 = 35f * 2.4f
        val sweepAngle35To65 = 30f * 2.4f
        val sweepAngle65To100 = 35f * 2.4f

        trackPaint.color = currentPalette.gaugeGreen
        canvas.drawArc(bounds, startAngle, sweepAngle0To35, false, trackPaint)

        trackPaint.color = currentPalette.gaugeAmber
        canvas.drawArc(bounds, startAngle + sweepAngle0To35, sweepAngle35To65, false, trackPaint)

        trackPaint.color = currentPalette.gaugeRed
        canvas.drawArc(bounds, startAngle + sweepAngle0To35 + sweepAngle35To65, sweepAngle65To100, false, trackPaint)

        val currentSweep = currentScore * 2.4f
        if (currentSweep > 0.1f) {
            valueArcPaint.color = currentPalette.accent
            canvas.drawArc(bounds, startAngle, currentSweep, false, valueArcPaint)

            val endAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val r = bounds.width() / 2f
            val knobX = (cx + r * cos(endAngleRad)).toFloat()
            val knobY = (cy + r * sin(endAngleRad)).toFloat()

            knobPaint.color = currentPalette.accent
            canvas.drawCircle(knobX, knobY, strokeWidth * 0.65f, knobPaint)
        }

        scoreTextPaint.color = currentPalette.ink
        scoreTextPaint.textSize = 52f * density

        val textY = bounds.centerY() - (scoreTextPaint.descent() + scoreTextPaint.ascent()) / 2f
        canvas.drawText(currentScore.toInt().toString(), bounds.centerX(), textY, scoreTextPaint)
    }
}
