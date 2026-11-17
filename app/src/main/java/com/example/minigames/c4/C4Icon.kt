package com.example.minigames.c4

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.minigames.core.Ui

/** Hub icon: a Connect Four board with a diagonal of discs (flat, text-free, about 64x64). */
object C4Icon {
    /** Accent colour of the game (warm yellow). */
    val ACCENT: Int = Color.rgb(250, 202, 66)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rf = RectF()

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        // board body
        rf.set(cx - 29f, cy - 28f, cx + 29f, cy + 28f)
        fill.color = accent
        c.drawRoundRect(rf, 9f, 9f, fill)

        // 4 x 4 holes (row 3 = bottom): a rising diagonal of four red discs wins, white discs fill below
        val red = Color.rgb(236, 84, 92)
        val white = Color.rgb(250, 250, 246)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                val x = cx - 19.5f + col * 13f
                val y = cy - 19.5f + row * 13f
                val diag = row == 3 - col
                val filler = row == 3 && col in 1..2
                fill.color = if (diag) red else if (filler) white else Ui.INK
                c.drawCircle(x, y, 4.9f, fill)
            }
        }
    }
}
