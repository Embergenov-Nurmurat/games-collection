package com.example.minigames.mines

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.minigames.core.Ui

/** Hub icon: a coral plate with a mine (a dark ball with spikes and a small highlight), flat, text-free, about 64x64. */
object MinesIcon {
    /** Accent colour of the game (coral). */
    val ACCENT: Int = Color.rgb(242, 112, 96)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rf = RectF()

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        val half = 29f
        rf.set(cx - half, cy - half, cx + half, cy + half)
        fill.color = accent
        c.drawRoundRect(rf, 10f, 10f, fill)

        // spikes
        stroke.color = Ui.INK
        stroke.strokeWidth = 3.4f
        val r1 = 9.5f
        val r2 = 18f
        for (k in 0 until 8) {
            val a = k * Math.PI.toFloat() / 4f
            val ca = Math.cos(a.toDouble()).toFloat()
            val sa = Math.sin(a.toDouble()).toFloat()
            c.drawLine(cx + ca * r1, cy + sa * r1, cx + ca * r2, cy + sa * r2, stroke)
        }
        // ball
        fill.color = Ui.INK
        c.drawCircle(cx, cy, 12.5f, fill)
        // highlight
        fill.color = Color.argb(235, 255, 255, 255)
        c.drawCircle(cx - 4.5f, cy - 4.5f, 3.2f, fill)
    }
}
