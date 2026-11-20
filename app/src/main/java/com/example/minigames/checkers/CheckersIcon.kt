package com.example.minigames.checkers

import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Clay

/** Hub icon: a red and a cream puffy checker, the cream one crowned, on a dark tile (about 64x64). */
object CheckersIcon {
    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        val red = Color.rgb(238, 78, 86)
        val cream = Color.rgb(250, 238, 214)
        Clay.ball(c, cx - 8f, cy + 8f, 15f, red)
        Clay.ring(c, cx - 8.5f, cy + 7.5f, 8.5f, 3.4f, Color.rgb(250, 130, 130))
        Clay.ball(c, cx + 9f, cy - 8f, 14f, cream)
        Clay.ring(c, cx + 8.5f, cy - 8.5f, 8f, 3.4f, Color.rgb(226, 208, 176))
        // crown
        Clay.poly(c, floatArrayOf(cx + 3f, cy - 4f, cx + 3.5f, cy - 13f, cx + 6.5f, cy - 9f, cx + 9f, cy - 15f, cx + 11.5f, cy - 9f, cx + 14.5f, cy - 13f, cx + 15f, cy - 4f), Color.rgb(255, 196, 40))
    }
}
