package com.example.minigames.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.W
import com.example.minigames.core.clampF
import com.example.minigames.core.t
import com.example.minigames.core.withA
import com.example.minigames.core.Ui
import com.example.minigames.table.TableGameView
import kotlin.math.min
import kotlin.math.sin

private val X_COLOR = Color.rgb(255, 112, 100)
private val O_COLOR = Color.rgb(96, 190, 255)

/** Tic-tac-toe on 3x3 (3 in a row) or 5x5 (4 in a row), against the computer or a friend. */
class TttView(context: Context, host: Host) : TableGameView(context, host, "ttt", "ttt_title") {

    private var sizeOpt = store.getI("ttt_size", 0).coerceIn(0, 1)

    private var board = TttBoard(3, 3)
    private val moves = ArrayList<Int>()
    private var turn = 1                       // 1 = X, 2 = O
    private var anim = FloatArray(9) { 1f }
    private var winLine: IntArray? = null
    private var winAnim = 0f
    private var lastCell = -1
    private var hintCell = -1

    // delayed end of the round so the winning line stays visible
    private var pendingRes = 0
    private var pendingReason = ""
    private var pendingT = 0

    // session scoreboard
    private var scoreX = 0
    private var scoreO = 0
    private var scoreDraw = 0

    // board geometry (logical coordinates)
    private val bx = 40f
    private val by = 205f
    private val bsize = 400f

    init {
        addSetupButton(
            btn(
                { t("board_x", if (sizeOpt == 0) "3x3" else "5x5 (4)") },
                W / 2f, 0f, 320f, 52f, style = { 1 }
            ) {
                sizeOpt = 1 - sizeOpt
                store.putI("ttt_size", sizeOpt)
            }
        )
    }

    override fun sideName(side: Int): String = if (side == 0) "X" else "O"

    override val accent: Int get() = Color.rgb(155, 140, 255)

    // ------------------------------- game flow ------------------------------

    override fun onNewGame() {
        val n = if (sizeOpt == 0) 3 else 5
        board = TttBoard(n, if (n == 3) 3 else 4)
        anim = FloatArray(n * n) { 1f }
        moves.clear()
        turn = 1
        winLine = null
        winAnim = 0f
        lastCell = -1
        hintCell = -1
        pendingRes = 0
        maybeAiMove()
    }

    private fun maybeAiMove() {
        if (!vsAi || phase != Phase.PLAY || pendingRes != 0) return
        if (turn == humanSide + 1) return
        val snap = board.copy()
        val me = turn
        val lvl = level
        runAi(550L, { TttAI.choose(snap, me, lvl) }) { cell ->
            if (phase == Phase.PLAY && pendingRes == 0 && cell >= 0 && board.cells[cell] == 0) place(cell)
        }
    }

    private fun place(cell: Int) {
        board.cells[cell] = turn
        moves.add(cell)
        anim[cell] = 0f
        lastCell = cell
        hintCell = -1
        sfx.play(Sfx.Kind.PLACE)
        haptic()
        val line = board.winLine()
        if (line != null) {
            winLine = line
            winAnim = 0f
            pendingRes = turn
            pendingReason = ""
            pendingT = 70
        } else if (board.isFull()) {
            pendingRes = 3
            pendingReason = "r_board_full"
            pendingT = 40
        } else {
            turn = 3 - turn
            maybeAiMove()
        }
    }

    private fun humanCanMove(): Boolean =
        phase == Phase.PLAY && !thinking && pendingRes == 0 && isHumanTurn(turn - 1)

    // ------------------------- saving and continuing -------------------------

    /** "T1;size;m1,m2,..." - the moves in order; X always moves first, so the turn follows from the count. */
    override fun saveState(): String? {
        if (pendingRes != 0 || moves.isEmpty()) return null
        return "T1;${board.n};" + moves.joinToString(",")
    }

    override fun loadState(s: String): Boolean {
        val parts = s.trim().split(';')
        if (parts.size != 3 || parts[0] != "T1") return false
        val n = parts[1].toIntOrNull() ?: return false
        if (n != 3 && n != 5) return false
        val list = ArrayList<Int>()
        for (tok in parts[2].split(',')) {
            list.add(tok.toIntOrNull() ?: return false)
        }
        if (list.isEmpty() || list.size >= n * n) return false
        val b = TttBoard(n, if (n == 3) 3 else 4)
        var who = 1
        for ((i, cell) in list.withIndex()) {
            if (cell < 0 || cell >= n * n || b.cells[cell] != 0) return false
            b.cells[cell] = who
            if (b.winLine() != null) return false          // a finished game is never saved
            who = 3 - who
            if (i == list.size - 1 && b.isFull()) return false
        }
        board = b
        moves.clear()
        moves.addAll(list)
        turn = who
        anim = FloatArray(n * n) { 1f }
        winLine = null
        winAnim = 0f
        lastCell = list[list.size - 1]
        hintCell = -1
        pendingRes = 0
        maybeAiMove()
        return true
    }

    /** A decided game waits a moment before the result panel; leaving or pausing must not lose that result. */
    private fun flushPending() {
        if (pendingRes == 0 || phase != Phase.PLAY) return
        val res = pendingRes
        pendingRes = 0
        when (res) {
            1 -> scoreX++
            2 -> scoreO++
            else -> scoreDraw++
        }
        finishGame(res, pendingReason)
    }

    override fun onBack(): Boolean {
        if (!fading) flushPending()
        return super.onBack()
    }

    override fun onHostPause() {
        flushPending()
        super.onHostPause()
    }

    // ------------------------------- input ----------------------------------

    override fun onPointerDown(x: Float, y: Float) {
        if (!humanCanMove()) return
        val n = board.n
        val cs = bsize / n
        if (x < bx || y < by || x >= bx + bsize || y >= by + bsize) return
        val col = ((x - bx) / cs).toInt().coerceIn(0, n - 1)
        val row = ((y - by) / cs).toInt().coerceIn(0, n - 1)
        val cell = row * n + col
        if (board.cells[cell] == 0) place(cell)
    }

    override fun canUndo(): Boolean = phase == Phase.PLAY && !thinking && pendingRes == 0 && undoSize(moves.size) >= 0

    override fun doUndo() {
        val ns = undoSize(moves.size)
        if (ns < 0) return
        while (moves.size > ns) {
            val cell = moves.removeAt(moves.size - 1)
            board.cells[cell] = 0
            anim[cell] = 1f
        }
        turn = if (moves.size % 2 == 0) 1 else 2
        lastCell = if (moves.isEmpty()) -1 else moves[moves.size - 1]
        hintCell = -1
        winLine = null
        sfx.play(Sfx.Kind.MOVE)
    }

    override fun canHint(): Boolean = humanCanMove() && board.empties() > 0

    override fun doHint() {
        val snap = board.copy()
        val me = turn
        runAi(0L, { TttAI.choose(snap, me, 2) }) { cell ->
            if (phase == Phase.PLAY && cell >= 0 && board.cells[cell] == 0) hintCell = cell
        }
    }

    // ------------------------------- update ---------------------------------

    override fun onUpdate() {
        for (i in anim.indices) if (anim[i] < 1f) anim[i] = min(1f, anim[i] + 0.085f)
        if (winLine != null && winAnim < 1f) winAnim = min(1f, winAnim + 0.045f)
        if (pendingRes != 0 && phase == Phase.PLAY) {
            pendingT--
            if (pendingT <= 0) flushPending()
        }
    }

    override fun statusText(): String {
        if (pendingRes != 0) return if (pendingRes == 3) t("draw_title") else t("side_wins", sideName(pendingRes - 1))
        return if (vsAi) t("your_turn_as", sideName(humanSide)) else t("turn_of", sideName(turn - 1))
    }

    // ------------------------------- drawing --------------------------------

    override fun drawSetupArt(c: Canvas, cx: Float, cy: Float) {
        drawX(c, cx - 62f, cy, 30f, 1f)
        drawO(c, cx + 62f, cy, 31f, 1f)
    }

    private fun drawX(c: Canvas, cx: Float, cy: Float, half: Float, p: Float) {
        line.color = X_COLOR
        line.strokeWidth = half * 0.42f
        val p1 = clampF(p * 2f, 0f, 1f)
        val p2 = clampF(p * 2f - 1f, 0f, 1f)
        if (p1 > 0f) c.drawLine(cx - half, cy - half, cx - half + 2f * half * p1, cy - half + 2f * half * p1, line)
        if (p2 > 0f) c.drawLine(cx + half, cy - half, cx + half - 2f * half * p2, cy - half + 2f * half * p2, line)
    }

    private fun drawO(c: Canvas, cx: Float, cy: Float, r: Float, p: Float) {
        line.color = O_COLOR
        line.strokeWidth = r * 0.42f
        rf.set(cx - r, cy - r, cx + r, cy + r)
        if (p >= 1f) c.drawOval(rf, line) else if (p > 0f) c.drawArc(rf, -90f, 360f * p, false, line)
    }

    override fun drawGame(c: Canvas) {
        drawScoreboard(c)

        val n = board.n
        val cs = bsize / n

        // board panel
        fillRRect(c, bx - 10f, by - 10f, bx + bsize + 10f, by + bsize + 10f, 24f, Ui.SURFACE)
        strokeRRect(c, bx - 10f, by - 10f, bx + bsize + 10f, by + bsize + 10f, 24f, Ui.BORDER, 1.5f)

        // last move / hint highlights
        if (lastCell >= 0) {
            val r = lastCell / n
            val col = lastCell % n
            fillRRect(c, bx + col * cs + 4f, by + r * cs + 4f, bx + (col + 1) * cs - 4f, by + (r + 1) * cs - 4f, 12f, Color.argb(34, 255, 255, 255))
        }
        if (hintCell >= 0) {
            val r = hintCell / n
            val col = hintCell % n
            val a = 0.35f + 0.3f * (0.5f + 0.5f * sin(tick * 0.15f))
            fillRRect(c, bx + col * cs + 4f, by + r * cs + 4f, bx + (col + 1) * cs - 4f, by + (r + 1) * cs - 4f, 12f, withA(Ui.OK, a * 0.6f))
        }

        // grid
        line.color = Ui.GRID
        line.strokeWidth = 3f
        for (i in 1 until n) {
            val o = i * cs
            c.drawLine(bx + o, by + 10f, bx + o, by + bsize - 10f, line)
            c.drawLine(bx + 10f, by + o, bx + bsize - 10f, by + o, line)
        }

        // marks
        val half = cs * 0.27f
        for (i in board.cells.indices) {
            val v = board.cells[i]
            if (v == 0) continue
            val cx = bx + (i % n + 0.5f) * cs
            val cy = by + (i / n + 0.5f) * cs
            if (v == 1) drawX(c, cx, cy, half, anim[i]) else drawO(c, cx, cy, half * 1.12f, anim[i])
        }

        // winning line
        val wl = winLine
        if (wl != null) {
            val a = wl[0]
            val b = wl[wl.size - 1]
            val ax = bx + (a % n + 0.5f) * cs
            val ay = by + (a / n + 0.5f) * cs
            val ex = bx + (b % n + 0.5f) * cs
            val ey = by + (b / n + 0.5f) * cs
            line.color = withA(Ui.ACCENT, 0.28f)
            line.strokeWidth = 24f
            c.drawLine(ax, ay, ax + (ex - ax) * winAnim, ay + (ey - ay) * winAnim, line)
            line.color = Ui.TEXT
            line.strokeWidth = 4f
            c.drawLine(ax, ay, ax + (ex - ax) * winAnim, ay + (ey - ay) * winAnim, line)
        }
    }

    private fun drawScoreboard(c: Canvas) {
        val y0 = 146f
        val y1 = 186f
        val items = arrayOf(
            Triple(110f, "X  $scoreX", X_COLOR),
            Triple(240f, t("draws_short") + "  $scoreDraw", Ui.MUTED),
            Triple(370f, "O  $scoreO", O_COLOR)
        )
        for ((cx, label, col) in items) {
            val hw = 58f
            fillRRect(c, cx - hw, y0, cx + hw, y1, 14f, Ui.SURFACE)
            strokeRRect(c, cx - hw, y0, cx + hw, y1, 14f, Ui.BORDER, 1.5f)
            plainText(c, label, cx, y0 + 27f, 20f, col, 0, hw * 2f - 14f)
        }
    }
}
