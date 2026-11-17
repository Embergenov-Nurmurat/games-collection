package com.example.minigames.c4

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.Ui
import com.example.minigames.core.clampF
import com.example.minigames.core.mixC
import com.example.minigames.core.t
import com.example.minigames.core.withA
import com.example.minigames.table.TableGameView
import kotlin.math.sin

private val RED = Color.rgb(236, 84, 92)
private val YELLOW = Color.rgb(250, 202, 66)

// board geometry (logical coordinates)
private const val CS = 58f           // cell size
private const val BX = 37f           // left edge of the 7 columns
private const val BY = 222f          // top edge of the 6 rows
private const val GHOST_Y = 184f     // where the disc waits above the board
private const val R = CS * 0.40f     // disc radius

/** Connect Four: 7x6 board, against the computer (3 levels) or a friend. Red (side 0) moves first. */
class C4View(context: Context, host: Host) : TableGameView(context, host, "c4", "c4_title") {

    private val board = C4Board()

    // falling disc (the engine already holds it; it is drawn at its animated position until it lands)
    private var dropping = false
    private var dropCol = 0
    private var dropRow = 0
    private var dropSide = 0
    private var dropY = 0f
    private var dropV = 0f
    private var dropBounce = 0

    private var aimCol = -1            // column under the finger
    private var hintCol = -1
    private var lastCol = -1
    private var winCells: IntArray? = null

    // delayed end of the round so that the winning four stays visible
    private var pendingRes = 0
    private var pendingReason = ""
    private var pendingT = 0

    // scoreboard of the current session
    private var scoreRed = 0
    private var scoreYellow = 0
    private var scoreDraw = 0

    override val accent: Int get() = C4Icon.ACCENT

    override fun sideName(side: Int): String = if (side == 0) t("c4_red") else t("c4_yellow")

    // ------------------------------- helpers --------------------------------

    private fun cellX(col: Int): Float = BX + col * CS + CS / 2f
    private fun cellY(row: Int): Float = BY + (5 - row) * CS + CS / 2f
    private fun sideColor(side: Int): Int = if (side == 0) RED else YELLOW

    private fun colAt(x: Float): Int = ((x - BX) / CS).toInt().coerceIn(0, C4Board.COLS - 1)

    private fun humanCanMove(): Boolean =
        phase == Phase.PLAY && !thinking && !dropping && pendingRes == 0 && isHumanTurn(board.side)

    private fun resetRound() {
        while (board.undo() >= 0) { /* back to the empty board */ }
        dropping = false
        aimCol = -1
        hintCol = -1
        lastCol = -1
        winCells = null
        pendingRes = 0
        pendingT = 0
    }

    // ------------------------------- game flow ------------------------------

    override fun onNewGame() {
        resetRound()
        maybeAiMove()
    }

    private fun maybeAiMove() {
        if (!vsAi || phase != Phase.PLAY || pendingRes != 0 || dropping) return
        if (board.side == humanSide) return
        val snap = board.copy()
        val lvl = level
        runAi(if (lvl == 2) 450L else 350L, { C4Ai.choose(snap, lvl) }) { col ->
            if (phase == Phase.PLAY && pendingRes == 0 && !dropping && board.side != humanSide &&
                col >= 0 && board.canPlay(col)
            ) place(col)
        }
    }

    private fun place(col: Int) {
        if (!board.canPlay(col)) return
        dropSide = board.side
        dropRow = board.height[col]
        dropCol = col
        board.play(col)
        dropping = true
        dropY = BY - CS * 0.55f
        dropV = 2f
        dropBounce = 0
        lastCol = col
        hintCol = -1
        aimCol = -1
    }

    private fun onLanded() {
        dropping = false
        sfx.play(Sfx.Kind.PLACE)
        haptic()
        val w = board.winner()
        if (w >= 0) {
            winCells = board.winCells()
            pendingRes = w + 1
            pendingReason = "c4_r_four"
            pendingT = 85
        } else if (board.isFull()) {
            pendingRes = 3
            pendingReason = "r_board_full"
            pendingT = 45
        } else {
            maybeAiMove()
        }
    }

    // ------------------------------- input ----------------------------------

    private fun inPlayZone(y: Float): Boolean = y in 140f..(BY + 6 * CS + 40f)

    override fun onPointerDown(x: Float, y: Float) {
        aimCol = if (humanCanMove() && inPlayZone(y)) colAt(x) else -1
    }

    override fun onPointerMove(x: Float, y: Float) {
        if (aimCol >= 0) aimCol = colAt(x)
    }

    override fun onPointerUp(x: Float, y: Float) {
        val col = aimCol
        aimCol = -1
        if (col < 0 || !humanCanMove() || !inPlayZone(y)) return
        val target = colAt(x)
        if (board.canPlay(target)) place(target) else flashMessage(t("c4_full"))
    }

    override fun canUndo(): Boolean = phase == Phase.PLAY && !thinking && !dropping && pendingRes == 0 && undoSize(board.count) >= 0

    override fun doUndo() {
        val ns = undoSize(board.count)
        if (ns < 0) return
        while (board.count > ns) board.undo()
        lastCol = if (board.count == 0) -1 else board.lastMove()
        hintCol = -1
        winCells = null
        sfx.play(Sfx.Kind.MOVE)
    }

    override fun canHint(): Boolean = humanCanMove() && !board.isFull()

    override fun doHint() {
        val snap = board.copy()
        runAi(0L, { C4Ai.hint(snap) }) { col ->
            if (phase == Phase.PLAY && col >= 0 && board.canPlay(col)) hintCol = col
        }
    }

    // ------------------------- saving and continuing -------------------------

    override fun saveState(): String? {
        if (pendingRes != 0 || board.winner() >= 0 || board.isFull()) return null
        return board.serialize()
    }

    override fun loadState(s: String): Boolean {
        resetRound()
        if (!board.deserialize(s)) return false
        if (board.winner() >= 0 || board.isFull()) {
            resetRound()
            return false
        }
        lastCol = if (board.count == 0) -1 else board.lastMove()
        maybeAiMove()
        return true
    }

    // ------------------------------- update ---------------------------------

    override fun onUpdate() {
        if (dropping) {
            dropV += 1.25f
            dropY += dropV
            val ty = cellY(dropRow)
            if (dropY >= ty) {
                dropY = ty
                if (dropBounce == 0 && dropV > 7f) {
                    dropV = -dropV * 0.27f
                    dropBounce = 1
                } else {
                    onLanded()
                }
            }
        }
        if (pendingRes != 0 && phase == Phase.PLAY && !dropping) {
            pendingT--
            if (pendingT <= 0) {
                val res = pendingRes
                pendingRes = 0
                when (res) {
                    1 -> scoreRed++
                    2 -> scoreYellow++
                    else -> scoreDraw++
                }
                finishGame(res, pendingReason)
            }
        }
    }

    override fun statusText(): String {
        if (pendingRes != 0) return if (pendingRes == 3) t("draw_title") else t("side_wins", sideName(pendingRes - 1))
        return if (vsAi) t("your_turn_as", sideName(humanSide)) else t("turn_of", sideName(board.side))
    }

    // ------------------------------- drawing --------------------------------

    override fun drawSetupArt(c: Canvas, cx: Float, cy: Float) {
        fillRRect(c, cx - 118f, cy - 32f, cx + 118f, cy + 32f, 18f, Ui.SURFACE)
        strokeRRect(c, cx - 118f, cy - 32f, cx + 118f, cy + 32f, 18f, Ui.BORDER, 1.5f)
        for (i in 0 until 4) drawDisc(c, cx - 84f + i * 56f, cy, 20f, i % 2, 1f)
    }

    private fun drawDisc(c: Canvas, cx: Float, cy: Float, r: Float, side: Int, alpha: Float) {
        val col = sideColor(side)
        fillOval(c, cx - r, cy - r, cx + r, cy + r, withA(col, alpha))
        val ring = mixC(col, Ui.INK, 0.28f)
        val rr = r * 0.62f
        strokeOval(c, cx - rr, cy - rr, cx + rr, cy + rr, withA(ring, alpha), 3f)
    }

    override fun drawGame(c: Canvas) {
        drawScoreboard(c)

        val bw = C4Board.COLS * CS
        val bh = 6 * CS

        // column highlight under the finger / hinted column
        val hl = if (aimCol >= 0 && humanCanMove()) aimCol else -1
        if (hl >= 0) {
            fillRRect(c, BX + hl * CS + 2f, BY - 6f, BX + (hl + 1) * CS - 2f, BY + bh + 6f, 14f, withA(sideColor(board.side), 0.14f))
            drawDisc(c, cellX(hl), GHOST_Y, R, board.side, 0.8f)
        }
        if (hintCol >= 0) {
            val a = 0.5f + 0.5f * sin(tick * 0.14f)
            fillRRect(c, BX + hintCol * CS + 2f, BY - 6f, BX + (hintCol + 1) * CS - 2f, BY + bh + 6f, 14f, withA(Ui.OK, 0.10f + 0.10f * a))
            strokeOval(c, cellX(hintCol) - R, GHOST_Y - R, cellX(hintCol) + R, GHOST_Y + R, withA(Ui.OK, 0.5f + 0.5f * a), 4f)
        }

        // board panel
        fillRRect(c, BX - 10f, BY - 10f, BX + bw + 10f, BY + bh + 10f, 22f, Ui.SURFACE_HI)
        strokeRRect(c, BX - 10f, BY - 10f, BX + bw + 10f, BY + bh + 10f, 22f, Ui.BORDER, 1.5f)

        val wc = winCells
        val showWin = wc != null && pendingRes != 0 && !dropping
        for (col in 0 until C4Board.COLS) {
            for (row in 0 until 6) {
                val cx = cellX(col)
                val cy = cellY(row)
                fillOval(c, cx - R, cy - R, cx + R, cy + R, Ui.DISABLED_BG)
                strokeOval(c, cx - R, cy - R, cx + R, cy + R, withA(Ui.BORDER, 0.7f), 1.5f)
                if (dropping && col == dropCol && row == dropRow) continue
                val v = board.cell(col, row)
                if (v < 0) continue
                var alpha = 1f
                if (showWin) {
                    val code = col * 10 + row
                    var inWin = false
                    for (w in wc!!) if (w == code) inWin = true
                    if (!inWin) alpha = 0.4f
                }
                drawDisc(c, cx, cy, R, v, alpha)
                if (!dropping && col == lastCol && row == board.height[col] - 1 && !showWin) {
                    fillOval(c, cx - 4f, cy - 4f, cx + 4f, cy + 4f, withA(Ui.INK, 0.55f))
                }
            }
        }
        if (dropping) drawDisc(c, cellX(dropCol), dropY, R, dropSide, 1f)

        if (showWin) {
            val a = 0.55f + 0.45f * sin(tick * 0.2f)
            for (w in wc!!) {
                val cx = cellX(w / 10)
                val cy = cellY(w % 10)
                strokeOval(c, cx - R - 3f, cy - R - 3f, cx + R + 3f, cy + R + 3f, withA(Ui.TEXT, clampF(a, 0f, 1f)), 4f)
            }
        }
    }

    private fun drawScoreboard(c: Canvas) {
        val y0 = 606f
        val y1 = 646f
        for (i in 0..2) {
            val cx = 110f + i * 130f
            val hw = 56f
            fillRRect(c, cx - hw, y0, cx + hw, y1, 14f, Ui.SURFACE)
            strokeRRect(c, cx - hw, y0, cx + hw, y1, 14f, Ui.BORDER, 1.5f)
            when (i) {
                0 -> {
                    drawDisc(c, cx - 28f, (y0 + y1) / 2f, 11f, 0, 1f)
                    plainText(c, "$scoreRed", cx + 12f, y0 + 27f, 20f, Ui.TEXT, 0, 60f)
                }
                1 -> plainText(c, t("draws_short") + "  $scoreDraw", cx, y0 + 27f, 18f, Ui.MUTED, 0, hw * 2f - 14f)
                else -> {
                    drawDisc(c, cx - 28f, (y0 + y1) / 2f, 11f, 1, 1f)
                    plainText(c, "$scoreYellow", cx + 12f, y0 + 27f, 20f, Ui.TEXT, 0, 60f)
                }
            }
        }
    }
}
