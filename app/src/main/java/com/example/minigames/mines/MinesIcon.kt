package com.example.minigames.mines

import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Clay

/** Hub icon: a spiky dark mine with a lit fuse on a dark tile (about 64x64). */
object MinesIcon {
    /** Accent colour of the game (coral). */
    val ACCENT: Int = Color.rgb(242, 112, 96)

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        val x = cx - 1f
        val y = cy + 3f
        val spike = Color.rgb(96, 104, 138)
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0)
            val dx = Math.cos(a).toFloat()
            val dy = Math.sin(a).toFloat()
            Clay.bar(c, x + dx * 11f, y + dy * 11f, x + dx * 19f, y + dy * 19f, 5.4f, spike)
        }
        Clay.ball(c, x, y, 14.5f, Color.rgb(74, 84, 124))
        // fuse and spark
        Clay.bar(c, x + 8f, y - 12f, x + 14f, y - 20f, 3.6f, Color.rgb(230, 196, 140))
        Clay.dot(c, x + 16f, y - 22f, 6f, withRed())
        Clay.ball(c, x + 16f, y - 22f, 3.6f, Color.rgb(255, 214, 70))
    }

    private fun withRed() = Color.argb(150, 255, 90, 70)
}
