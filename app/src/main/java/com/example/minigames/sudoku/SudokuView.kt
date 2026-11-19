package com.example.minigames.sudoku

import android.content.Context
import android.graphics.Canvas
import com.example.minigames.core.Achievements
import com.example.minigames.core.BaseView
import com.example.minigames.core.Button
import com.example.minigames.core.GameId
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.Ui
import com.example.minigames.core.W
import com.example.minigames.core.clampF
import com.example.minigames.core.mixC
import com.example.minigames.core.t
import com.example.minigames.core.withA
import kotlin.math.max

// board geometry (logical coordinates): 9 cells of 48 px
private const val BL = 24f
private const val BT = 124f
private const val CELL = 48f
private const val BOARD = CELL * 9f

private enum class Phase { SETUP, PLAY, WIN }

private val DIGITS = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

/**
 * Sudoku: three levels, pencil notes, undo, hints, errors counter and a timer. The running game is saved after
 * every move (key sudoku_save). Records: sd_best_0..2 (seconds, only games without hints), sd_lvl (chosen level);
 * statistics stat_sudoku_0 (solved games).
 */
class SudokuView(context: Context, host: Host) : BaseView(context, host) {

    override val accent: Int get() = SudokuIcon.ACCENT

    private var phase = Phase.SETUP
    private var game: SudokuGame? = null
    private var saved: SudokuGame? = null          // the stored game shown on the setup screen
    private var hasSave = false
    private var level = store.getI("sd_lvl", 0).coerceIn(0, 2)

    private var selCell = -1
    private var notesMode = false
    private var overT = 0              // frames since the game was solved
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

    private val playBtn: Button = btn({ t("play") }, W / 2f, 548f, 240f, 56f, visible = { !hasSave }) {
        startNew()
    }
    private val resumeBtn: Button = btn({ t("resume") }, W / 2f, 548f, 240f, 56f, visible = { hasSave }) {
        resumeSaved()
    }
    private val newBtn: Button = btn({ t("new_game") }, W / 2f, 616f, 240f, 50f, style = { 1 }, visible = { hasSave }) {
        startNew()
    }

    private val digitBtns: List<Button> = List(9) { i ->
        val d = i + 1
        btn(
            { DIGITS[d] }, BL + CELL / 2f + i * CELL, 590f, 44f, 48f,
            enabled = { val g = game; g != null && g.correctCount(d) < 9 }
        ) { onDigit(d) }
    }

    private val undoBtn: Button = btn(
        { t("undo") }, 24f + 45f, 654f, 90f, 44f, style = { 1 },
        enabled = { game?.canUndo == true }
    ) { onUndo() }
    private val eraseBtn: Button = btn({ t("sd_erase") }, 24f + 90f + 7f + 49f, 654f, 98f, 44f, style = { 1 }) {
        onErase()
    }
    private val notesBtn: Button = btn(
        { t("sd_notes") }, 24f + 90f + 98f + 14f + 53f, 654f, 106f, 44f,
        style = { if (notesMode) 2 else 1 }
    ) {
        notesMode = !notesMode
    }
    private val hintBtn: Button = btn({ t("hint") }, 24f + 90f + 98f + 106f + 21f + 58f, 654f, 116f, 44f, style = { 1 }) {
        onHint()
    }

    private val ovAgainBtn: Button = btn({ t("again") }, W / 2f, 446f, 240f, 50f) { startNewSame() }
    private val ovMenuBtn: Button = btn({ t("menu") }, W / 2f, 506f, 240f, 50f, style = { 1 }) { toSetup() }

    init {
        refreshSave()
    }

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
        Phase.PLAY -> {
            val l = ArrayList<Button>()
            l.add(backBtn)
            l.addAll(digitBtns)
            l.add(undoBtn)
            l.add(eraseBtn)
            l.add(notesBtn)
            l.add(hintBtn)
            l
        }
        Phase.WIN -> if (overT < 14) listOf(backBtn) else listOf(backBtn, ovAgainBtn, ovMenuBtn)
    }

    // ------------------------------- game flow ------------------------------

    private fun refreshSave() {
        val s = store.getS("sudoku_save")
        var g: SudokuGame? = null
        if (s != null) {
            g = SudokuGame.deserialize(s)
            if (g == null || g.isSolved()) {
                g = null
                store.remove("sudoku_save")
            }
        }
        saved = g
        hasSave = g != null
    }

    private fun setLevel(i: Int) {
        level = i
        store.putI("sd_lvl", i)
    }

    private fun begin(g: SudokuGame) {
        game = g
        phase = Phase.PLAY
        selCell = -1
        notesMode = false
        overT = 0
        winRecord = false
        particles.clear()
        store.putS("sudoku_save", g.serialize())
    }

    private fun startNew() {
        if (fading) return
        begin(SudokuGame(SudokuEngine.generate(level)))
    }

    private fun startNewSame() {
        val g = game
        if (g != null) level = g.puzzle.diff
        startNew()
    }

    private fun resumeSaved() {
        val g = saved ?: return
        level = g.puzzle.diff
        begin(g)
    }

    private fun toSetup() {
        phase = Phase.SETUP
        game = null
        refreshSave()
    }

    private fun saveNow() {
        val g = game ?: return
        if (phase == Phase.PLAY) store.putS("sudoku_save", g.serialize())
    }

    private fun onDigit(d: Int) {
        val g = game ?: return
        if (phase != Phase.PLAY || fading) return
        val cell = selCell
        if (cell < 0 || g.given[cell]) {
            shake = 2f
            return
        }
        if (notesMode) {
            if (g.toggleNote(cell, d)) saveNow() else shake = 2f
            return
        }
        when (g.place(cell, d)) {
            1 -> {
                sfx.play(Sfx.Kind.PLACE, 0.5f)
                if (g.correctCount(d) == 9) {
                    emit(BL + (d - 0.5f) * CELL, 590f, 12, 1f, 3f, 26, 3f, 5f, 0.05f, accent)
                }
            }
            2 -> {
                sfx.play(Sfx.Kind.HIT, 0.5f)
                shake = 3f
                haptic()
            }
            else -> return
        }
        saveNow()
        checkSolved()
    }

    private fun onUndo() {
        val g = game ?: return
        if (phase != Phase.PLAY || fading) return
        if (g.undo()) saveNow()
    }

    private fun onErase() {
        val g = game ?: return
        if (phase != Phase.PLAY || fading || selCell < 0) return
        if (g.erase(selCell)) saveNow()
    }

    private fun onHint() {
        val g = game ?: return
        if (phase != Phase.PLAY || fading) return
        val cell = g.hint(selCell)
        if (cell < 0) return
        selCell = cell
        sfx.play(Sfx.Kind.POP, 0.5f)
        val cx = BL + (cell % 9 + 0.5f) * CELL
        val cy = BT + (cell / 9 + 0.5f) * CELL
        emit(cx, cy, 8, 0.8f, 2.4f, 24, 3f, 5f, 0.04f, accent)
        saveNow()
        checkSolved()
    }

    private fun checkSolved() {
        val g = game ?: return
        if (phase != Phase.PLAY || !g.isSolved()) return
        phase = Phase.WIN
        overT = -20
        val diff = g.puzzle.diff
        winSecs = max(1, g.frames / 60)
        winRecord = false
        if (g.hints == 0) {
            val key = "sd_best_$diff"
            val old = store.getI(key, 0)
            if (old <= 0 || winSecs < old) {
                store.putI(key, winSecs)
                winRecord = true
            }
        }
        store.addStat("sudoku", 0)
        Achievements.recordGame(store, true)
        Achievements.unlock(store, when (diff) {
            0 -> "sudoku_easy"
            1 -> "sudoku_med"
            else -> "sudoku_hard"
        })
        if (g.mistakes == 0 && g.hints == 0) Achievements.unlock(store, "sudoku_clean")
        store.remove("sudoku_save")
        sfx.play(Sfx.Kind.WIN)
        confetti()
        haptic(true)
    }

    // ------------------------------- input ----------------------------------

    private fun cellAt(x: Float, y: Float): Int {
        if (x < BL || x >= BL + BOARD || y < BT || y >= BT + BOARD) return -1
        val col = ((x - BL) / CELL).toInt().coerceIn(0, 8)
        val row = ((y - BT) / CELL).toInt().coerceIn(0, 8)
        return row * 9 + col
    }

    override fun onPointerDown(x: Float, y: Float) {
        if (phase != Phase.PLAY) return
        val c = cellAt(x, y)
        if (c >= 0) selCell = c
    }

    override fun onPointerMove(x: Float, y: Float) {
        if (phase != Phase.PLAY) return
        val c = cellAt(x, y)
        if (c >= 0) selCell = c
    }

    override fun onBack(): Boolean {
        if (fading) return true
        when (phase) {
            Phase.SETUP -> leaveTo(GameId.HUB)
            Phase.PLAY -> {
                saveNow()
                toSetup()
            }
            Phase.WIN -> toSetup()
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
        if (phase == Phase.PLAY && g != null) g.frames++
        if (phase == Phase.WIN && overT < 1000) overT++
    }

    // ------------------------------- drawing --------------------------------

    private fun fmtTime(secs: Int): String {
        val s = max(0, secs)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        val ss = (if (r < 10) "0" else "") + r
        return if (h > 0) h.toString() + ":" + (if (m < 10) "0" else "") + m + ":" + ss else m.toString() + ":" + ss
    }

    /** Colour for user-entered digits: the accent, a bit darker on the light theme for contrast. */
    private fun entryColor(): Int = if (Ui.dark) accent else mixC(accent, Ui.INK, 0.3f)

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
        SudokuIcon.draw(c, 0f, 0f, accent)
        c.restore()
        drawTitle(c, t("sd_title"), W / 2f, 288f, 40f, Ui.TEXT, 360f)

        for (b in levelBtns) drawButton(c, b)

        val best = store.getI("sd_best_$level", 0)
        plainText(c, t("sd_best_time", if (best > 0) fmtTime(best) else "—"), W / 2f, 438f, 16f, Ui.MUTED, 0, 420f)
        plainText(c, t("sd_solved_n", store.stat("sudoku", 0)), W / 2f, 464f, 16f, Ui.MUTED, 0, 420f)

        val s = saved
        if (s != null) {
            plainText(
                c, t("sd_saved", t(LEVEL_KEYS[s.puzzle.diff]), fmtTime(s.frames / 60)),
                W / 2f, 502f, 16f, accent, 0, 420f
            )
        }
        drawButton(c, playBtn)
        drawButton(c, resumeBtn)
        drawButton(c, newBtn)
    }

    private fun drawGame(c: Canvas) {
        val g = game ?: return
        // header
        text(c, t(LEVEL_KEYS[g.puzzle.diff]), W / 2f, 62f, 22f, accent, maxW = 160f, shadow = false)
        text(c, fmtTime(g.frames / 60), 412f, 62f, 22f, Ui.TEXT, maxW = 100f, shadow = false)
        plainText(
            c, t("sd_errors", g.mistakes), BL, 112f, 14f,
            if (g.mistakes > 0) Ui.DANGER else Ui.MUTED, -1, 200f
        )
        plainText(c, t("sd_hints", g.hints), BL + BOARD, 112f, 14f, Ui.MUTED, 1, 200f)

        drawBoard(c, g)

        for (i in 0 until 9) drawKey(c, g, i)
        drawButton(c, undoBtn)
        drawButton(c, eraseBtn)
        drawButton(c, notesBtn)
        drawButton(c, hintBtn)

        if (phase == Phase.WIN && overT > 0) drawWin(c, g)
    }

    private fun drawBoard(c: Canvas, g: SudokuGame) {
        fillRRect(c, BL, BT, BL + BOARD, BT + BOARD, 10f, Ui.SURFACE)

        val sc = selCell
        val sd = if (sc >= 0) g.v[sc] else 0
        val hiPeer = Ui.SURFACE_HI
        val hiSame = mixC(Ui.SURFACE, accent, 0.24f)
        val hiSel = mixC(Ui.SURFACE, accent, 0.5f)
        val hiWrong = mixC(Ui.SURFACE, Ui.DANGER, 0.22f)

        for (i in 0 until 81) {
            val l = BL + (i % 9) * CELL
            val tp = BT + (i / 9) * CELL
            val v = g.v[i]
            val wrong = v != 0 && !g.given[i] && v != g.puzzle.solution[i]
            var bg = 0
            if (i == sc) bg = hiSel
            else if (wrong) bg = hiWrong
            else if (sd != 0 && v == sd) bg = hiSame
            else if (sc >= 0 && SudokuEngine.PEERS[sc].contains(i)) bg = hiPeer
            if (bg != 0) fillRect(c, l + 0.5f, tp + 0.5f, l + CELL - 0.5f, tp + CELL - 0.5f, bg)
        }

        // grid lines: thin between cells, thick between boxes
        for (k in 1 until 9) {
            if (k % 3 == 0) continue
            line.color = Ui.GRID
            line.strokeWidth = 1f
            c.drawLine(BL + k * CELL, BT + 3f, BL + k * CELL, BT + BOARD - 3f, line)
            c.drawLine(BL + 3f, BT + k * CELL, BL + BOARD - 3f, BT + k * CELL, line)
        }
        for (k in 1..2) {
            line.color = Ui.MUTED
            line.strokeWidth = 2.2f
            c.drawLine(BL + 3 * k * CELL, BT + 2f, BL + 3 * k * CELL, BT + BOARD - 2f, line)
            c.drawLine(BL + 2f, BT + 3 * k * CELL, BL + BOARD - 2f, BT + 3 * k * CELL, line)
        }
        strokeRRect(c, BL, BT, BL + BOARD, BT + BOARD, 10f, Ui.MUTED, 2.2f)

        // digits and notes
        val mine = entryColor()
        for (i in 0 until 81) {
            val cx = BL + (i % 9 + 0.5f) * CELL
            val cy = BT + (i / 9 + 0.5f) * CELL
            val v = g.v[i]
            if (v != 0) {
                val color = when {
                    g.given[i] -> Ui.TEXT
                    v != g.puzzle.solution[i] -> Ui.DANGER
                    else -> mine
                }
                text(c, DIGITS[v], cx, cy + 9f, 27f, color, shadow = false)
            } else {
                val mask = g.notes[i]
                if (mask != 0) {
                    for (d in 1..9) {
                        if (mask and (1 shl (d - 1)) == 0) continue
                        val nx = cx + ((d - 1) % 3 - 1) * 14f
                        val ny = cy + ((d - 1) / 3 - 1) * 14f + 4f
                        val hot = sd == d
                        plainText(c, DIGITS[d], nx, ny, 11f, if (hot) mine else Ui.MUTED)
                    }
                }
            }
        }
    }

    private fun drawKey(c: Canvas, g: SudokuGame, i: Int) {
        val b = digitBtns[i]
        val d = i + 1
        val done = g.correctCount(d) >= 9
        val sc = 1f - 0.05f * b.down
        c.save()
        c.translate(b.cx, b.cy)
        c.scale(sc, sc)
        val l = -b.w / 2f
        val r = b.w / 2f
        val tp = -b.h / 2f
        val bt = b.h / 2f
        if (done) {
            fillRRect(c, l, tp, r, bt, 10f, Ui.DISABLED_BG)
            text(c, DIGITS[d], 0f, 5f, 24f, Ui.DISABLED_FG, shadow = false)
        } else {
            val pr = max(b.down, b.press)
            val fillC = if (notesMode) mixC(Ui.SURFACE, accent, 0.16f + 0.12f * pr) else mixC(Ui.SURFACE, Ui.TEXT, 0.07f * pr)
            fillRRect(c, l, tp, r, bt, 10f, fillC)
            strokeRRect(c, l, tp, r, bt, 10f, if (notesMode) accent else Ui.BORDER, 1.5f)
            text(c, DIGITS[d], 0f, 3f, 24f, if (notesMode) accent else Ui.TEXT, shadow = false)
            plainText(c, DIGITS[9 - g.correctCount(d)], 0f, 18f, 10f, Ui.MUTED)
        }
        c.restore()
    }

    private fun drawWin(c: Canvas, g: SudokuGame) {
        val a = clampF(overT / 14f, 0f, 1f)
        fillRRect(c, BL, BT, BL + BOARD, BT + BOARD, 10f, withA(Ui.BG_BOTTOM, 0.82f * a))
        if (a < 1f) return
        drawPanel(c, 50f, 140f, 430f, 554f)
        text(c, t("sd_win"), W / 2f, 202f, 34f, accent, maxW = 320f, shadow = false)
        text(c, fmtTime(winSecs), W / 2f, 266f, 44f, Ui.TEXT, maxW = 300f, shadow = false)
        plainText(c, t(LEVEL_KEYS[g.puzzle.diff]), W / 2f, 296f, 16f, Ui.MUTED, 0, 320f)
        plainText(
            c, t("sd_errors", g.mistakes), W / 2f, 330f, 17f,
            if (g.mistakes > 0) Ui.DANGER else Ui.MUTED, 0, 320f
        )
        plainText(c, t("sd_hints", g.hints), W / 2f, 356f, 17f, Ui.MUTED, 0, 320f)
        if (winRecord) {
            plainText(c, t("sd_record"), W / 2f, 390f, 17f, Ui.OK, 0, 320f)
        } else if (g.hints == 0) {
            val best = store.getI("sd_best_" + g.puzzle.diff, 0)
            if (best > 0) plainText(c, t("sd_best_time", fmtTime(best)), W / 2f, 390f, 16f, Ui.MUTED, 0, 320f)
        }
        drawButton(c, ovAgainBtn)
        drawButton(c, ovMenuBtn)
    }

    private companion object {
        val LEVEL_KEYS = arrayOf("lvl_easy", "lvl_med", "lvl_hard")
    }
}
