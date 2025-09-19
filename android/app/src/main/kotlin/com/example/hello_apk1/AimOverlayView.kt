package com.example.hello_apk1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class AimOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class AimState { IDLE, TARGET, STABLE }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 4f
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 2f
    }

    private var overlayScale = 0.78f
    private var currentState: AimState = AimState.IDLE

    fun setOverlayScale(scale: Float) {
        overlayScale = scale.coerceIn(0.4f, 0.95f)
        invalidate()
    }

    fun setState(state: AimState) {
        if (currentState != state) {
            currentState = state
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height) * overlayScale
        val radius = diameter / 2f
        val cx = width / 2f
        val cy = height / 2f

        val color = when (currentState) {
            AimState.IDLE -> Color.argb(200, 255, 255, 255)
            AimState.TARGET -> Color.parseColor("#FFC107")
            AimState.STABLE -> Color.parseColor("#4CAF50")
        }
        ringPaint.color = color
        guidePaint.color = color

        canvas.drawCircle(cx, cy, radius, ringPaint)

        val guideLength = radius * 0.35f
        canvas.drawLine(cx - guideLength, cy, cx + guideLength, cy, guidePaint)
        canvas.drawLine(cx, cy - guideLength, cx, cy + guideLength, guidePaint)
    }
}
