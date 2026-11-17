package com.example.minigames.g2048

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.minigames.core.Ui

/** Hub icon: 2x2 tiles with 1, 2, 3 and 4 dots on an orange plate (flat, text-free, about 64x64). */
object G2048Icon {
    /** Accent colour of the game (orange). */
    val ACCENT: Int = Color.rgb(246, 150, 58)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rf = RectF()

    private val TILES = intArrayOf(
        Color.rgb(255, 246, 228),
        Color.rgb(255, 232, 192),
        Color.rgb(255, 218, 156),
        Color.rgb(255, 204, 120)
    )

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        rf.set(cx - 29f, cy - 29f, cx + 29f, cy + 29f)
        fill.color = accent
        c.drawRoundRect(rf, 10f, 10f, fill)

        for (i in 0 until 4) {
            val tx = cx + (if (i % 2 == 0) -14f else 14f)
            val ty = cy + (if (i / 2 == 0) -14f else 14f)
            rf.set(tx - 12f, ty - 12f, tx + 12f, ty + 12f)
            fill.color = TILES[i]
            c.drawRoundRect(rf, 6f, 6f, fill)
            fill.color = Ui.INK
            when (i) {
                0 -> c.drawCircle(tx, ty, 2.8f, fill)
                1 -> {
                    c.drawCircle(tx - 4.5f, ty - 4.5f, 2.8f, fill)
                    c.drawCircle(tx + 4.5f, ty + 4.5f, 2.8f, fill)
                }
                2 -> {
                    c.drawCircle(tx - 5f, ty - 5f, 2.8f, fill)
                    c.drawCircle(tx, ty, 2.8f, fill)
                    c.drawCircle(tx + 5f, ty + 5f, 2.8f, fill)
                }
                else -> {
                    c.drawCircle(tx - 4.5f, ty - 4.5f, 2.8f, fill)
                    c.drawCircle(tx + 4.5f, ty - 4.5f, 2.8f, fill)
                    c.drawCircle(tx - 4.5f, ty + 4.5f, 2.8f, fill)
                    c.drawCircle(tx + 4.5f, ty + 4.5f, 2.8f, fill)
                }
            }
        }
    }
}
