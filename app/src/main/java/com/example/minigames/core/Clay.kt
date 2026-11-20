package com.example.minigames.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Drawing helpers for the soft "clay" look of the game icons: every shape is a darker base with a lighter copy shifted
 * to the upper left (so a dark crescent stays on the lower right) and a small white highlight. Flat colours only, no
 * shaders, so it is cheap and looks the same everywhere. Icons are designed around (0,0)-centred 64x64 tiles.
 */
object Clay {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rf = RectF()
    private val path = Path()

    val NAVY: Int = Color.rgb(22, 32, 80)

    fun dark(c: Int) = mixC(c, Color.BLACK, 0.30f)
    fun light(c: Int) = mixC(c, Color.WHITE, 0.40f)

    /** Dark rounded tile with a soft glow of [accent] (the icon background). */
    fun tile(c: Canvas, cx: Float, cy: Float, accent: Int, half: Float = 30f) {
        rf.set(cx - half, cy - half, cx + half, cy + half)
        fill.color = NAVY
        c.drawRoundRect(rf, half * 0.46f, half * 0.46f, fill)
        c.save()
        c.clipPath(Path().also { it.addRoundRect(rf, half * 0.46f, half * 0.46f, Path.Direction.CW) })
        for (k in 0 until 4) {
            val r = half * (1.25f - 0.28f * k)
            fill.color = withA(accent, 0.15f)
            c.drawCircle(cx, cy + half * 0.05f, r, fill)
        }
        c.restore()
    }

    fun ball(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = dark(color)
        c.drawCircle(x, y, r, fill)
        fill.color = color
        c.drawCircle(x - r * 0.10f, y - r * 0.12f, r * 0.88f, fill)
        fill.color = light(color)
        c.drawCircle(x - r * 0.20f, y - r * 0.24f, r * 0.62f, fill)
        fill.color = color
        c.drawCircle(x - r * 0.12f, y - r * 0.14f, r * 0.58f, fill)
        fill.color = withA(Color.WHITE, 0.75f)
        rf.set(x - r * 0.62f, y - r * 0.66f, x - r * 0.18f, y - r * 0.40f)
        c.drawOval(rf, fill)
    }

    fun block(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        rf.set(l, t, r, b)
        fill.color = dark(color)
        c.drawRoundRect(rf, rad, rad, fill)
        rf.set(l, t, r - 2.2f, b - 2.8f)
        fill.color = color
        c.drawRoundRect(rf, rad, rad, fill)
        val w = (r - l)
        rf.set(l + w * 0.12f, t + 2.4f, l + w * 0.12f + w * 0.34f, t + 5.4f)
        fill.color = withA(Color.WHITE, 0.55f)
        c.drawRoundRect(rf, 1.5f, 1.5f, fill)
    }

    /** A thick round-capped bar. */
    fun bar(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        stroke.strokeWidth = w
        stroke.color = dark(color)
        c.drawLine(x1, y1, x2, y2, stroke)
        stroke.strokeWidth = w - 2.6f
        stroke.color = color
        c.drawLine(x1 - 0.7f, y1 - 0.9f, x2 - 0.7f, y2 - 0.9f, stroke)
    }

    fun ring(c: Canvas, x: Float, y: Float, r: Float, w: Float, color: Int) {
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = w
        stroke.color = dark(color)
        c.drawCircle(x, y, r, stroke)
        stroke.strokeWidth = w - 2.6f
        stroke.color = color
        c.drawCircle(x - 0.7f, y - 0.9f, r, stroke)
    }

    /** Filled polygon from x,y pairs with the same shading. */
    fun poly(c: Canvas, pts: FloatArray, color: Int) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        fill.color = dark(color)
        c.drawPath(path, fill)
        c.save()
        c.clipPath(path)
        c.translate(-1.6f, -1.3f)
        fill.color = color
        c.drawPath(path, fill)
        c.restore()
    }

    fun dot(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
    }
}
