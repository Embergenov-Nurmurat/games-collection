package com.example.minigames.chess

import android.graphics.Canvas
import com.example.minigames.core.Clay

/** Hub icon: the white knight on a dark tile (about 64x64). */
object ChessIcon {
    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        PieceArt.draw(c, 2, true, cx, cy + 1f, 56f)
    }
}
