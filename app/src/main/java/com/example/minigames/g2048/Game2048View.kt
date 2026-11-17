package com.example.minigames.g2048

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
import com.example.minigames.core.Achievements
import com.example.minigames.core.BaseView
import com.example.minigames.core.Button
import com.example.minigames.core.GameId
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.Ui
import com.example.minigames.core.W
import com.example.minigames.core.clampF
import com.example.minigames.core.easeOutBack
import com.example.minigames.core.easeOutCubic
import com.example.minigames.core.lerp
import com.example.minigames.core.t
import com.example.minigames.core.withA
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

// board geometry (logical coordinates)
private const val TILE = 90f
private const val GAP = 10f
private const val BOARD = 4f * TILE + 5f * GAP   // 410
private const val BX = (480f - BOARD) / 2f       // 35
private const val BY = 150f

private const val SLIDE = 7        // frames of the sliding animation
private const val POP = 14         // frames of the merge pulse / new tile appearance after the slide
private const val PI_F = 3.1415927f

private val TILE_COLORS = intArrayOf(
    Color.rgb(84, 98, 132),     // 2
    Color.rgb(70, 112, 152),    // 4
    Color.rgb(58, 140, 162),    // 8
    Color.rgb(56, 160, 140),    // 16
    Color.rgb(92, 172, 100),    // 32
    Color.rgb(166, 176, 70),    // 64
    Color.rgb(238, 196, 70),    // 128
    Color.rgb(242, 166, 56),    // 256
    Color.rgb(242, 134, 50),    // 512
    Color.rgb(234, 98, 70),     // 1024
    Color.rgb(255, 214, 92),    // 2048
    Color.rgb(150, 110, 230)    // 4096 and more
)

private enum class Phase { PLAY, WIN, OVER }

/**
 * 2048: swipe to slide the tiles, equal tiles join. Opens straight into the game; the running game is saved
 * after every move (key g48_save) and restored next time. Records: g48_best (score), g48_besttile.
 */
class Game2048View(context: Context, host: Host) : BaseView(context, host) {

    override val accent: Int get() = G2048Icon.ACCENT

    private var game = Game2048()
    private var phase = Phase.PLAY
    private var best = 0
    private var startBest = 0          // best score when the current game began (to show "new best")
    private var bestTile = 0
    private var statGames = 0

    private var step: G48Step? = null  // the last move, for the animation
    private var animT = 99             // frames since that move started
    private var overT = 0              // frames since the win / game-over panel was requested (negative = wait)
    private var confirmNew = 0         // frames left in which the NEW button asks for confirmation
    private var floatV = 0             // "+n" that floats up next to the score
    private var floatT = 99

    private var downX = 0f
    private var downY = 0f
    private var swiped = false

    private val labels = HashMap<Int, String>()

    // ------------------------------- buttons --------------------------------

    private val backBtn: Button = btn({ "<" }, 50f, 52f, 64f, 46f, style = { 1 }) { onBack() }

    private val undoBtn: Button = btn(
        { t("undo") }, 130f, 610f, 180f, 52f, style = { 1 }, enabled = { game.canUndo() }
    ) { doUndo() }

    private val newBtn: Button = btn(
        { if (confirmNew > 0) t("g48_sure") else t("new_game") }, 350f, 610f, 180f, 52f,
        style = { if (confirmNew > 0) 3 else 1 }
    ) { onNewTap() }

    private val ovMainBtn: Button = btn(
        { if (phase == Phase.WIN) t("g48_keep") else t("again") }, W / 2f, 392f, 240f, 50f
    ) { onOverlayMain() }

    private val ovSecondBtn: Button = btn(
        { if (phase == Phase.WIN) t("new_game") else t("undo") }, W / 2f, 452f, 240f, 50f,
        style = { 1 }, enabled = { phase == Phase.WIN || game.canUndo() }
    ) { onOverlaySecond() }

    init {
        loadSaved()
    }

    override fun buttons(): List<Button> = when {
        phase == Phase.PLAY -> listOf(backBtn, undoBtn, newBtn)
        overT < 14 -> listOf(backBtn)
        else -> listOf(backBtn, ovMainBtn, ovSecondBtn)
    }

    // ------------------------------- game flow ------------------------------

    private fun loadSaved() {
        best = store.getI("g48_best", 0)
        bestTile = store.getI("g48_besttile", 0)
        refreshStats()
        val g = Game2048.deserialize(store.getS("g48_save"))
        if (g != null && !g.isOver()) {
            game = g
            startBest = store.getI("g48_sbest", best)
            if (g.maxTile() >= 2048 && !g.keepGoing) {
                phase = Phase.WIN
                overT = 0
            }
        } else {
            newGame(false)
        }
    }

    private fun refreshStats() {
        statGames = store.stat("g2048", 0) + store.stat("g2048", 1)
    }

    private fun newGame(save: Boolean) {
        val g = Game2048()
        g.reset()
        game = g
        phase = Phase.PLAY
        startBest = best
        store.putI("g48_sbest", best)
        step = null
        animT = 99
        overT = 0
        confirmNew = 0
        floatT = 99
        particles.clear()
        if (save) saveNow()
    }

    private fun saveNow() {
        if (phase == Phase.OVER) store.remove("g48_save") else store.putS("g48_save", game.serialize())
    }

    private fun onNewTap() {
        if (game.moves == 0 || confirmNew > 0) {
            newGame(true)
        } else {
            confirmNew = 150
        }
    }

    private fun onOverlayMain() {
        if (phase == Phase.WIN) {
            game.keepGoing = true
            phase = Phase.PLAY
            if (game.isOver()) finishOver() else saveNow()
        } else {
            newGame(true)
        }
    }

    private fun onOverlaySecond() {
        if (phase == Phase.WIN) newGame(true) else doUndo()
    }

    private fun doUndo() {
        if (fading || !game.undo()) return
        phase = Phase.PLAY
        step = null
        animT = 99
        confirmNew = 0
        saveNow()
    }

    private fun doMove(dir: Int) {
        if (phase != Phase.PLAY || fading) return
        val s = game.move(dir)
        if (s == null) {
            shake = 2f
            return
        }
        step = s
        animT = 0
        confirmNew = 0
        if (s.gained > 0) {
            floatV = s.gained
            floatT = 0
        }
        if (game.score > best) {
            best = game.score
            store.putI("g48_best", best)
        }
        val mt = game.maxTile()
        if (mt > bestTile) {
            bestTile = mt
            store.putI("g48_besttile", mt)
        }
        if (mt >= 1024) Achievements.unlock(store, "g2048_1024")
        if (mt >= 2048) Achievements.unlock(store, "g2048_2048")

        sfx.play(if (s.biggest > 0) Sfx.Kind.POP else Sfx.Kind.MOVE, 0.5f)
        for (idx in s.merged) {
            val v = game.cells[idx]
            if (v >= 64) {
                val lv = 31 - v.countLeadingZeroBits()
                emit(tileCx(idx % 4), tileCy(idx / 4), 4 + lv, 1f, 3.2f, 24, 3f, 6f, 0.06f, tileColor(v))
            }
        }

        if (mt >= 2048 && !game.keepGoing) {
            phase = Phase.WIN
            overT = -24
            if (!game.recorded) {
                game.recorded = true
                store.addStat("g2048", 0)
                Achievements.recordGame(store, true)
                refreshStats()
            }
            sfx.play(Sfx.Kind.WIN)
            confetti()
            haptic(true)
        } else if (game.isOver()) {
            finishOver()
        }
        saveNow()
    }

    private fun finishOver() {
        phase = Phase.OVER
        overT = -24
        if (!game.recorded) {
            game.recorded = true
            store.addStat("g2048", 1)
            Achievements.recordGame(store, false)
            refreshStats()
        }
        sfx.play(Sfx.Kind.LOSE)
        haptic(true)
        saveNow()
    }

    // ------------------------------- input ----------------------------------

    override fun onKey(keyCode: Int): Boolean {
        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Game2048.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Game2048.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> Game2048.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Game2048.DOWN
            else -> return false
        }
        doMove(dir)
        return true
    }

    override fun onPointerDown(x: Float, y: Float) {
        downX = x
        downY = y
        swiped = false
    }

    override fun onPointerMove(x: Float, y: Float) {
        trySwipe(x, y, 26f)
    }

    override fun onPointerUp(x: Float, y: Float) {
        trySwipe(x, y, 16f)
    }

    private fun trySwipe(x: Float, y: Float, minDist: Float) {
        if (x < -900f) return            // the gesture was cancelled
        if (swiped || phase != Phase.PLAY) return
        val dx = x - downX
        val dy = y - downY
        if (abs(dx) < minDist && abs(dy) < minDist) return
        swiped = true
        val dir = if (abs(dx) > abs(dy)) {
            if (dx < 0f) Game2048.LEFT else Game2048.RIGHT
        } else {
            if (dy < 0f) Game2048.UP else Game2048.DOWN
        }
        doMove(dir)
    }

    override fun onBack(): Boolean {
        if (fading) return true
        saveNow()
        leaveTo(GameId.HUB)
        return true
    }

    override fun onHostPause() {
        saveNow()
        super.onHostPause()
    }

    // ------------------------------- update ---------------------------------

    override fun update() {
        if (animT < 200) animT++
        if (floatT < 60) floatT++
        if (confirmNew > 0) confirmNew--
        if (phase != Phase.PLAY && overT < 1000) overT++
    }

    // ------------------------------- drawing --------------------------------

    private fun tileCx(col: Int): Float = BX + GAP + col * (TILE + GAP) + TILE / 2f
    private fun tileCy(row: Int): Float = BY + GAP + row * (TILE + GAP) + TILE / 2f

    private fun tileColor(v: Int): Int {
        val lv = 31 - v.countLeadingZeroBits()       // 2 -> 1
        return TILE_COLORS[min(lv - 1, TILE_COLORS.size - 1).coerceAtLeast(0)]
    }

    private fun tileInk(v: Int): Int {
        val lv = 31 - v.countLeadingZeroBits()
        return if (lv in 4..9 || lv == 11) Ui.INK else Color.WHITE
    }

    override fun drawScreen(c: Canvas) {
        drawBackdrop(c)
        drawHeader(c)
        drawBoard(c)
        if (phase == Phase.PLAY) {
            drawButton(c, undoBtn)
            drawButton(c, newBtn)
        }
        plainText(c, t("g48_stats", statGames, bestTile), W / 2f, 672f, 14f, Ui.MUTED, 0, 440f)
        drawButton(c, backBtn)
        if (phase != Phase.PLAY && overT > 0) drawOverlay(c)
        drawParticles(c)
    }

    private fun drawHeader(c: Canvas) {
        text(c, t("g48_title"), 150f, 66f, 34f, accent, maxW = 100f, shadow = false)
        scoreBox(c, 206f, 328f, t("score"), game.score)
        scoreBox(c, 338f, 460f, t("best"), best)
        if (floatT < 40) {
            val a = 1f - floatT / 40f
            plainText(c, "+" + floatV, 267f, 112f - floatT * 0.5f, 16f, withA(accent, a), 0, 110f)
        }
        val info = if (game.moves == 0) t("g48_hint") else t("g48_moves", game.moves)
        plainText(c, info, W / 2f, 130f, 15f, Ui.MUTED, 0, 440f)
    }

    private fun scoreBox(c: Canvas, l: Float, r: Float, label: String, value: Int) {
        fillRRect(c, l, 34f, r, 86f, 12f, Ui.SURFACE)
        strokeRRect(c, l, 34f, r, 86f, 12f, Ui.BORDER, 1.5f)
        val mid = (l + r) / 2f
        plainText(c, label, mid, 53f, 11f, Ui.MUTED, 0, r - l - 14f)
        text(c, value.toString(), mid, 77f, 22f, Ui.TEXT, maxW = r - l - 16f, shadow = false)
    }

    private fun drawBoard(c: Canvas) {
        fillRRect(c, BX, BY, BX + BOARD, BY + BOARD, 18f, Ui.SURFACE)
        strokeRRect(c, BX, BY, BX + BOARD, BY + BOARD, 18f, Ui.BORDER, 1.5f)
        for (i in 0 until Game2048.CELLS) {
            val l = tileCx(i % 4) - TILE / 2f
            val tp = tileCy(i / 4) - TILE / 2f
            fillRRect(c, l, tp, l + TILE, tp + TILE, 12f, Ui.SURFACE_HI)
        }

        val st = step
        if (st != null && animT < SLIDE) {
            // sliding: every tile of the move travels from its old cell to its new one (values before merging)
            val k = easeOutCubic(animT / SLIDE.toFloat())
            for (m in st.moves) {
                val x = lerp(tileCx(m.from % 4), tileCx(m.to % 4), k)
                val y = lerp(tileCy(m.from / 4), tileCy(m.to / 4), k)
                drawTile(c, x, y, m.value, 1f)
            }
            return
        }

        val post = animT - SLIDE
        for (i in 0 until Game2048.CELLS) {
            val v = game.cells[i]
            if (v == 0) continue
            var sc = 1f
            if (st != null && post < POP) {
                if (i == st.spawnIdx) {
                    if (post < 1) continue
                    sc = easeOutBack(clampF((post - 1) / 8f, 0f, 1f))
                    if (sc < 0.02f) continue
                } else if (isMerged(st, i)) {
                    sc = 1f + 0.16f * sin(PI_F * clampF(post / 9f, 0f, 1f))
                }
            }
            drawTile(c, tileCx(i % 4), tileCy(i / 4), v, sc)
        }
    }

    private fun isMerged(st: G48Step, idx: Int): Boolean {
        for (m in st.merged) if (m == idx) return true
        return false
    }

    private fun drawTile(c: Canvas, cx: Float, cy: Float, v: Int, sc: Float) {
        val h = TILE / 2f * sc
        val rad = 12f * sc
        fillRRect(c, cx - h, cy - h, cx + h, cy + h, rad, tileColor(v))
        if (v >= 2048) strokeRRect(c, cx - h, cy - h, cx + h, cy + h, rad, Color.rgb(255, 246, 210), 2f)
        val label = labels.getOrPut(v) { v.toString() }
        val base = when {
            label.length <= 2 -> 38f
            label.length == 3 -> 32f
            label.length == 4 -> 26f
            else -> 21f
        }
        val size = base * sc
        text(c, label, cx, cy + size * 0.36f, size, tileInk(v), maxW = (TILE - 14f) * sc, shadow = false)
    }

    private fun drawOverlay(c: Canvas) {
        val a = clampF(overT / 14f, 0f, 1f)
        fillRRect(c, BX, BY, BX + BOARD, BY + BOARD, 18f, withA(Ui.BG_BOTTOM, 0.8f * a))
        if (a < 1f) return
        drawPanel(c, 60f, 222f, 420f, 490f)
        if (phase == Phase.WIN) {
            text(c, t("g48_win"), W / 2f, 282f, 32f, accent, maxW = 320f, shadow = false)
        } else {
            text(c, t("game_over"), W / 2f, 282f, 32f, Ui.TEXT, maxW = 320f, shadow = false)
        }
        plainText(c, t("score") + ": " + game.score, W / 2f, 316f, 17f, Ui.MUTED, 0, 320f)
        if (game.score > startBest && game.score > 0) {
            plainText(c, t("new_badge") + " " + t("best"), W / 2f, 344f, 15f, Ui.OK, 0, 320f)
        }
        drawButton(c, ovMainBtn)
        drawButton(c, ovSecondBtn)
    }
}
