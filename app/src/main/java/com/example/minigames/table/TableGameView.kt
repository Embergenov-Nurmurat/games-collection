package com.example.minigames.table

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.BaseView
import com.example.minigames.core.Achievements
import com.example.minigames.core.Button
import com.example.minigames.core.GameId
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.Ui
import com.example.minigames.core.W
import com.example.minigames.core.clampF
import com.example.minigames.core.mixC
import com.example.minigames.core.withA
import com.example.minigames.core.easeOutBack
import com.example.minigames.core.t
import kotlin.random.Random

/**
 * Shared skeleton of the board games (tic-tac-toe, chess, checkers):
 *  - a setup screen (opponent, level, side, game specific options),
 *  - the play screen chrome (title, status line, undo / hint / new buttons),
 *  - a result panel, statistics, background AI thread, "thinking" indicator.
 *
 * Side numbering: 0 = the side that moves first (white / X), 1 = the second side.
 *
 * Optional features a subclass switches on:
 *  - [supportsClock]: chess clocks (setup button "TIME", pills in the title bar). The subclass calls
 *    [setClock] with the side to move after every move and [stopClock] when the game ends.
 *  - [supportsF2f]: "face to face" option for two players on one phone (the subclass draws the pieces of
 *    side 1 upside down when [f2f] is true).
 *  - [saveState] / [loadState]: the game is stored when the player leaves or the app pauses; the setup
 *    screen then offers "Continue".
 */
abstract class TableGameView(
    context: Context,
    host: Host,
    protected val key: String,
    private val titleKey: String
) : BaseView(context, host) {

    protected enum class Phase { SETUP, PLAY, OVER }

    private companion object {
        /** Minutes per side for the "time" option (0 = no limit). */
        val TIME_MIN = intArrayOf(0, 3, 5, 10)
    }

    protected var phase = Phase.SETUP

    protected var vsAi = store.getB("${key}_ai", true)
    protected var level = store.getI("${key}_lvl", 1).coerceIn(0, 2)
    protected var sideOpt = store.getI("${key}_side", 0).coerceIn(0, 2)

    /** Which side the human plays against the computer (always 0 in two-player mode). */
    protected var humanSide = 0

    /** 0 running, 1 first side won, 2 second side won, 3 draw */
    protected var result = 0
    protected var reasonKey = ""
    protected var overT = 0

    protected var thinking = false
    private var aiGen = 0

    // --- optional features (see the class comment) ---
    /** Override with a getter (`override val supportsClock: Boolean get() = true`), not an initialiser. */
    protected open val supportsClock: Boolean get() = false
    protected open val supportsF2f: Boolean get() = false
    /** False for games without a first-move choice. */
    protected open val hasSideOption: Boolean get() = true

    protected var timeOpt = store.getI("${key}_time", 0).coerceIn(0, 3)
    protected var f2fOpt = store.getB("${key}_f2f", false)
    protected val f2f: Boolean get() = supportsF2f && !vsAi && f2fOpt
    protected val clockOn: Boolean get() = supportsClock && timeOpt > 0

    /** Remaining time in ms of side 0 and side 1. */
    protected val clockMs = LongArray(2)
    private var clockSide = -1
    private var hasSave = store.getS("${key}_save") != null

    private var message = ""
    private var messageT = 0

    // ------------------------------- buttons --------------------------------

    protected val backBtn: Button = btn({ "<" }, 50f, 52f, 64f, 46f, style = { 1 }) { onBack() }

    private val setupBtns = ArrayList<Button>()

    private val modeBtn: Button = btn(
        { if (vsAi) t("vs_ai") else t("two_players") }, W / 2f, 262f, 320f, 52f, style = { 1 }
    ) {
        vsAi = !vsAi
        store.putB("${key}_ai", vsAi)
        relayout()
    }

    private val levelBtn: Button = btn(
        { t("level_x", t(levelKey(level))) }, W / 2f, 328f, 320f, 52f, style = { 1 }, visible = { vsAi }
    ) {
        level = (level + 1) % 3
        store.putI("${key}_lvl", level)
    }

    private val sideBtn: Button = btn(
        { t("side_x", if (sideOpt == 2) t("random") else sideName(sideOpt)) },
        W / 2f, 394f, 320f, 52f, style = { 1 }, visible = { vsAi && hasSideOption }
    ) {
        sideOpt = (sideOpt + 1) % 3
        store.putI("${key}_side", sideOpt)
    }

    private val timeBtn: Button = btn(
        { t("time_x", if (timeOpt == 0) t("time_off") else t("min_x", TIME_MIN[timeOpt])) },
        W / 2f, 460f, 320f, 52f, style = { if (timeOpt == 0) 1 else 2 }, visible = { supportsClock }
    ) {
        timeOpt = (timeOpt + 1) % TIME_MIN.size
        store.putI("${key}_time", timeOpt)
    }

    private val f2fBtn: Button = btn(
        { t("f2f_x", if (f2fOpt) t("on") else t("off")) },
        W / 2f, 526f, 320f, 52f, style = { if (f2fOpt) 2 else 1 }, visible = { supportsF2f && !vsAi }
    ) {
        f2fOpt = !f2fOpt
        store.putB("${key}_f2f", f2fOpt)
    }

    private val playBtn: Button =
        btn({ t("play") }, W / 2f, 604f, 280f, 60f, visible = { !hasSave }) { startGame() }
    private val continueBtn: Button =
        btn({ t("resume") }, 150f, 604f, 190f, 58f, visible = { hasSave }) { resumeGame() }
    private val startNewBtn: Button =
        btn({ t("new_game") }, 340f, 604f, 190f, 58f, style = { 1 }, visible = { hasSave }) { startGame() }

    protected val undoBtn: Button =
        btn({ t("undo") }, 90f, 684f, 140f, 46f, style = { 1 }, enabled = { canUndo() }) { doUndo() }
    protected val hintBtn: Button =
        btn({ t("hint") }, 240f, 684f, 140f, 46f, style = { 2 }, enabled = { canHint() }) { doHint() }
    protected val newBtn: Button = btn({ t("new_game") }, 390f, 684f, 140f, 46f, style = { 1 }) { startGame() }

    private val againBtn: Button = btn({ t("again") }, 150f, 452f, 150f, 50f) { startGame() }
    private val menuBtn: Button = btn({ t("menu") }, 330f, 452f, 150f, 50f, style = { 1 }) { phase = Phase.SETUP }

    private val setupAll: List<Button> by lazy { setupBtns + listOf(playBtn, continueBtn, startNewBtn, backBtn) }
    private val playAll: List<Button> by lazy { listOf(backBtn, undoBtn, hintBtn, newBtn) }
    private val overAll: List<Button> by lazy { listOf(againBtn, menuBtn, backBtn) }

    init {
        setupBtns.add(modeBtn)
        setupBtns.add(levelBtn)
        setupBtns.add(sideBtn)
        setupBtns.add(timeBtn)
        setupBtns.add(f2fBtn)
        relayout()
    }

    /** Game specific option button on the setup screen (call from the subclass init block). */
    protected fun addSetupButton(b: Button) {
        setupBtns.add(b)
        relayout()
    }

    private fun relayout() {
        var y = 262f
        for (b in setupBtns) {
            if (b.visible()) {
                b.cy = y
                y += 66f
            }
        }
    }

    override fun buttons(): List<Button> = when (phase) {
        Phase.SETUP -> setupAll
        Phase.PLAY -> playAll
        Phase.OVER -> overAll
    }

    // ------------------------------ to implement ----------------------------

    protected abstract fun sideName(side: Int): String
    protected abstract fun drawSetupArt(c: Canvas, cx: Float, cy: Float)

    /** Reset the board; the base class has already chosen [humanSide]. Call [maybeAiMove] at the end. */
    protected abstract fun onNewGame()
    protected abstract fun drawGame(c: Canvas)
    protected abstract fun statusText(): String
    protected abstract fun onUpdate()
    protected abstract fun canUndo(): Boolean
    protected abstract fun doUndo()
    protected abstract fun canHint(): Boolean
    protected abstract fun doHint()

    /** Serialises the running game (moves so far, ...) into a string, or null when it cannot be saved. */
    protected open fun saveState(): String? = null

    /**
     * Rebuilds a game from [saveState]'s string. The base class has restored vsAi, level, humanSide,
     * clocks and set the phase to PLAY; the subclass sets up the board and, when it is the computer's turn,
     * starts the computer (e.g. via its `maybeAiMove()`). Return false when the data is unusable.
     */
    protected open fun loadState(s: String): Boolean = false

    // ------------------------------- flow -----------------------------------

    protected fun levelKey(l: Int): String = when (l) {
        0 -> "lvl_easy"
        1 -> "lvl_med"
        else -> "lvl_hard"
    }

    protected fun startGame() {
        cancelAi()
        humanSide = if (!vsAi) 0 else if (sideOpt == 2) Random.nextInt(2) else sideOpt
        result = 0
        reasonKey = ""
        phase = Phase.PLAY
        overT = 0
        message = ""
        messageT = 0
        particles.clear()
        clearSave()
        val start = if (clockOn) TIME_MIN[timeOpt] * 60_000L else 0L
        clockMs[0] = start
        clockMs[1] = start
        clockSide = if (clockOn) 0 else -1
        onNewGame()
    }

    // ------------------------------ clocks ----------------------------------

    /** The clock of [side] runs from now on (the other one stops). */
    protected fun setClock(side: Int) {
        clockSide = if (clockOn) side else -1
    }

    protected fun stopClock() {
        clockSide = -1
    }

    // ------------------------- saving and continuing -------------------------

    private fun clearSave() {
        store.remove("${key}_save")
        hasSave = false
    }

    /** Stores the running game so that the setup screen can offer "Continue". */
    protected fun persist() {
        if (phase != Phase.PLAY || result != 0) return
        val body = try {
            saveState()
        } catch (e: Throwable) {
            null
        } ?: return
        val head = "v1|${if (vsAi) 1 else 0}|$level|$humanSide|$timeOpt|${clockMs[0]}|${clockMs[1]}|$clockSide|${if (f2fOpt) 1 else 0}"
        store.putS("${key}_save", head + "\n" + body)
        hasSave = true
    }

    private fun resumeGame() {
        val raw = store.getS("${key}_save")
        val nl = raw?.indexOf('\n') ?: -1
        if (raw == null || nl < 0) {
            clearSave()
            return
        }
        try {
            val h = raw.substring(0, nl).split('|')
            vsAi = h[1] == "1"
            level = h[2].toInt().coerceIn(0, 2)
            humanSide = h[3].toInt()
            timeOpt = h[4].toInt().coerceIn(0, 3)
            clockMs[0] = h[5].toLong()
            clockMs[1] = h[6].toLong()
            clockSide = h[7].toInt()
            f2fOpt = h[8] == "1"
        } catch (e: Exception) {
            clearSave()
            return
        }
        cancelAi()
        result = 0
        reasonKey = ""
        message = ""
        messageT = 0
        overT = 0
        particles.clear()
        phase = Phase.PLAY
        val ok = try {
            loadState(raw.substring(nl + 1))
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            clearSave()
            phase = Phase.SETUP
        }
    }

    override fun onHostPause() {
        persist()
        super.onHostPause()
    }

    protected fun finishGame(res: Int, reason: String) {
        cancelAi()
        stopClock()
        clearSave()
        result = res
        reasonKey = reason
        phase = Phase.OVER
        overT = 0
        val humanWon = vsAi && res == humanSide + 1
        if (vsAi) store.addStat(key, if (res == 3) 2 else if (humanWon) 0 else 1)
        Achievements.recordGame(store, humanWon)
        if (humanWon && level == 2) Achievements.unlock(store, "${key}_hard")
        sfx.play(if (res == 3) Sfx.Kind.DRAW else if (!vsAi || humanWon) Sfx.Kind.WIN else Sfx.Kind.LOSE)
        if (res != 3 && (!vsAi || humanWon)) confetti()
        haptic(true)
    }

    protected fun isHumanTurn(sideToMove: Int): Boolean = !vsAi || sideToMove == humanSide

    protected fun flashMessage(s: String) {
        message = s
        messageT = 110
    }

    /**
     * New stack size after "undo": removes the human's last move and everything after it
     * (two-player mode: just the last move). Returns -1 when there is nothing to undo.
     */
    protected fun undoSize(plyCount: Int): Int {
        if (!vsAi) return if (plyCount > 0) plyCount - 1 else -1
        var i = plyCount - 1
        while (i >= 0 && ((i % 2 == 0) != (humanSide == 0))) i--
        return i
    }

    // --------------------------------- AI -----------------------------------

    /** Runs [work] on a background thread, then calls [done] on the UI thread (if still relevant). */
    protected fun <T : Any> runAi(minMs: Long, work: () -> T?, done: (T) -> Unit) {
        val gen = ++aiGen
        thinking = true
        val t0 = System.currentTimeMillis()
        val worker = Thread {
            val r: T? = try {
                work()
            } catch (e: Throwable) {
                null
            }
            val spent = System.currentTimeMillis() - t0
            if (spent < minMs) {
                try {
                    Thread.sleep(minMs - spent)
                } catch (e: InterruptedException) {
                    // ignore
                }
            }
            post {
                if (gen == aiGen) {
                    thinking = false
                    if (r != null) done(r)
                }
            }
        }
        worker.isDaemon = true
        worker.start()
    }

    protected fun cancelAi() {
        aiGen++
        thinking = false
    }

    // ------------------------------- input ----------------------------------

    override fun onBack(): Boolean {
        if (fading) return true
        when (phase) {
            Phase.SETUP -> leaveTo(GameId.HUB)
            else -> {
                persist()
                cancelAi()
                phase = Phase.SETUP
            }
        }
        return true
    }

    // ------------------------------- update ---------------------------------

    override fun update() {
        if (messageT > 0) messageT--
        if (phase == Phase.OVER) overT++
        if (phase == Phase.PLAY && result == 0 && clockSide in 0..1) {
            clockMs[clockSide] -= 17L
            if (clockMs[clockSide] <= 0L) {
                val loser = clockSide
                clockMs[loser] = 0L
                finishGame(if (loser == 0) 2 else 1, "r_timeout")
            }
        }
        updateClouds(0.1f)
        if (phase != Phase.SETUP) onUpdate()
    }

    // ------------------------------- drawing --------------------------------

    override fun drawScreen(c: Canvas) {
        drawBackdrop(c)

        if (phase == Phase.SETUP) {
            drawSetup(c)
        } else {
            if (clockOn) drawClocks(c) else text(c, t(titleKey), W / 2f + 20f, 62f, 24f, Ui.TEXT, maxW = 280f, shadow = false)
            drawGame(c)
            drawStatus(c)
            if (phase == Phase.PLAY) {
                drawButton(c, undoBtn)
                drawButton(c, hintBtn)
                drawButton(c, newBtn)
            }
            drawButton(c, backBtn)
            if (phase == Phase.OVER) drawOver(c)
        }
        drawParticles(c)
    }

    private fun drawClocks(c: Canvas) {
        for (side in 0..1) {
            val cx = if (side == 0) 178f else 302f
            val running = clockSide == side && phase == Phase.PLAY
            val ms = clockMs[side]
            val low = ms < 20_000L
            fillRRect(c, cx - 56f, 40f, cx + 56f, 84f, 12f, if (running) mixC(Ui.SURFACE, accent, 0.18f) else Ui.SURFACE)
            strokeRRect(c, cx - 56f, 40f, cx + 56f, 84f, 12f, if (running) accent else Ui.BORDER, 1.5f)
            fillOval(c, cx - 46f, 54f, cx - 30f, 70f, if (side == 0) Color.rgb(246, 246, 240) else Color.rgb(44, 52, 72))
            strokeOval(c, cx - 46f, 54f, cx - 30f, 70f, withA(Ui.MUTED, 0.8f), 1.5f)
            val sec = ((ms + 999L) / 1000L).toInt()
            val txt = "%d:%02d".format(sec / 60, sec % 60)
            text(c, txt, cx + 12f, 70f, 20f, if (low) Ui.DANGER else Ui.TEXT, shadow = false)
        }
    }

    private fun drawSetup(c: Canvas) {
        drawTitle(c, t(titleKey), W / 2f, 118f, 44f, Ui.TEXT, 400f)
        drawSetupArt(c, W / 2f, 188f)
        for (b in setupBtns) drawButton(c, b)
        drawButton(c, playBtn)
        drawButton(c, backBtn)
        val w = store.stat(key, 0)
        val l = store.stat(key, 1)
        val d = store.stat(key, 2)
        plainText(c, t("stats_line", w, l, d), W / 2f, 666f, 14f, Ui.MUTED, 0, 440f)
    }

    private fun drawStatus(c: Canvas) {
        val s: String
        if (messageT > 0) {
            s = message
        } else if (thinking) {
            val dots = ".".repeat(1 + (tick / 14) % 3)
            s = t("thinking") + dots
        } else {
            s = statusText()
        }
        if (phase != Phase.OVER) text(c, s, W / 2f, 124f, 20f, Ui.TEXT, maxW = 440f, shadow = false)
    }

    private fun drawOver(c: Canvas) {
        val k = easeOutBack(clampF(overT / 22f, 0f, 1f)).coerceAtLeast(0f)
        c.save()
        c.translate(W / 2f, 395f)
        c.scale(k, k)
        c.translate(-W / 2f, -395f)
        drawPanel(c, 50f, 285f, 430f, 500f)
        val title = when {
            result == 3 -> t("draw_title")
            vsAi -> if (result == humanSide + 1) t("you_win") else t("you_lose")
            else -> t("side_wins", sideName(result - 1))
        }
        val col = when {
            result == 3 -> Color.rgb(176, 186, 206)
            !vsAi || result == humanSide + 1 -> Ui.OK
            else -> Ui.DANGER
        }
        text(c, title, W / 2f, 350f, 36f, col, maxW = 330f, shadow = false)
        if (reasonKey.isNotEmpty()) plainText(c, t(reasonKey), W / 2f, 390f, 16f, Ui.MUTED, 0, 330f)
        drawButton(c, againBtn)
        drawButton(c, menuBtn)
        c.restore()
    }
}
