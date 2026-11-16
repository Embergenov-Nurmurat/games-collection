package com.example.minigames.chess

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.minigames.core.withA

/**
 * Chess pieces drawn with plain paths (no fonts or images, so they look the same on every device).
 * Every piece is designed in a 100x100 box and scaled to the requested size.
 */
object PieceArt {

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3.4f
    }
    private val path = Path()
    private val rf = RectF()

    private val WHITE_FILL = Color.rgb(252, 248, 238)
    private val WHITE_LINE = Color.rgb(70, 46, 60)
    private val BLACK_FILL = Color.rgb(64, 58, 78)
    private val BLACK_LINE = Color.rgb(20, 16, 26)

    /** [type] 1..6 (pawn..king). Centre of the piece is (cx, cy), [size] is the box side. */
    fun draw(c: Canvas, type: Int, white: Boolean, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
        val fillC = withA(if (white) WHITE_FILL else BLACK_FILL, alpha)
        val lineC = withA(if (white) WHITE_LINE else BLACK_LINE, alpha)
        val detail = withA(if (white) WHITE_LINE else Color.rgb(205, 200, 218), alpha)

        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        val s = size / 100f
        c.scale(s, s)

        // soft shadow under the piece
        fillP.shader = null
        fillP.color = withA(Color.BLACK, 0.18f * alpha)
        rf.set(22f, 84f, 80f, 95f)
        c.drawOval(rf, fillP)

        when (type) {
            1 -> pawn(c, fillC, lineC)
            2 -> knight(c, fillC, lineC, detail)
            3 -> bishop(c, fillC, lineC, detail)
            4 -> rook(c, fillC, lineC, detail)
            5 -> queen(c, fillC, lineC)
            6 -> king(c, fillC, lineC)
        }
        c.restore()
    }

    private fun shape(c: Canvas, fillC: Int, lineC: Int) {
        fillP.color = fillC
        c.drawPath(path, fillP)
        lineP.color = lineC
        c.drawPath(path, lineP)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, fillC: Int, lineC: Int) {
        fillP.color = fillC
        c.drawCircle(x, y, r, fillP)
        lineP.color = lineC
        c.drawCircle(x, y, r, lineP)
    }

    private fun base(c: Canvas, fillC: Int, lineC: Int) {
        fillP.color = fillC
        rf.set(24f, 79f, 76f, 91f)
        c.drawRoundRect(rf, 5f, 5f, fillP)
        lineP.color = lineC
        c.drawRoundRect(rf, 5f, 5f, lineP)
    }

    private fun pawn(c: Canvas, f: Int, l: Int) {
        path.reset()
        path.moveTo(41f, 46f)
        path.lineTo(59f, 46f)
        path.quadTo(62f, 64f, 70f, 80f)
        path.lineTo(30f, 80f)
        path.quadTo(38f, 64f, 41f, 46f)
        path.close()
        shape(c, f, l)
        base(c, f, l)
        circle(c, 50f, 31f, 13f, f, l)
    }

    private fun rook(c: Canvas, f: Int, l: Int, d: Int) {
        path.reset()
        path.moveTo(33f, 40f)
        path.lineTo(67f, 40f)
        path.lineTo(71f, 80f)
        path.lineTo(29f, 80f)
        path.close()
        shape(c, f, l)
        path.reset()
        path.moveTo(28f, 40f)
        path.lineTo(28f, 17f)
        path.lineTo(39f, 17f)
        path.lineTo(39f, 26f)
        path.lineTo(46f, 26f)
        path.lineTo(46f, 17f)
        path.lineTo(54f, 17f)
        path.lineTo(54f, 26f)
        path.lineTo(61f, 26f)
        path.lineTo(61f, 17f)
        path.lineTo(72f, 17f)
        path.lineTo(72f, 40f)
        path.close()
        shape(c, f, l)
        base(c, f, l)
        lineP.color = d
        lineP.strokeWidth = 2.2f
        c.drawLine(36f, 54f, 64f, 54f, lineP)
        lineP.strokeWidth = 3.4f
    }

    private fun knight(c: Canvas, f: Int, l: Int, d: Int) {
        path.reset()
        path.moveTo(30f, 80f)
        path.lineTo(36f, 58f)
        path.quadTo(22f, 56f, 19f, 46f)
        path.lineTo(26f, 37f)
        path.lineTo(44f, 20f)
        path.lineTo(46f, 8f)
        path.lineTo(55f, 18f)
        path.quadTo(77f, 26f, 75f, 52f)
        path.lineTo(72f, 80f)
        path.close()
        shape(c, f, l)
        base(c, f, l)
        fillP.color = d
        c.drawCircle(47f, 33f, 3.4f, fillP)
        c.drawCircle(25f, 45f, 2f, fillP)
        lineP.color = d
        lineP.strokeWidth = 2.2f
        c.drawLine(56f, 28f, 66f, 50f, lineP)
        lineP.strokeWidth = 3.4f
    }

    private fun bishop(c: Canvas, f: Int, l: Int, d: Int) {
        path.reset()
        path.moveTo(42f, 52f)
        path.lineTo(58f, 52f)
        path.quadTo(60f, 66f, 70f, 80f)
        path.lineTo(30f, 80f)
        path.quadTo(40f, 66f, 42f, 52f)
        path.close()
        shape(c, f, l)
        base(c, f, l)
        // mitre
        path.reset()
        rf.set(36f, 20f, 64f, 56f)
        path.addOval(rf, Path.Direction.CW)
        shape(c, f, l)
        circle(c, 50f, 14f, 5.5f, f, l)
        lineP.color = d
        lineP.strokeWidth = 2.6f
        c.drawLine(46f, 38f, 56f, 28f, lineP)
        lineP.strokeWidth = 3.4f
    }

    private fun queen(c: Canvas, f: Int, l: Int) {
        path.reset()
        path.moveTo(30f, 80f)
        path.lineTo(24f, 34f)
        path.lineTo(38f, 54f)
        path.lineTo(42f, 24f)
        path.lineTo(50f, 52f)
        path.lineTo(58f, 24f)
        path.lineTo(62f, 54f)
        path.lineTo(76f, 34f)
        path.lineTo(70f, 80f)
        path.close()
        shape(c, f, l)
        base(c, f, l)
        circle(c, 24f, 30f, 5f, f, l)
        circle(c, 42f, 19f, 5f, f, l)
        circle(c, 58f, 19f, 5f, f, l)
        circle(c, 76f, 30f, 5f, f, l)
    }

    private fun king(c: Canvas, f: Int, l: Int) {
        path.reset()
        path.moveTo(32f, 80f)
        path.lineTo(35f, 48f)
        path.quadTo(50f, 36f, 65f, 48f)
        path.lineTo(68f, 80f)
        path.close()
        shape(c, f, l)
        base(c, f, l)
        // crown dome
        path.reset()
        path.moveTo(34f, 48f)
        path.quadTo(50f, 26f, 66f, 48f)
        path.close()
        shape(c, f, l)
        // cross
        path.reset()
        rf.set(46f, 8f, 54f, 36f)
        path.addRoundRect(rf, 2f, 2f, Path.Direction.CW)
        shape(c, f, l)
        path.reset()
        rf.set(38f, 15f, 62f, 23f)
        path.addRoundRect(rf, 2f, 2f, Path.Direction.CW)
        shape(c, f, l)
    }
}
