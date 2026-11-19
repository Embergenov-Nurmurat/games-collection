package com.example.minigames.mines

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Achievements
import com.example.minigames.core.BaseView
import com.example.minigames.core.Button
import com.example.minigames.core.GameId
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.Ui
import com.example.minigames.core.W
import com.example.minigames.core.mixC
import com.example.minigames.core.t
import com.example.minigames.core.withA
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private enum class Phase { SETUP, PLAY }

/** Frames a finger must stay on a cell to count as a long press. */
private const val LONG_PRESS = 22

private const val BOARD_MAX_W = 432f
private const val BOARD_MAX_H = 504f
private const val BOARD_TOP = 124f

/**
 * Minesweeper: three levels, the first tap is always safe, tap = dig, long press = flag (or the DIG / FLAG switch),
 * tapping an open number with enough flags around it opens the rest (chord). The running game is saved after every
 * action (key mn_save). Records: mn_best_0..2 (seconds), mn_lvl (chosen level); statistics stat_mines_0 / _1
 * (cleared / exploded).
 */
class MinesView(context: Context, host: Host) : BaseView(context, host) {

    override val accent: Int get() = MinesIcon.ACCENT

    private var phase = Phase.SETUP
    private var game: MinesGame? = null
    private var saved: MinesGame? = null
    private var hasSave = false
    private var level = store.getI("mn_lvl", 0).coerceIn(0, 2)

    private var flagMode = false

    // board geometry of the current game
    private var cell = 48f
    private var bl = 24f
    private var bt = BOARD_TOP

    // finger on the board
    private var pressCell = -1
    private var pressFrames = 0
    private var longDone = false

    // animation state per cell
    private var delay = IntArray(0)       // frames until an opened cell is drawn as opened (flood ripple)
    private var pop = IntArray(0)         // short "pop" after it appeared
    private var overT = 0                 // frames since the game ended
    private var winRecord = false
    private var winSecs = 0

    // ------------------------------- buttons --------------------------------

    private val backBtn: Button = btn({ "<" }, 50f, 52f, 64f, 46f, style = { 1 }) { onBack() }

    private val levelBtns: List<Button> = List(3) { i ->
        btn(
            { t(LEVEL_KEYS[i]) }, 90f + i * 150f, 372f, 136f, 52f,
            style = { if (level == i) 2 else 1 }
        ) { setLevel(i) }
    }

    private val playBtn: Button = btn({ t("play") }, W / 2f, 548f, 240f, 56f, visible = { !hasSave }) { startNew() }
    private val resumeBtn: Button = btn({ t("resume") }, W / 2f, 548f, 240f, 56f, visible = { hasSave }) { resumeSaved() }
    private val newBtn: Button = btn({ t("new_game") }, W / 2f, 612f, 240f, 48f, style = { 1 }, visible = { hasSave }) {
        startNew()
    }

    private val digBtn: Button = btn({ t("mn_dig") }, 100f, 672f, 150f, 48f, style = { if (!flagMode) 2 else 1 }) {
        flagMode = false
    }
    private val flagBtn: Button = btn({ t("mn_flag") }, 260f, 672f, 150f, 48f, style = { if (flagMode) 2 else 1 }) {
        flagMode = true
    }
    private val restartBtn: Button = btn({ t("new_game") }, 410f, 672f, 110f, 48f, style = { 1 }) { startNewSame() }

    private val ovAgainBtn: Button = btn({ t("again") }, 140f, 672f, 190f, 48f) { startNewSame() }
    private val ovMenuBtn: Button = btn({ t("menu") }, 340f, 672f, 190f, 48f, style = { 1 }) { toSetup() }

    init {
        refreshSave()
    }

    private fun over(): Boolean = game?.status != MinesEngine.PLAYING

    override fun buttons(): List<Button> = when (phase) {
        Phase.SETUP -> {
            val l = ArrayList<Button>()
            l.add(backBtn)
            l.addAll(levelBtns)
            l.add(playBtn)
            l.add(resumeBtn)
            l.add(newBtn)
            l
        }
        Phase.PLAY ->
            if (!over()) listOf(backBtn, digBtn, flagBtn, restartBtn)
            else if (overT < 20) listOf(backBtn)
            else listOf(backBtn, ovAgainBtn, ovMenuBtn)
    }

    // ------------------------------- game flow ------------------------------

    private fun refreshSave() {
        val s = store.getS("mn_save")
        var g: MinesGame? = null
        if (s != null) {
            g = MinesGame.deserialize(s)
            if (g == null || g.status != MinesEngine.PLAYING || !g.placed) {
                g = null
                store.remove("mn_save")
            }
        }
        saved = g
        hasSave = g != null
    }

    private fun setLevel(i: Int) {
        level = i
        store.putI("mn_lvl", i)
    }

    private fun begin(g: MinesGame) {
        game = g
        phase = Phase.PLAY
        cell = min(BOARD_MAX_W / g.cols, BOARD_MAX_H / g.rows).toInt().toFloat()
        bl = (W - cell * g.cols) / 2f
        bt = BOARD_TOP + (BOARD_MAX_H - cell * g.rows) / 2f
        delay = IntArray(g.n)
        pop = IntArray(g.n)
        pressCell = -1
        longDone = false
        overT = 0
        winRecord = false
        flagMode = false
        particles.clear()
        store.remove("mn_save")
    }

    private fun startNew() {
        if (fading) return
        begin(MinesGame(level))
    }

    private fun startNewSame() {
        if (fading) return
        val g = game
        if (g != null) level = g.level
        startNew()
    }

    private fun resumeSaved() {
        val g = saved ?: return
        level = g.level
        begin(g)
        store.putS("mn_save", g.serialize())
    }

    private fun toSetup() {
        phase = Phase.SETUP
        game = null
        pressCell = -1
        refreshSave()
    }

    private fun saveNow() {
        val g = game ?: return
        if (phase == Phase.PLAY && g.status == MinesEngine.PLAYING && g.placed) store.putS("mn_save", g.serialize())
    }

    private fun cellAt(x: Float, y: Float): Int {
        val g = game ?: return -1
        if (x < bl || y < bt || x >= bl + cell * g.cols || y >= bt + cell * g.rows) return -1
        val col = ((x - bl) / cell).toInt().coerceIn(0, g.cols - 1)
        val row = ((y - bt) / cell).toInt().coerceIn(0, g.rows - 1)
        return row * g.cols + col
    }

    private fun cellCx(i: Int, g: MinesGame): Float = bl + (i % g.cols + 0.5f) * cell
    private fun cellCy(i: Int, g: MinesGame): Float = bt + (i / g.cols + 0.5f) * cell

    /** Actions: tap and long press on a cell. */
    private fun doReveal(i: Int) {
        val g = game ?: return
        val res = g.reveal(i)
        afterStep(g, res, i)
    }

    private fun doChord(i: Int) {
        val g = game ?: return
        if (!g.canChord(i)) {
            shake = 1.5f
            return
        }
        val res = g.chord(i)
        afterStep(g, res, i)
    }

    private fun doFlag(i: Int) {
        val g = game ?: return
        if (g.toggleFlag(i)) {
            sfx.play(Sfx.Kind.PLACE, 0.5f)
            haptic()
            pop[i] = 8
            saveNow()
        }
    }

    private fun onTap(i: Int) {
        val g = game ?: return
        if (phase != Phase.PLAY || g.status != MinesEngine.PLAYING || fading) return
        when (g.st[i]) {
            MinesEngine.OPEN -> if (g.adj[i] > 0) doChord(i)
            MinesEngine.FLAG -> if (flagMode) doFlag(i) else shake = 1.5f
            else -> if (flagMode) doFlag(i) else doReveal(i)
        }
    }

    private fun onLong(i: Int) {
        val g = game ?: return
        if (phase != Phase.PLAY || g.status != MinesEngine.PLAYING || fading) return
        when (g.st[i]) {
            MinesEngine.FLAG -> doFlag(i)
            MinesEngine.HIDDEN -> if (flagMode) doReveal(i) else doFlag(i)
            else -> {}
        }
    }

    private fun afterStep(g: MinesGame, res: Int, from: Int) {
        if (res == MinesEngine.NOOP) return
        // ripple: opened cells appear in waves starting at the tapped cell
        val fr = from % g.cols
        val fw = from / g.cols
        for (j in g.newly) {
            val d = max(abs(j % g.cols - fr), abs(j / g.cols - fw))
            delay[j] = min(26, d * 2)
            pop[j] = 8
        }
        if (res == MinesEngine.BOOM) {
            lose(g)
            return
        }
        sfx.play(Sfx.Kind.POP, 0.5f)
        if (res == MinesEngine.CLEARED) {
            win(g)
            return
        }
        saveNow()
    }

    private fun lose(g: MinesGame) {
        overT = 0
        store.addStat("mines", 1)
        Achievements.recordGame(store, false)
        store.remove("mn_save")
        sfx.play(Sfx.Kind.LOSE)
        shake = 6f
        haptic(true)
        val b = g.boom
        if (b >= 0) emit(cellCx(b, g), cellCy(b, g), 26, 1.2f, 4.5f, 40, 4f, 8f, 0.06f, Ui.DANGER, accent, Color.rgb(255, 205, 80))
    }

    private fun win(g: MinesGame) {
        overT = 0
        winSecs = max(1, g.frames / 60)
        winRecord = false
        val key = "mn_best_" + g.level
        val old = store.getI(key, 0)
        if (old <= 0 || winSecs < old) {
            store.putI(key, winSecs)
            winRecord = true
        }
        store.addStat("mines", 0)
        Achievements.recordGame(store, true)
        Achievements.unlock(store, when (g.level) {
            0 -> "mines_easy"
            1 -> "mines_med"
            else -> "mines_hard"
        })
        store.remove("mn_save")
        sfx.play(Sfx.Kind.WIN)
        confetti()
        haptic(true)
    }

    // ------------------------------- input ----------------------------------

    override fun onPointerDown(x: Float, y: Float) {
        if (phase != Phase.PLAY || over()) return
        pressCell = cellAt(x, y)
        pressFrames = 0
        longDone = false
    }

    override fun onPointerMove(x: Float, y: Float) {
        if (pressCell < 0) return
        if (cellAt(x, y) != pressCell) pressCell = -1      // the finger slid away: cancel
    }

    override fun onPointerUp(x: Float, y: Float) {
        val c = pressCell
        pressCell = -1
        if (c < 0 || longDone || phase != Phase.PLAY) return
        if (cellAt(x, y) == c) onTap(c)
    }

    override fun onBack(): Boolean {
        if (fading) return true
        when (phase) {
            Phase.SETUP -> leaveTo(GameId.HUB)
            Phase.PLAY -> {
                saveNow()
                toSetup()
            }
        }
        return true
    }

    override fun onHostPause() {
        saveNow()
        super.onHostPause()
    }

    // ------------------------------- update ---------------------------------

    override fun update() {
        val g = game
        if (phase != Phase.PLAY || g == null) return
        if (g.status == MinesEngine.PLAYING) {
            if (g.placed) g.frames++
            if (pressCell >= 0 && !longDone) {
                pressFrames++
                if (pressFrames >= LONG_PRESS) {
                    longDone = true
                    onLong(pressCell)
                }
            }
        } else if (overT < 1000) {
            overT++
        }
        for (i in 0 until g.n) {
            if (delay[i] > 0) delay[i]--
            else if (pop[i] > 0) pop[i]--
        }
    }

    // ------------------------------- drawing --------------------------------

    private fun fmtTime(secs: Int): String {
        val s = max(0, secs)
        val m = s / 60
        val r = s % 60
        return m.toString() + ":" + (if (r < 10) "0" else "") + r
    }

    private fun numColor(n: Int): Int = if (Ui.dark) when (n) {
        1 -> Color.rgb(90, 160, 255)
        2 -> Color.rgb(98, 214, 140)
        3 -> Color.rgb(240, 110, 110)
        4 -> Color.rgb(176, 140, 255)
        5 -> Color.rgb(255, 170, 80)
        6 -> Color.rgb(80, 210, 210)
        7 -> Ui.TEXT
        else -> Ui.MUTED
    } else when (n) {
        1 -> Color.rgb(25, 95, 215)
        2 -> Color.rgb(20, 140, 70)
        3 -> Color.rgb(205, 55, 55)
        4 -> Color.rgb(110, 70, 210)
        5 -> Color.rgb(200, 110, 20)
        6 -> Color.rgb(15, 140, 150)
        7 -> Ui.TEXT
        else -> Ui.MUTED
    }

    override fun drawScreen(c: Canvas) {
        drawBackdrop(c)
        if (phase == Phase.SETUP) drawSetup(c) else drawGame(c)
        drawButton(c, backBtn)
        drawParticles(c)
    }

    private fun drawSetup(c: Canvas) {
        c.save()
        c.translate(W / 2f, 170f)
        c.scale(1.7f, 1.7f)
        MinesIcon.draw(c, 0f, 0f, accent)
        c.restore()
        drawTitle(c, t("mn_title"), W / 2f, 288f, 40f, Ui.TEXT, 360f)

        for (b in levelBtns) drawButton(c, b)

        val lv = MinesEngine.LEVELS[level]
        plainText(c, t("mn_info", lv.cols, lv.rows, lv.mines), W / 2f, 436f, 16f, Ui.TEXT, 0, 420f)
        val best = store.getI("mn_best_$level", 0)
        plainText(c, t("mn_best_time", if (best > 0) fmtTime(best) else "—"), W / 2f, 460f, 16f, Ui.MUTED, 0, 420f)
        plainText(c, t("mn_cleared_n", store.stat("mines", 0)), W / 2f, 484f, 16f, Ui.MUTED, 0, 420f)

        val s = saved
        if (s != null) {
            plainText(
                c, t("mn_saved", t(LEVEL_KEYS[s.level]), fmtTime(s.frames / 60)),
                W / 2f, 516f, 16f, accent, 0, 420f
            )
        }
        drawButton(c, playBtn)
        drawButton(c, resumeBtn)
        drawButton(c, newBtn)

        plainText(c, t("mn_howto"), W / 2f, 676f, 13f, Ui.MUTED, 0, 440f)
        plainText(c, t("mn_howto2"), W / 2f, 696f, 13f, Ui.MUTED, 0, 440f)
    }

    private fun drawGame(c: Canvas) {
        val g = game ?: return
        val lost = g.status == MinesEngine.LOST
        val won = g.status == MinesEngine.WON

        // header: mines left in the middle, time on the right
        val ix = W / 2f - 28f
        drawMine(c, ix, 56f, 10f, Ui.TEXT)
        text(c, g.minesLeft.toString(), W / 2f + 22f, 64f, 24f, if (g.minesLeft < 0) Ui.DANGER else Ui.TEXT, maxW = 64f, shadow = false)
        val secs = if (won) winSecs else g.frames / 60
        text(c, fmtTime(secs), 420f, 64f, 24f, if (won) Ui.OK else Ui.TEXT, maxW = 90f, shadow = false)

        if (lost) {
            text(c, t("mn_boom"), W / 2f, 106f, 22f, Ui.DANGER, maxW = 260f, shadow = false)
            plainText(c, t("mn_progress", g.percent), 456f, 88f, 12f, Ui.MUTED, 1, 130f)
        } else if (won) {
            text(c, t("mn_win"), W / 2f, 106f, 22f, Ui.OK, maxW = 260f, shadow = false)
            if (winRecord) plainText(c, t("mn_record"), 456f, 88f, 12f, Ui.OK, 1, 130f)
        } else {
            plainText(
                c, t(LEVEL_KEYS[g.level]) + " · " + g.cols + "×" + g.rows, W / 2f, 106f, 15f, Ui.MUTED, 0, 260f
            )
        }

        drawBoard(c, g)

        if (!over()) {
            drawButton(c, digBtn)
            drawButton(c, flagBtn)
            drawButton(c, restartBtn)
        } else if (overT >= 20) {
            drawButton(c, ovAgainBtn)
            drawButton(c, ovMenuBtn)
        }
    }

    private fun drawBoard(c: Canvas, g: MinesGame) {
        val bw = cell * g.cols
        val bh = cell * g.rows
        fillRRect(c, bl - 5f, bt - 5f, bl + bw + 5f, bt + bh + 5f, 12f, Ui.BORDER)
        val hidden = mixC(Ui.SURFACE_HI, Ui.TEXT, if (Ui.dark) 0.14f else 0.3f)
        val hiddenPress = mixC(hidden, accent, 0.4f)
        val open = Ui.SURFACE
        val rad = min(7f, cell * 0.18f)
        val lost = g.status == MinesEngine.LOST
        val boom = g.boom
        val numSize = cell * 0.6f

        for (i in 0 until g.n) {
            val l = bl + (i % g.cols) * cell + 0.9f
            val tp = bt + (i / g.cols) * cell + 0.9f
            val r = l + cell - 1.8f
            val b = tp + cell - 1.8f
            val cx = (l + r) / 2f
            val cy = (tp + b) / 2f
            val state = g.st[i]
            val isMine = g.mine[i]

            // what is shown on this cell now
            var showOpen = state == MinesEngine.OPEN && !isMine && delay[i] <= 0
            var showMine = false
            var showWrongFlag = false
            if (lost) {
                val d = dist(i, boom, g)
                val revealed = overT * 0.45f >= d
                if (i == boom) showMine = true
                else if (isMine && state != MinesEngine.FLAG && revealed) showMine = true
                else if (!isMine && state == MinesEngine.FLAG && revealed) showWrongFlag = true
            }
            if (state == MinesEngine.OPEN && isMine) showMine = true

            var fillC = if (showOpen) open else hidden
            if (i == pressCell && state != MinesEngine.OPEN && !over()) fillC = hiddenPress
            if (showMine && i == boom) fillC = mixC(open, Ui.DANGER, 0.55f)
            else if (showMine) fillC = mixC(hidden, open, 0.5f)

            // pop animation of freshly opened cells
            val p = if (showOpen) pop[i] / 8f else 0f
            val s = 1f - 0.16f * p
            c.save()
            c.translate(cx, cy)
            c.scale(s, s)
            fillRRect(c, l - cx, tp - cy, r - cx, b - cy, rad, fillC)
            if (!showOpen && !showMine) {
                // light top edge of a raised cell
                fillRRect(c, l - cx + 1.5f, tp - cy + 1f, r - cx - 1.5f, tp - cy + 2.6f, 1f, withA(Color.WHITE, if (Ui.dark) 0.10f else 0.45f))
            }
            c.restore()

            if (showOpen) {
                val n = g.adj[i]
                if (n > 0) text(c, n.toString(), cx, cy + numSize * 0.34f, numSize, numColor(n), shadow = false)
            }
            if (showMine) drawMine(c, cx, cy, cell * 0.28f, Ui.TEXT)
            if (state == MinesEngine.FLAG && !showWrongFlag) {
                val fs = if (pop[i] > 0) cell * (0.5f + 0.06f * (8 - pop[i]) / 8f) else cell * 0.56f
                drawFlag(c, cx, cy, fs, accent)
            }
            if (showWrongFlag) {
                drawFlag(c, cx, cy, cell * 0.56f, withA(accent, 0.5f))
                line.color = Ui.DANGER
                line.strokeWidth = max(2f, cell * 0.07f)
                val q = cell * 0.26f
                c.drawLine(cx - q, cy - q, cx + q, cy + q, line)
                c.drawLine(cx + q, cy - q, cx - q, cy + q, line)
            }
        }
    }

    private fun dist(i: Int, j: Int, g: MinesGame): Float {
        if (j < 0) return 0f
        val dx = (i % g.cols - j % g.cols).toFloat()
        val dy = (i / g.cols - j / g.cols).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    private fun drawFlag(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val px = cx - s * 0.14f
        line.color = Ui.TEXT
        line.strokeWidth = max(1.6f, s * 0.07f)
        c.drawLine(px, cy - s * 0.4f, px, cy + s * 0.38f, line)
        c.drawLine(cx - s * 0.32f, cy + s * 0.38f, cx + s * 0.1f, cy + s * 0.38f, line)
        path.reset()
        path.moveTo(px, cy - s * 0.4f)
        path.lineTo(cx + s * 0.36f, cy - s * 0.16f)
        path.lineTo(px, cy + s * 0.08f)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun drawMine(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        line.color = color
        line.strokeWidth = max(1.6f, r * 0.24f)
        for (k in 0 until 4) {
            val a = k * Math.PI.toFloat() / 4f
            val dx = Math.cos(a.toDouble()).toFloat() * r * 1.45f
            val dy = Math.sin(a.toDouble()).toFloat() * r * 1.45f
            c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, line)
        }
        fill.color = color
        c.drawCircle(cx, cy, r * 0.95f, fill)
        fill.color = withA(if (Ui.dark) Ui.INK else Color.WHITE, 0.9f)
        c.drawCircle(cx - r * 0.32f, cy - r * 0.32f, r * 0.24f, fill)
    }

    private companion object {
        val LEVEL_KEYS = arrayOf("lvl_easy", "lvl_med", "lvl_hard")
    }
}
