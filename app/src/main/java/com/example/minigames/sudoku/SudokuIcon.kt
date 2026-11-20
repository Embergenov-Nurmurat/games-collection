package com.example.minigames.sudoku

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.minigames.core.Clay

/** Hub icon: a white 3x3-box grid with a few puffy violet cells on a dark tile (text-free, about 64x64). */
object SudokuIcon {
    /** Accent colour of the game (violet). */
    val ACCENT: Int = Color.rgb(160, 124, 246)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        Clay.block(c, cx - 25f, cy - 25f, cx + 25f, cy + 25f, 8f, Color.rgb(248, 246, 255))
        line.color = Color.rgb(196, 182, 244)
        line.strokeWidth = 1.6f
        for (k in 1..2) {
            val o = -25f + k * 16.67f
            c.drawLine(cx + o, cy - 21f, cx + o, cy + 21f, line)
            c.drawLine(cx - 21f, cy + o, cx + 21f, cy + o, line)
        }
        // filled cells
        val v = Color.rgb(150, 110, 240)
        Clay.block(c, cx - 22f, cy - 22f, cx - 11f, cy - 11f, 3f, v)
        Clay.ball(c, cx + 8f, cy - 2f, 4.6f, Color.rgb(120, 214, 150))
        Clay.ball(c, cx - 9f, cy + 15f, 4.6f, Color.rgb(250, 160, 90))
        Clay.ball(c, cx + 17f, cy + 17f, 4.6f, v)
        Clay.ball(c, cx + 17f, cy - 17f, 4.6f, Color.rgb(250, 160, 90))
    }
}
