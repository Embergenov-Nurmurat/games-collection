package com.example.minigames.backgammon

import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Clay

/** Hub icon: a white die and a wooden checker on a dark tile (about 64x64). */
object BackgammonIcon {
    /** Accent colour of the game (warm wood). */
    val ACCENT: Int = Color.rgb(222, 138, 62)

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        Clay.ball(c, cx + 9f, cy + 11f, 15f, Color.rgb(226, 140, 66))
        Clay.ring(c, cx + 8f, cy + 10f, 8f, 3.6f, Color.rgb(246, 176, 98))
        Clay.block(c, cx - 23f, cy - 24f, cx + 5f, cy + 4f, 7f, Color.rgb(250, 246, 238))
        val pip = Color.rgb(40, 40, 60)
        Clay.dot(c, cx - 16.5f, cy - 17.5f, 2.7f, pip)
        Clay.dot(c, cx - 9f, cy - 10f, 2.7f, pip)
        Clay.dot(c, cx - 1.5f, cy - 2.5f, 2.7f, pip)
    }
}
