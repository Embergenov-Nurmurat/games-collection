package com.example.minigames.chess

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.minigames.core.mixC
import com.example.minigames.core.withA

/**
 * Chess pieces drawn with plain paths (no fonts or images, so they look the same on every device).
 * Soft "toy" style: a fill, a darker crescent of shade on the lower right (the same path shifted and clipped),
 * a light highlight on the upper left and a rounded outline. Every piece is designed in a 100x100 box.
 */
object PieceArt {

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3.2f
    }
    private val path = Path()
    private val tmp = Path()
    private val rf = RectF()

    private class Pal(val fill: Int, val shade: Int, val light: Int, val line: Int, val detail: Int)

    private val WHITE = Pal(
        Color.rgb(250, 247, 240), Color.rgb(198, 190, 184), Color.WHITE, Color.rgb(78, 66, 82), Color.rgb(120, 106, 122)
    )
    private val BLACK = Pal(
        Color.rgb(84, 80, 98), Color.rgb(40, 36, 50), Color.rgb(150, 146, 168), Color.rgb(22, 18, 30), Color.rgb(188, 184, 206)
    )

    private fun fade(c: Int, a: Float) = withA(c, a)

    /** [type] 1..6 (pawn..king). Centre of the piece is (cx, cy), [size] is the box side. */
    fun draw(c: Canvas, type: Int, white: Boolean, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
        val base = if (white) WHITE else BLACK
        val p = if (alpha >= 0.999f) base else Pal(
            fade(base.fill, alpha), fade(base.shade, alpha), fade(base.light, alpha), fade(base.line, alpha), fade(base.detail, alpha)
        )
        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        val s = size / 100f
        c.scale(s, s)

        // soft shadow under the piece
        fillP.color = withA(Color.BLACK, 0.20f * alpha)
        rf.set(20f, 84f, 82f, 96f)
        c.drawOval(rf, fillP)

        when (type) {
            1 -> pawn(c, p)
            2 -> knight(c, p)
            3 -> bishop(c, p)
            4 -> rook(c, p)
            5 -> queen(c, p)
            6 -> king(c, p)
        }
        c.restore()
    }

    /** Fills [path] with shading + outline. [lightX]/[lightY]: highlight spot. */
    private fun shape(c: Canvas, p: Pal, hl: Boolean = true) {
        fillP.color = p.shade
        c.drawPath(path, fillP)
        c.save()
        c.clipPath(path)
        c.translate(-4.5f, -3f)
        fillP.color = p.fill
        c.drawPath(path, fillP)
        c.restore()
        if (hl) {
            // thin highlight just inside the upper left edge
            c.save()
            c.clipPath(path)
            c.translate(2.2f, 2.2f)
            lineP.color = fade(p.light, 0.9f)
            lineP.strokeWidth = 2.2f
            c.drawPath(path, lineP)
            c.restore()
        }
        lineP.color = p.line
        lineP.strokeWidth = 3.2f
        c.drawPath(path, lineP)
    }

    private fun circleShape(c: Canvas, x: Float, y: Float, r: Float, p: Pal) {
        path.reset()
        rf.set(x - r, y - r, x + r, y + r)
        path.addOval(rf, Path.Direction.CW)
        shape(c, p)
    }

    private fun rrShape(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, p: Pal) {
        path.reset()
        rf.set(l, t, r, b)
        path.addRoundRect(rf, rad, rad, Path.Direction.CW)
        shape(c, p)
    }

    /** Two-step plinth shared by all pieces. */
    private fun base(c: Canvas, p: Pal) {
        rrShape(c, 27f, 73f, 73f, 82f, 4f, p)
        rrShape(c, 22f, 81f, 78f, 91f, 5f, p)
    }

    private fun pawn(c: Canvas, p: Pal) {
        path.reset()
        path.moveTo(43f, 46f)
        path.lineTo(57f, 46f)
        path.cubicTo(58f, 58f, 66f, 66f, 69f, 76f)
        path.lineTo(31f, 76f)
        path.cubicTo(34f, 66f, 42f, 58f, 43f, 46f)
        path.close()
        shape(c, p)
        base(c, p)
        rrShape(c, 36f, 42f, 64f, 50f, 4f, p)
        circleShape(c, 50f, 30f, 13f, p)
    }

    private fun rook(c: Canvas, p: Pal) {
        path.reset()
        path.moveTo(34f, 38f)
        path.lineTo(66f, 38f)
        path.cubicTo(66f, 52f, 70f, 62f, 71f, 76f)
        path.lineTo(29f, 76f)
        path.cubicTo(30f, 62f, 34f, 52f, 34f, 38f)
        path.close()
        shape(c, p)
        base(c, p)
        // battlements
        path.reset()
        path.moveTo(27f, 40f)
        path.lineTo(27f, 16f)
        path.lineTo(38f, 16f)
        path.lineTo(38f, 24f)
        path.lineTo(45f, 24f)
        path.lineTo(45f, 16f)
        path.lineTo(55f, 16f)
        path.lineTo(55f, 24f)
        path.lineTo(62f, 24f)
        path.lineTo(62f, 16f)
        path.lineTo(73f, 16f)
        path.lineTo(73f, 40f)
        path.close()
        shape(c, p)
        lineP.color = fade(p.detail, 0.8f)
        lineP.strokeWidth = 2f
        c.drawLine(31f, 33f, 69f, 33f, lineP)
        c.drawLine(38f, 52f, 62f, 52f, lineP)
    }

    private fun knight(c: Canvas, p: Pal) {
        path.reset()
        path.moveTo(29f, 76f)
        path.cubicTo(28f, 62f, 38f, 56f, 43f, 48f)
        path.cubicTo(37f, 50f, 31f, 52f, 26f, 49f)
        path.cubicTo(20f, 46f, 19f, 41f, 23f, 37f)
        path.cubicTo(28f, 32f, 34f, 27f, 36f, 19f)
        path.lineTo(40f, 9f)
        path.lineTo(46f, 16f)
        path.cubicTo(49f, 14f, 52f, 13f, 55f, 14f)
        path.cubicTo(72f, 19f, 80f, 40f, 76f, 58f)
        path.cubicTo(74f, 64f, 73f, 70f, 72f, 76f)
        path.close()
        shape(c, p)
        base(c, p)
        // eye, nostril, mane strokes
        fillP.color = p.line
        c.drawCircle(44f, 28f, 3.2f, fillP)
        c.drawCircle(23f, 42f, 1.8f, fillP)
        lineP.color = fade(p.detail, 0.85f)
        lineP.strokeWidth = 2.4f
        path.reset()
        path.moveTo(55f, 24f)
        path.cubicTo(64f, 30f, 68f, 42f, 66f, 56f)
        c.drawPath(path, lineP)
        path.reset()
        path.moveTo(51f, 36f)
        path.cubicTo(56f, 42f, 58f, 50f, 56f, 58f)
        c.drawPath(path, lineP)
    }

    private fun bishop(c: Canvas, p: Pal) {
        path.reset()
        path.moveTo(42f, 56f)
        path.lineTo(58f, 56f)
        path.cubicTo(58f, 64f, 65f, 70f, 69f, 76f)
        path.lineTo(31f, 76f)
        path.cubicTo(35f, 70f, 42f, 64f, 42f, 56f)
        path.close()
        shape(c, p)
        base(c, p)
        rrShape(c, 36f, 52f, 64f, 60f, 4f, p)
        // mitre
        path.reset()
        path.moveTo(50f, 16f)
        path.cubicTo(60f, 24f, 68f, 34f, 64f, 46f)
        path.cubicTo(62f, 52f, 56f, 54f, 50f, 54f)
        path.cubicTo(44f, 54f, 38f, 52f, 36f, 46f)
        path.cubicTo(32f, 34f, 40f, 24f, 50f, 16f)
        path.close()
        shape(c, p)
        circleShape(c, 50f, 12f, 5.2f, p)
        lineP.color = p.detail
        lineP.strokeWidth = 3f
        c.drawLine(46f, 40f, 57f, 28f, lineP)
    }

    private fun queen(c: Canvas, p: Pal) {
        path.reset()
        path.moveTo(31f, 76f)
        path.lineTo(26f, 32f)
        path.lineTo(38f, 50f)
        path.lineTo(42f, 24f)
        path.lineTo(50f, 48f)
        path.lineTo(58f, 24f)
        path.lineTo(62f, 50f)
        path.lineTo(74f, 32f)
        path.lineTo(69f, 76f)
        path.close()
        shape(c, p)
        base(c, p)
        rrShape(c, 34f, 62f, 66f, 71f, 4f, p)
        circleShape(c, 26f, 28f, 5f, p)
        circleShape(c, 42f, 19f, 5f, p)
        circleShape(c, 58f, 19f, 5f, p)
        circleShape(c, 74f, 28f, 5f, p)
    }

    private fun king(c: Canvas, p: Pal) {
        path.reset()
        path.moveTo(32f, 76f)
        path.cubicTo(32f, 62f, 32f, 54f, 34f, 46f)
        path.cubicTo(42f, 38f, 58f, 38f, 66f, 46f)
        path.cubicTo(68f, 54f, 68f, 62f, 68f, 76f)
        path.close()
        shape(c, p)
        base(c, p)
        rrShape(c, 33f, 62f, 67f, 71f, 4f, p)
        // crown
        path.reset()
        path.moveTo(31f, 46f)
        path.cubicTo(30f, 34f, 40f, 28f, 50f, 28f)
        path.cubicTo(60f, 28f, 70f, 34f, 69f, 46f)
        path.close()
        shape(c, p)
        // cross
        path.reset()
        path.moveTo(46f, 28f); path.lineTo(46f, 21f); path.lineTo(40f, 21f); path.lineTo(40f, 14f)
        path.lineTo(46f, 14f); path.lineTo(46f, 8f); path.lineTo(54f, 8f); path.lineTo(54f, 14f)
        path.lineTo(60f, 14f); path.lineTo(60f, 21f); path.lineTo(54f, 21f); path.lineTo(54f, 28f)
        path.close()
        shape(c, p)
    }
}
