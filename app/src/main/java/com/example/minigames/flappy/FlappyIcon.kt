package com.example.minigames.flappy

import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Clay

/** Hub icon: the puffy yellow bird on a dark tile (about 64x64). */
object FlappyIcon {
    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        val orange = Color.rgb(248, 128, 48)
        Clay.poly(c, floatArrayOf(cx - 25f, cy - 8f, cx - 12f, cy - 2f, cx - 14f, cy + 10f, cx - 26f, cy + 8f, cx - 21f, cy), orange)
        Clay.ball(c, cx - 2f, cy + 1f, 17f, Color.rgb(252, 200, 60))
        Clay.ball(c, cx - 8f, cy + 5f, 8f, Color.rgb(255, 246, 214))
        Clay.poly(c, floatArrayOf(cx + 12f, cy + 3f, cx + 28f, cy + 6f, cx + 12f, cy + 10f), Color.rgb(255, 120, 64))
        Clay.poly(c, floatArrayOf(cx + 12f, cy + 10f, cx + 25f, cy + 11f, cx + 12f, cy + 15f), Color.rgb(232, 70, 48))
        Clay.ball(c, cx + 6f, cy - 6f, 6.8f, Color.WHITE)
        Clay.dot(c, cx + 8f, cy - 6f, 3.2f, Color.rgb(44, 32, 52))
    }
}
