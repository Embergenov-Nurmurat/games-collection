package com.example.minigames.backgammon

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.minigames.core.Ui
import com.example.minigames.core.withA

/** Hub icon: a die and a checker (flat, text-free, fits in about 64x64). */
object BackgammonIcon {
    /** Accent colour of the game (warm brown-orange). */
    val ACCENT: Int = Color.rgb(222, 138, 62)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val rf = RectF()

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        // die (tilted)
        c.save()
        c.translate(cx - 7f, cy - 6f)
        c.rotate(-12f)
        rf.set(-17f, -17f, 17f, 17f)
        fill.color = accent
        c.drawRoundRect(rf, 7f, 7f, fill)
        fill.color = Ui.INK
        val pr = 3.1f
        for (p in arrayOf(floatArrayOf(-8f, -8f), floatArrayOf(8f, -8f), floatArrayOf(0f, 0f),
            floatArrayOf(-8f, 8f), floatArrayOf(8f, 8f))) {
            c.drawCircle(p[0], p[1], pr, fill)
        }
        c.restore()
        // checker
        fill.color = Ui.TEXT
        c.drawCircle(cx + 14f, cy + 14f, 13f, fill)
        stroke.color = withA(Ui.INK, 0.35f)
        c.drawCircle(cx + 14f, cy + 14f, 8f, stroke)
    }
}
