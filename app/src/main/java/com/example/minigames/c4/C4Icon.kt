package com.example.minigames.c4

import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Clay

/** Hub icon: a blue Connect Four board with puffy red and yellow discs on a dark tile (about 64x64). */
object C4Icon {
    /** Accent colour of the game (warm yellow). */
    val ACCENT: Int = Color.rgb(250, 202, 66)

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        Clay.block(c, cx - 25f, cy - 22f, cx + 25f, cy + 23f, 8f, Color.rgb(70, 120, 238))
        val red = Color.rgb(240, 80, 96)
        val yel = Color.rgb(252, 206, 60)
        for (col in 0 until 4) for (row in 0 until 3) {
            val x = cx - 16.5f + col * 11f
            val y = cy - 11.5f + row * 11.5f
            Clay.dot(c, x, y, 4.6f, Color.rgb(14, 24, 80))
            val d = when {
                row == 2 -> if (col % 2 == 0) red else yel
                row == 1 && col == 1 -> red
                row == 1 && col == 2 -> yel
                else -> 0
            }
            if (d != 0) Clay.ball(c, x, y, 4.6f, d)
        }
    }
}
