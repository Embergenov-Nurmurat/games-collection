package com.example.minigames.battleship

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.minigames.core.Clay

/** Hub icon: a puffy grey ship on blue water on a dark tile (about 64x64). */
object BattleshipIcon {
    /** Accent colour of the game (sea teal). */
    val ACCENT: Int = Color.rgb(54, 190, 206)

    private val crest = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        Clay.tile(c, cx, cy, accent)
        val grey = Color.rgb(168, 182, 210)
        // superstructure
        Clay.block(c, cx - 3f, cy - 20f, cx + 8f, cy - 9f, 3f, Color.rgb(226, 232, 246))
        Clay.block(c, cx - 13f, cy - 12f, cx + 9f, cy - 1f, 4f, Color.rgb(210, 220, 240))
        Clay.block(c, cx - 19f, cy - 17f, cx - 12f, cy - 6f, 3f, Color.rgb(246, 92, 88))   // funnel
        Clay.bar(c, cx + 8f, cy - 5f, cx + 22f, cy - 10f, 4f, Color.rgb(120, 134, 168))       // gun
        // hull
        Clay.poly(c, floatArrayOf(cx - 26f, cy - 2f, cx + 26f, cy - 2f, cx + 17f, cy + 13f, cx - 18f, cy + 13f), grey)
        // water in front
        Clay.block(c, cx - 27f, cy + 7f, cx + 27f, cy + 24f, 8f, Color.rgb(62, 150, 235))
        crest.color = Color.argb(220, 255, 255, 255)
        crest.strokeWidth = 2.4f
        for (k in 0 until 3) {
            val x0 = cx - 20f + k * 14f
            path.reset()
            path.moveTo(x0, cy + 16f)
            path.quadTo(x0 + 3.5f, cy + 12.5f, x0 + 7f, cy + 16f)
            path.quadTo(x0 + 10.5f, cy + 19.5f, x0 + 14f, cy + 16f)
            c.drawPath(path, crest)
        }
    }
}
