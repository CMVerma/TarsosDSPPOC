package com.example.tarsosdsppoc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class PitchVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val path = Path()
    private var currentFrequency: Float? = null
    private var currentAmplitude: Float = 0f
    private val maxPoints = 100
    private val points = mutableListOf<Pair<Float, Float>>()

    fun updatePitch(frequency: Float?, amplitude: Float) {
        currentFrequency = frequency
        currentAmplitude = amplitude
        if (frequency != null) {
            points.add(Pair(frequency, amplitude))
            if (points.size > maxPoints) {
                points.removeAt(0)
            }
        } else {
            points.clear()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (points.isEmpty()) {
            // Draw a flat line when no pitch is detected
            canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, paint)
            return
        }

        path.reset()
        val width = width.toFloat()
        val height = height.toFloat()
        val xStep = width / (maxPoints - 1)

        // Find min and max frequencies for scaling
        val minFreq = points.minOf { it.first }
        val maxFreq = points.maxOf { it.first }
        val freqRange = maxFreq - minFreq

        // Find max amplitude for scaling
        val maxAmp = points.maxOf { it.second }

        // Draw the path
        points.forEachIndexed { index, (frequency, amplitude) ->
            val x = index * xStep
            val normalizedFreq = (frequency - minFreq) / freqRange
            val normalizedAmp = amplitude / maxAmp
            val y = height * (1f - normalizedAmp * 0.8f) // Leave some margin at top

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, paint)
    }
} 