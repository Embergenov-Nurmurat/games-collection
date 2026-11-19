package com.example.minigames.battleship

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.minigames.core.Ui

/** Hub icon: a teal plate with a ship (hull, bridge, funnel, gun) on a wave, flat, text-free, about 64x64. */
object BattleshipIcon {
    /** Accent colour of the game (sea teal). */
    val ACCENT: Int = Color.rgb(54, 190, 206)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rf = RectF()
    private val path = Path()

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        val half = 29f
        rf.set(cx - half, cy - half, cx + half, cy + half)
        fill.color = accent
        c.drawRoundRect(rf, 10f, 10f, fill)

        // hull
        path.reset()
        path.moveTo(cx - 22f, cy + 2f)
        path.lineTo(cx + 22f, cy + 2f)
        path.lineTo(cx + 15f, cy + 12f)
        path.lineTo(cx - 16f, cy + 12f)
        path.close()
        fill.color = Ui.INK
        c.drawPath(path, fill)
        // bridge and funnel
        rf.set(cx - 9f, cy - 8f, cx + 9f, cy + 2f)
        c.drawRoundRect(rf, 2f, 2f, fill)
        rf.set(cx - 3.5f, cy - 16f, cx + 3.5f, cy - 8f)
        c.drawRoundRect(rf, 1.5f, 1.5f, fill)
        // gun
        stroke.color = Ui.INK
        stroke.strokeWidth = 3.2f
        c.drawLine(cx + 9f, cy - 3f, cx + 20f, cy - 8f, stroke)
        // waves
        stroke.color = Color.argb(235, 255, 255, 255)
        stroke.strokeWidth = 2.6f
        for (k in 0 until 3) {
            val x0 = cx - 21f + k * 14f
            path.reset()
            path.moveTo(x0, cy + 20f)
            path.quadTo(x0 + 3.5f, cy + 16.5f, x0 + 7f, cy + 20f)
            path.quadTo(x0 + 10.5f, cy + 23.5f, x0 + 14f, cy + 20f)
            c.drawPath(path, stroke)
        }
    }
}
