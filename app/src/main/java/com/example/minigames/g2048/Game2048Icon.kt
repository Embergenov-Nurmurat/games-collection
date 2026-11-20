package com.example.minigames.g2048

import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Clay

/** Hub icon: 2x2 puffy tiles with 1, 2, 3 and 4 dots on a dark tile (text-free, about 64x64). */
object G2048Icon {
    /** Accent colour of the game (orange). */
    val ACCENT: Int = Color.rgb(246, 150, 58)

    private val TILES = intArrayOf(
        Color.rgb(255, 236, 200),
        Color.rgb(255, 214, 140),
        Color.rgb(255, 184, 88),
        Color.rgb(246, 140, 52)
    )

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        val ink = Color.rgb(120, 72, 40)
        for (i in 0 until 4) {
            val tx = cx + (if (i % 2 == 0) -13f else 13f)
            val ty = cy + (if (i / 2 == 0) -13f else 13f)
            Clay.block(c, tx - 11.5f, ty - 11.5f, tx + 11.5f, ty + 11.5f, 6f, TILES[i])
            val o = if (i == 3) Color.WHITE else ink
            val d = 2.4f
            when (i) {
                0 -> Clay.dot(c, tx, ty + 1f, d, o)
                1 -> { Clay.dot(c, tx - 4f, ty - 3f, d, o); Clay.dot(c, tx + 4f, ty + 5f, d, o) }
                2 -> { Clay.dot(c, tx - 5f, ty - 3f, d, o); Clay.dot(c, tx, ty + 1f, d, o); Clay.dot(c, tx + 5f, ty + 5f, d, o) }
                else -> {
                    Clay.dot(c, tx - 4f, ty - 3.5f, d, o); Clay.dot(c, tx + 4f, ty - 3.5f, d, o)
                    Clay.dot(c, tx - 4f, ty + 4.5f, d, o); Clay.dot(c, tx + 4f, ty + 4.5f, d, o)
                }
            }
        }
    }
}
