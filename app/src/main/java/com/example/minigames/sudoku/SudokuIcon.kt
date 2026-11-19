package com.example.minigames.sudoku

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.minigames.core.Ui

/** Hub icon: a 9x9 grid on a violet plate, a few cells filled (flat, text-free, about 64x64). */
object SudokuIcon {
    /** Accent colour of the game (violet). */
    val ACCENT: Int = Color.rgb(160, 124, 246)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rf = RectF()

    // cells (col, row) of the 9x9 grid that get a dot
    private val DOTS = intArrayOf(0, 1, 4, 2, 7, 0, 3, 3, 5, 4, 8, 5, 1, 6, 6, 7, 2, 8, 4, 8)

    fun draw(c: Canvas, cx: Float, cy: Float, accent: Int) {
        val half = 29f
        rf.set(cx - half, cy - half, cx + half, cy + half)
        fill.color = accent
        c.drawRoundRect(rf, 10f, 10f, fill)

        val cell = 5.6f
        val gl = cx - cell * 4.5f
        val gt = cy - cell * 4.5f
        // thin lines
        fill.color = Color.argb(70, 255, 255, 255)
        for (i in 1 until 9) {
            if (i % 3 == 0) continue
            c.drawRect(gl + i * cell - 0.4f, gt, gl + i * cell + 0.4f, gt + 9 * cell, fill)
            c.drawRect(gl, gt + i * cell - 0.4f, gl + 9 * cell, gt + i * cell + 0.4f, fill)
        }
        // box lines
        fill.color = Color.argb(235, 255, 255, 255)
        for (i in 0..3) {
            c.drawRect(gl + i * 3 * cell - 0.9f, gt, gl + i * 3 * cell + 0.9f, gt + 9 * cell, fill)
            c.drawRect(gl, gt + i * 3 * cell - 0.9f, gl + 9 * cell, gt + i * 3 * cell + 0.9f, fill)
        }
        // some filled cells
        fill.color = Ui.INK
        var k = 0
        while (k + 1 < DOTS.size) {
            val x = gl + (DOTS[k] + 0.5f) * cell
            val y = gt + (DOTS[k + 1] + 0.5f) * cell
            c.drawCircle(x, y, 1.5f, fill)
            k += 2
        }
    }
}
