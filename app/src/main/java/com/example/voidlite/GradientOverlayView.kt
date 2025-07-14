package com.example.voidlite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class GradientOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var topGradientAlpha = 0f
    private var bottomGradientAlpha = 1f
    private val gradientHeight = 40f // Height of gradient in dp

    private val topGradient: LinearGradient by lazy {
        val height = (gradientHeight * context.resources.displayMetrics.density).toInt()
        LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            ContextCompat.getColor(context, R.color.backgroundColor),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }

    private val bottomGradient: LinearGradient by lazy {
        val height = (gradientHeight * context.resources.displayMetrics.density).toInt()
        LinearGradient(
            0f, this.height - height.toFloat(), 0f, this.height.toFloat(),
            Color.TRANSPARENT,
            ContextCompat.getColor(context, R.color.backgroundColor),
            Shader.TileMode.CLAMP
        )
    }

    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = topGradient
    }

    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = bottomGradient
    }

    fun updateGradients(topAlpha: Float, bottomAlpha: Float) {
        if (topGradientAlpha != topAlpha || bottomGradientAlpha != bottomAlpha) {
            topGradientAlpha = topAlpha
            bottomGradientAlpha = bottomAlpha
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Recreate gradients with new dimensions
        val gradientHeightPx = (gradientHeight * context.resources.displayMetrics.density).toInt()

        topPaint.shader = LinearGradient(
            0f, 0f, 0f, gradientHeightPx.toFloat(),
            ContextCompat.getColor(context, R.color.backgroundColor),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )

        bottomPaint.shader = LinearGradient(
            0f, h - gradientHeightPx.toFloat(), 0f, h.toFloat(),
            Color.TRANSPARENT,
            ContextCompat.getColor(context, R.color.backgroundColor),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val gradientHeightPx = (gradientHeight * context.resources.displayMetrics.density).toInt()

        // Draw top gradient
        if (topGradientAlpha > 0f) {
            topPaint.alpha = (topGradientAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), gradientHeightPx.toFloat(), topPaint)
        }

        // Draw bottom gradient
        if (bottomGradientAlpha > 0f) {
            bottomPaint.alpha = (bottomGradientAlpha * 255).toInt()
            canvas.drawRect(
                0f,
                height - gradientHeightPx.toFloat(),
                width.toFloat(),
                height.toFloat(),
                bottomPaint
            )
        }
    }
}