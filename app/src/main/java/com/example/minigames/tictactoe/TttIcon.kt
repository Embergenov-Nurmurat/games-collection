package com.example.minigames.tictactoe

import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Clay

/** Hub icon: puffy X and O on a dark tile (about 64x64). */
object TttIcon {
    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        val x = Color.rgb(255, 96, 120)
        val o = Color.rgb(84, 204, 255)
        Clay.bar(c, cx - 22f, cy - 20f, cx - 4f, cy - 2f, 7.5f, x)
        Clay.bar(c, cx - 4f, cy - 20f, cx - 22f, cy - 2f, 7.5f, x)
        Clay.ring(c, cx + 12f, cy + 11f, 9.5f, 7.5f, o)
        Clay.bar(c, cx - 22f, cy + 14f, cx - 8f, cy + 14f, 5f, Color.rgb(255, 214, 80))
    }
}
