package com.example.minigames.battleship

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
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private enum class Phase { SETUP, PLACE, BATTLE }

// geometry (logical 480x720)
private const val ECS = 36f          // enemy sea (battle) and own sea (placing): cell size
private const val EL = 60f           // left edge of the big sea
private const val ET_BATTLE = 108f   // top edge in battle
private const val ET_PLACE = 124f    // top edge while placing the fleet
private const val MCS = 16f          // own sea in battle: small copy
private const val ML = 24f
private const val MT = 512f

private const val ANIM = 26

/**
 * Battleship against the computer: 10x10, ships 5-4-3-3-2. SETUP (level, continue) -> PLACE (a random fleet is
 * shown at once; drag a ship to move it, tap it to turn it, RANDOM shuffles) -> BATTLE (aim with the finger, lift to
 * fire; a hit or a sunk ship lets you shoot again). The running battle is saved after every shot (key bs_save).
 * Storage: bs_lvl, bs_best_0..2 (fewest shots in a win), stat_bs_0 / stat_bs_1 (won / lost).
 */
class BattleshipView(context: Context, host: Host) : BaseView(context, host) {

    override val accent: Int get() = BattleshipIcon.ACCENT

    private var phase = Phase.SETUP
    private var game: BsGame? = null
    private var saved: BsGame? = null
    private var hasSave = false
    private var level = store.getI("bs_lvl", 1).coerceIn(0, 2)

    // placing
    private var dragIdx = -1
    private var dragMoved = false
    private var grabDr = 0
    private var grabDc = 0
    private var startCell = -1
    private var dragR = 0
    private var dragC = 0
    private var dragOk = false

    // battle
    private var aim = -1
    private var aiWait = 0
    private var overT = 0
    private var winRecord = false
    private var winPerfect = false
    private var msgKey: String? = null
    private var msgColor = 0
    private var msgT = 0
    private val animFoe = IntArray(BsEngine.CELLS)
    private val animMe = IntArray(BsEngine.CELLS)

    // ------------------------------- buttons --------------------------------

    private val backBtn: Button = btn({ "<" }, 50f, 52f, 64f, 46f, style = { 1 }) { onBack() }

    private val levelBtns: List<Button> = List(3) { i ->
        btn({ t(LEVEL_KEYS[i]) }, 90f + i * 150f, 372f, 136f, 52f, style = { if (level == i) 2 else 1 }) { setLevel(i) }
    }
    private val playBtn: Button = btn({ t("play") }, W / 2f, 548f, 240f, 56f, visible = { !hasSave }) { startPlacing() }
    private val resumeBtn: Button = btn({ t("resume") }, W / 2f, 548f, 240f, 56f, visible = { hasSave }) { resumeSaved() }
    private val newBtn: Button = btn({ t("new_game") }, W / 2f, 612f, 240f, 48f, style = { 1 }, visible = { hasSave }) {
        startPlacing()
    }

    private val randomBtn: Button = btn({ t("random") }, W / 2f, 546f, 300f, 50f, style = { 1 }) { shuffleFleet() }
    private val startBtn: Button = btn({ t("bs_start") }, W / 2f, 622f, 300f, 56f) { startBattle() }

    private val ovAgainBtn: Button = btn({ t("again") }, 330f, 604f, 232f, 48f) { startPlacing() }
    private val ovMenuBtn: Button = btn({ t("menu") }, 330f, 662f, 232f, 48f, style = { 1 }) { toSetup() }

    init {
        refreshSave()
    }

    private fun over(): Boolean = game?.status != BsEngine.PLAYING

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
        Phase.PLACE -> listOf(backBtn, randomBtn, startBtn)
        Phase.BATTLE ->
            if (!over() || overT < 20) listOf(backBtn)
            else listOf(backBtn, ovAgainBtn, ovMenuBtn)
    }

    // ------------------------------- game flow ------------------------------

    private fun refreshSave() {
        val s = store.getS("bs_save")
        var g: BsGame? = null
        if (s != null) {
            g = BsGame.deserialize(s)
            if (g == null) store.remove("bs_save")
        }
        saved = g
        hasSave = g != null
    }

    private fun setLevel(i: Int) {
        level = i
        store.putI("bs_lvl", i)
    }

    private fun startPlacing() {
        if (fading) return
        val g = BsGame(level)
        g.me.randomize(Random.Default)
        game = g
        phase = Phase.PLACE
        dragIdx = -1
    }

    private fun shuffleFleet() {
        val g = game ?: return
        g.me.randomize(Random.Default)
        dragIdx = -1
        sfx.play(Sfx.Kind.PLACE, 0.6f)
    }

    private fun resetBattleState() {
        aim = -1
        overT = 0
        winRecord = false
        winPerfect = false
        msgKey = null
        msgT = 0
        animFoe.fill(0)
        animMe.fill(0)
        particles.clear()
    }

    private fun startBattle() {
        val g = game ?: return
        if (fading || !g.begin(Random.Default)) return
        resetBattleState()
        phase = Phase.BATTLE
        saveNow()
    }

    private fun resumeSaved() {
        val g = saved ?: return
        if (fading) return
        level = g.level
        game = g
        resetBattleState()
        phase = Phase.BATTLE
        aiWait = 40
    }

    private fun toSetup() {
        phase = Phase.SETUP
        game = null
        dragIdx = -1
        aim = -1
        refreshSave()
    }

    private fun saveNow() {
        val g = game ?: return
        if (phase == Phase.BATTLE && g.status == BsEngine.PLAYING) store.putS("bs_save", g.serialize())
    }

    // ------------------------------- geometry -------------------------------

    private fun cellAt(x: Float, y: Float, l: Float, tp: Float, cs: Float, clampIn: Boolean): Int {
        var col = Math.floor(((x - l) / cs).toDouble()).toInt()
        var row = Math.floor(((y - tp) / cs).toDouble()).toInt()
        if (clampIn) {
            col = col.coerceIn(0, BsEngine.N - 1)
            row = row.coerceIn(0, BsEngine.N - 1)
        } else if (col < 0 || row < 0 || col >= BsEngine.N || row >= BsEngine.N) return -1
        return row * BsEngine.N + col
    }

    private fun seaTop(): Float = if (phase == Phase.PLACE) ET_PLACE else ET_BATTLE

    private fun bigCenterX(i: Int): Float = EL + (i % BsEngine.N + 0.5f) * ECS
    private fun bigCenterY(i: Int): Float = seaTop() + (i / BsEngine.N + 0.5f) * ECS
    private fun miniCenterX(i: Int): Float = ML + (i % BsEngine.N + 0.5f) * MCS
    private fun miniCenterY(i: Int): Float = MT + (i / BsEngine.N + 0.5f) * MCS

    // ------------------------------- input ----------------------------------

    override fun onPointerDown(x: Float, y: Float) {
        val g = game ?: return
        when (phase) {
            Phase.PLACE -> {
                val cell = cellAt(x, y, EL, ET_PLACE, ECS, false)
                if (cell < 0) return
                val idx = g.me.owner[cell]
                if (idx < 0) return
                val s = g.me.ships[idx]
                dragIdx = idx
                dragMoved = false
                startCell = cell
                grabDr = cell / BsEngine.N - s.r
                grabDc = cell % BsEngine.N - s.c
                dragR = s.r
                dragC = s.c
                dragOk = true
            }
            Phase.BATTLE -> {
                if (g.status != BsEngine.PLAYING || g.turn != 0 || fading) return
                setAim(g, cellAt(x, y, EL, ET_BATTLE, ECS, false))
            }
            else -> {}
        }
    }

    private fun setAim(g: BsGame, cell: Int) {
        aim = if (cell >= 0 && !g.foe.shot[cell]) cell else -1
    }

    override fun onPointerMove(x: Float, y: Float) {
        val g = game ?: return
        when (phase) {
            Phase.PLACE -> {
                if (dragIdx < 0) return
                val cell = cellAt(x, y, EL, ET_PLACE, ECS, true)
                if (cell != startCell) dragMoved = true
                if (!dragMoved) return
                val s = g.me.ships[dragIdx]
                val maxR = if (s.horiz) BsEngine.N - 1 else BsEngine.N - s.len
                val maxC = if (s.horiz) BsEngine.N - s.len else BsEngine.N - 1
                dragR = (cell / BsEngine.N - grabDr).coerceIn(0, maxR)
                dragC = (cell % BsEngine.N - grabDc).coerceIn(0, maxC)
                dragOk = g.me.fits(dragIdx, dragR, dragC, s.horiz)
            }
            Phase.BATTLE -> {
                if (g.status != BsEngine.PLAYING || g.turn != 0) return
                setAim(g, cellAt(x, y, EL, ET_BATTLE, ECS, false))
            }
            else -> {}
        }
    }

    override fun onPointerUp(x: Float, y: Float) {
        val g = game ?: return
        val cancelled = x < -500f
        when (phase) {
            Phase.PLACE -> {
                val idx = dragIdx
                dragIdx = -1
                if (idx < 0 || cancelled) return
                val s = g.me.ships[idx]
                if (dragMoved) {
                    if (dragOk && g.me.place(idx, dragR, dragC, s.horiz)) {
                        sfx.play(Sfx.Kind.PLACE, 0.6f)
                        haptic()
                    } else shake = 2f
                } else rotateShip(g, idx)
            }
            Phase.BATTLE -> {
                val a = aim
                aim = -1
                if (cancelled || a < 0) return
                if (g.status != BsEngine.PLAYING || g.turn != 0 || fading) return
                if (cellAt(x, y, EL, ET_BATTLE, ECS, false) != a) return
                fire(g, a)
            }
            else -> {}
        }
    }

    /** A tap on a ship turns it: around the touched cell if it fits, else around its first cell. */
    private fun rotateShip(g: BsGame, idx: Int) {
        val s = g.me.ships[idx]
        val nh = !s.horiz
        val cr = startCell / BsEngine.N
        val cc = startCell % BsEngine.N
        val k = if (s.horiz) grabDc else grabDr
        val tries = arrayOf(
            if (nh) intArrayOf(cr, cc - k) else intArrayOf(cr - k, cc),
            intArrayOf(s.r, s.c)
        )
        for (p in tries) {
            if (g.me.fits(idx, p[0], p[1], nh) && g.me.place(idx, p[0], p[1], nh)) {
                sfx.play(Sfx.Kind.PLACE, 0.6f)
                return
            }
        }
        shake = 2f
    }

    override fun onBack(): Boolean {
        if (fading) return true
        when (phase) {
            Phase.SETUP -> leaveTo(GameId.HUB)
            Phase.PLACE -> toSetup()
            Phase.BATTLE -> {
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

    // ------------------------------- shooting -------------------------------

    private fun say(key: String, color: Int) {
        msgKey = key
        msgColor = color
        msgT = 75
    }

    private fun fire(g: BsGame, cell: Int) {
        val res = g.playerShoot(cell)
        if (res == BsEngine.NOOP) return
        animFoe[cell] = ANIM
        val x = bigCenterX(cell)
        val y = bigCenterY(cell)
        when (res) {
            BsEngine.MISS -> {
                sfx.play(Sfx.Kind.PLACE, 0.6f)
                emit(x, y, 8, 0.6f, 1.8f, 24, 3f, 5f, 0.02f, withA(Color.WHITE, 0.9f), accent)
                say("bs_you_miss", Ui.MUTED)
            }
            BsEngine.HIT -> {
                sfx.play(Sfx.Kind.HIT, 0.8f)
                haptic()
                emit(x, y, 14, 1f, 3.2f, 30, 4f, 8f, 0.05f, Ui.DANGER, Color.rgb(255, 170, 70))
                say("bs_you_hit", Ui.OK)
            }
            else -> {
                sfx.play(Sfx.Kind.CAPTURE, 0.9f)
                haptic(true)
                shake = 3f
                emit(x, y, 26, 1.2f, 4.2f, 38, 4f, 9f, 0.05f, Ui.DANGER, Color.rgb(255, 170, 70), Color.rgb(255, 215, 110))
                say("bs_you_sunk", Ui.OK)
            }
        }
        if (g.status == BsEngine.WON) {
            win(g)
            return
        }
        if (g.turn == 1) aiWait = 46
        saveNow()
    }

    private fun aiMove(g: BsGame) {
        val cell = g.aiChoice(Random.Default)
        val res = g.aiShoot(cell)
        if (res == BsEngine.NOOP) {
            aiWait = 10
            return
        }
        animMe[cell] = ANIM
        val x = miniCenterX(cell)
        val y = miniCenterY(cell)
        when (res) {
            BsEngine.MISS -> {
                sfx.play(Sfx.Kind.PLACE, 0.4f)
                emit(x, y, 5, 0.4f, 1.2f, 20, 2f, 3.5f, 0.02f, withA(Color.WHITE, 0.9f), accent)
                say("bs_en_miss", Ui.MUTED)
            }
            BsEngine.HIT -> {
                sfx.play(Sfx.Kind.HIT, 0.8f)
                haptic()
                emit(x, y, 10, 0.8f, 2.4f, 28, 3f, 6f, 0.04f, Ui.DANGER, Color.rgb(255, 170, 70))
                say("bs_en_hit", Ui.DANGER)
            }
            else -> {
                sfx.play(Sfx.Kind.CAPTURE, 0.9f)
                haptic(true)
                shake = 3f
                emit(x, y, 18, 1f, 3.2f, 34, 3f, 7f, 0.04f, Ui.DANGER, Color.rgb(255, 170, 70))
                say("bs_en_sunk", Ui.DANGER)
            }
        }
        if (g.status == BsEngine.LOST) {
            lose()
            return
        }
        aiWait = if (g.turn == 1) 34 else 0
        saveNow()
    }

    private fun win(g: BsGame) {
        overT = 0
        val shots = g.foe.shots
        val key = "bs_best_" + g.level
        val old = store.getI(key, 0)
        winRecord = old <= 0 || shots < old
        if (winRecord) store.putI(key, shots)
        winPerfect = g.me.sunkCount == 0
        store.addStat("bs", 0)
        Achievements.recordGame(store, true)
        if (g.level == 2) Achievements.unlock(store, "bs_hard")
        if (winPerfect) Achievements.unlock(store, "bs_perfect")
        store.remove("bs_save")
        sfx.play(Sfx.Kind.WIN)
        confetti()
        haptic(true)
    }

    private fun lose() {
        overT = 0
        store.addStat("bs", 1)
        Achievements.recordGame(store, false)
        store.remove("bs_save")
        sfx.play(Sfx.Kind.LOSE)
    }

    // ------------------------------- update ---------------------------------

    override fun update() {
        val g = game
        if (phase != Phase.BATTLE || g == null) return
        for (i in 0 until BsEngine.CELLS) {
            if (animFoe[i] > 0) animFoe[i]--
            if (animMe[i] > 0) animMe[i]--
        }
        if (msgT > 0) msgT--
        if (g.status == BsEngine.PLAYING) {
            if (g.turn == 1 && !fading) {
                aiWait--
                if (aiWait <= 0) aiMove(g)
            }
        } else if (overT < 1000) {
            overT++
        }
    }

    // ------------------------------- drawing --------------------------------

    private fun shipColor(): Int = mixC(Ui.TEXT, Ui.SURFACE, if (Ui.dark) 0.34f else 0.40f)
    private fun waterColor(): Int = mixC(Ui.SURFACE, accent, if (Ui.dark) 0.13f else 0.2f)

    override fun drawScreen(c: Canvas) {
        drawBackdrop(c)
        when (phase) {
            Phase.SETUP -> drawSetup(c)
            Phase.PLACE -> drawPlace(c)
            Phase.BATTLE -> drawBattle(c)
        }
        drawButton(c, backBtn)
        drawParticles(c)
    }

    private fun drawSetup(c: Canvas) {
        c.save()
        c.translate(W / 2f, 170f)
        c.scale(1.7f, 1.7f)
        BattleshipIcon.draw(c, 0f, 0f, accent)
        c.restore()
        drawTitle(c, t("bs_title"), W / 2f, 288f, 40f, Ui.TEXT, 380f)

        for (b in levelBtns) drawButton(c, b)

        plainText(c, t("bs_rules"), W / 2f, 436f, 14f, Ui.TEXT, 0, 440f)
        val best = store.getI("bs_best_$level", 0)
        plainText(c, if (best > 0) t("bs_best", best) else "—", W / 2f, 458f, 16f, Ui.MUTED, 0, 420f)
        plainText(c, t("bs_won_n", store.stat("bs", 0)), W / 2f, 482f, 16f, Ui.MUTED, 0, 420f)

        val s = saved
        if (s != null) {
            plainText(c, t("bs_saved", t(LEVEL_KEYS[s.level]), s.foe.shots), W / 2f, 510f, 16f, accent, 0, 420f)
        }
        drawButton(c, playBtn)
        drawButton(c, resumeBtn)
        drawButton(c, newBtn)
    }

    private fun drawPlace(c: Canvas) {
        val g = game ?: return
        text(c, t("bs_place_title"), W / 2f, 72f, 22f, Ui.TEXT, maxW = 300f, shadow = false)
        plainText(c, t("bs_place_hint"), W / 2f, 102f, 13f, Ui.MUTED, 0, 440f)
        drawSea(c, g.me, EL, ET_PLACE, ECS, true, false, null, -1, -1, if (dragMoved) dragIdx else -1)
        if (dragIdx >= 0 && dragMoved) {
            val s = g.me.ships[dragIdx]
            val col = if (dragOk) Ui.OK else Ui.DANGER
            drawHull(c, s.len, s.horiz, dragR, dragC, EL, ET_PLACE, ECS, withA(col, 0.55f), col)
        }
        drawButton(c, randomBtn)
        drawButton(c, startBtn)
    }

    private fun drawBattle(c: Canvas) {
        val g = game ?: return
        val lost = g.status == BsEngine.LOST
        val won = g.status == BsEngine.WON

        // header: result, a fresh message, or whose turn it is
        val head: String
        val headColor: Int
        if (won) {
            head = t("you_win")
            headColor = Ui.OK
        } else if (lost) {
            head = t("you_lose")
            headColor = Ui.DANGER
        } else if (msgT > 0 && msgKey != null) {
            head = t(msgKey!!)
            headColor = msgColor
        } else if (g.turn == 0) {
            head = t("bs_your_shot")
            headColor = Ui.TEXT
        } else {
            head = t("bs_enemy_shot")
            headColor = Ui.MUTED
        }
        text(c, head, W / 2f, 72f, 21f, headColor, maxW = 300f, shadow = false)

        drawSea(c, g.foe, EL, ET_BATTLE, ECS, false, lost, animFoe, g.lastMine, if (g.turn == 0 && !over()) aim else -1, -1)

        // own sea, small
        plainText(c, t("bs_your_fleet"), ML, 502f, 12f, Ui.MUTED, -1, 160f)
        drawSea(c, g.me, ML, MT, MCS, true, false, animMe, g.lastFoe, -1, -1)

        if (!over()) {
            val x0 = 208f
            plainText(c, t("bs_enemy_fleet"), x0, 524f, 12f, Ui.MUTED, -1, 240f)
            drawChips(c, x0, 534f, g.foe, false)
            plainText(c, t("bs_shots", g.foe.shots, g.foe.hitCount), x0, 596f, 14f, Ui.TEXT, -1, 250f)
        } else {
            val cx = 330f
            plainText(c, t("bs_shots", g.foe.shots, g.foe.hitCount), cx, 528f, 15f, Ui.TEXT, 0, 226f)
            if (won && winRecord) plainText(c, t("bs_record"), cx, 552f, 14f, Ui.OK, 0, 250f)
            if (won && winPerfect) plainText(c, t("bs_perfect"), cx, if (winRecord) 574f else 552f, 13f, Ui.OK, 0, 250f)
            if (overT >= 20) {
                drawButton(c, ovAgainBtn)
                drawButton(c, ovMenuBtn)
            }
        }
    }

    /** Small ship silhouettes of one fleet: afloat / hit segments (own fleet) / sunk. */
    private fun drawChips(c: Canvas, x0: Float, y: Float, b: BsBoard, mine: Boolean) {
        var x = x0
        val seg = 12f
        for (idx in b.ships.indices) {
            val s = b.ships[idx]
            val sunk = b.isSunk(idx)
            val w = s.len * seg - 1f
            fillRRect(c, x, y, x + w, y + 12f, 5f, if (sunk) withA(Ui.DANGER, 0.3f) else shipColor())
            if (mine && !sunk) {
                for (k in 0 until s.len) {
                    if (b.shot[s.cell(k)]) fillRRect(c, x + k * seg + 1f, y + 1f, x + k * seg + seg - 2f, y + 11f, 3f, Ui.DANGER)
                }
            }
            if (sunk) {
                line.color = Ui.DANGER
                line.strokeWidth = 2f
                c.drawLine(x + 2f, y + 6f, x + w - 2f, y + 6f, line)
            }
            x += s.len * seg + 10f
        }
    }

    private val hullPath = android.graphics.Path()

    /** A ship seen from above: pointed bow, lighter deck, cabin, funnel and guns depending on its length. */
    private fun drawHull(
        c: Canvas, len: Int, horiz: Boolean, r: Int, col: Int, l: Float, tp: Float, cs: Float,
        fillC: Int, strokeC: Int
    ) {
        val inset = max(1.2f, cs * 0.1f)
        val x0 = l + col * cs + inset
        val y0 = tp + r * cs + inset
        val w = (if (horiz) len * cs else cs) - 2f * inset
        val h = (if (horiz) cs else len * cs) - 2f * inset
        val ln = if (horiz) w else h          // length of the ship
        val bm = if (horiz) h else w          // beam
        val alpha = Color.alpha(fillC) / 255f
        val deck = withA(mixC(fillC or (0xFF shl 24), Color.WHITE, 0.34f), alpha)
        val hullDark = withA(mixC(fillC or (0xFF shl 24), Color.BLACK, 0.28f), alpha)
        val detail = withA(mixC(fillC or (0xFF shl 24), Color.WHITE, 0.62f), alpha)

        c.save()
        c.translate(x0 + w / 2f, y0 + h / 2f)
        if (!horiz) c.rotate(90f)
        val hl = ln / 2f
        val hb = bm / 2f
        val tip = min(bm * 0.95f, ln * 0.55f)
        val round = bm * 0.28f

        hullPath.reset()
        hullPath.moveTo(-hl + round, -hb)
        hullPath.lineTo(hl - tip, -hb)
        hullPath.quadTo(hl - tip * 0.25f, -hb, hl, 0f)
        hullPath.quadTo(hl - tip * 0.25f, hb, hl - tip, hb)
        hullPath.lineTo(-hl + round, hb)
        hullPath.quadTo(-hl, hb, -hl, hb - round)
        hullPath.lineTo(-hl, -hb + round)
        hullPath.quadTo(-hl, -hb, -hl + round, -hb)
        hullPath.close()
        fill.color = hullDark
        c.drawPath(hullPath, fill)
        // lighter deck: the same shape, smaller, shifted a little to the bow
        c.save()
        c.translate(bm * 0.02f, -bm * 0.04f)
        c.scale((ln - bm * 0.30f) / ln, 0.74f)
        fill.color = fillC
        c.drawPath(hullPath, fill)
        c.restore()

        if (cs >= 20f) {
            // superstructure: cabin, funnel, guns (positions scale with the length)
            val u = bm
            val cabinL = -hl + ln * 0.30f
            val cabinW = if (len == 1) u * 0.5f else u * 0.62f
            fillRRect(c, cabinL, -u * 0.2f, cabinL + cabinW, u * 0.2f, u * 0.08f, deck)
            if (len >= 2) fillRRect(c, cabinL + cabinW * 0.28f, -u * 0.12f, cabinL + cabinW * 0.72f, u * 0.12f, u * 0.05f, detail)
            if (len >= 3) {
                val fx = cabinL + cabinW + u * 0.22f
                fillOval(c, fx - u * 0.14f, -u * 0.14f, fx + u * 0.14f, u * 0.14f, deck)
                fillOval(c, fx - u * 0.07f, -u * 0.07f, fx + u * 0.07f, u * 0.07f, hullDark)
            }
            if (len >= 4 || len == 2) {
                val gx = hl - tip - u * 0.1f
                fillOval(c, gx - u * 0.17f, -u * 0.17f, gx + u * 0.17f, u * 0.17f, deck)
                line.color = deck
                line.strokeWidth = max(1.6f, u * 0.09f)
                c.drawLine(gx, 0f, gx + u * 0.38f, 0f, line)
            }
            if (len >= 3) {
                val gx = -hl + ln * 0.12f
                fillOval(c, gx - u * 0.13f, -u * 0.13f, gx + u * 0.13f, u * 0.13f, deck)
            }
        }
        if (strokeC != 0) {
            line.color = strokeC
            line.strokeWidth = if (cs >= 30f) 2f else 1.2f
            c.drawPath(hullPath, line)
        }
        c.restore()
    }

    /**
     * One sea. [showShips]: draw all ships (own sea); otherwise only sunk ones (and all of them when [reveal]).
     * [skipShip]: ship that is being dragged (drawn faded). [anim]: per-cell animation counters of fresh shots.
     */
    private fun drawSea(
        c: Canvas, b: BsBoard, l: Float, tp: Float, cs: Float, showShips: Boolean, reveal: Boolean,
        anim: IntArray?, last: Int, aimCell: Int, skipShip: Int
    ) {
        val n = BsEngine.N
        val sz = cs * n
        val pad = if (cs >= 30f) 5f else 3f
        fillRRect(c, l - pad, tp - pad, l + sz + pad, tp + sz + pad, if (cs >= 30f) 12f else 7f, Ui.BORDER)
        fillRRect(c, l - 1f, tp - 1f, l + sz + 1f, tp + sz + 1f, if (cs >= 30f) 8f else 5f, waterColor())
        line.color = withA(Ui.GRID, if (Ui.dark) 0.55f else 0.8f)
        line.strokeWidth = 1f
        for (k in 1 until n) {
            c.drawLine(l + k * cs, tp + 1f, l + k * cs, tp + sz - 1f, line)
            c.drawLine(l + 1f, tp + k * cs, l + sz - 1f, tp + k * cs, line)
        }

        // ships
        for (idx in b.ships.indices) {
            val s = b.ships[idx]
            if (!s.placed) continue
            val sunk = b.isSunk(idx)
            if (!(showShips || sunk || reveal)) continue
            val moving = idx == skipShip
            val fc = when {
                moving -> withA(shipColor(), 0.28f)
                sunk && !showShips -> withA(shipColor(), 0.55f)
                !showShips && reveal && !sunk -> withA(shipColor(), 0.42f)
                else -> shipColor()
            }
            drawHull(c, s.len, s.horiz, s.r, s.c, l, tp, cs, fc, if (sunk && !moving) Ui.DANGER else 0)
        }

        // aim: highlighted row, column and cell
        if (aimCell >= 0) {
            val ar = aimCell / n
            val ac = aimCell % n
            fillRect(c, l, tp + ar * cs, l + sz, tp + (ar + 1) * cs, withA(accent, 0.12f))
            fillRect(c, l + ac * cs, tp, l + (ac + 1) * cs, tp + sz, withA(accent, 0.12f))
            fillRRect(c, l + ac * cs + 1f, tp + ar * cs + 1f, l + (ac + 1) * cs - 1f, tp + (ar + 1) * cs - 1f, 6f, withA(accent, 0.35f))
            strokeRRect(c, l + ac * cs + 1f, tp + ar * cs + 1f, l + (ac + 1) * cs - 1f, tp + (ar + 1) * cs - 1f, 6f, accent, 2f)
        }

        // shots
        for (i in 0 until BsEngine.CELLS) {
            if (!b.shot[i]) continue
            val cx = l + (i % n + 0.5f) * cs
            val cy = tp + (i / n + 0.5f) * cs
            val a = if (anim != null) anim[i] / ANIM.toFloat() else 0f
            if (b.owner[i] >= 0) {
                val pulse = 1f + 0.55f * a
                fillOval(c, cx - cs * 0.27f * pulse, cy - cs * 0.27f * pulse, cx + cs * 0.27f * pulse, cy + cs * 0.27f * pulse, Ui.DANGER)
                fillOval(c, cx - cs * 0.1f, cy - cs * 0.1f, cx + cs * 0.1f, cy + cs * 0.1f, withA(Color.WHITE, 0.85f))
                if (a > 0f) {
                    val rr = cs * (0.3f + 0.5f * (1f - a))
                    strokeOval(c, cx - rr, cy - rr, cx + rr, cy + rr, withA(Color.rgb(255, 190, 90), a), 2f)
                }
            } else {
                val dr = cs * 0.1f
                fillOval(c, cx - dr, cy - dr, cx + dr, cy + dr, withA(Ui.MUTED, 0.9f))
                if (a > 0f) {
                    val rr = cs * (0.12f + 0.38f * (1f - a))
                    strokeOval(c, cx - rr, cy - rr, cx + rr, cy + rr, withA(accent, a), if (cs >= 30f) 2f else 1.5f)
                }
            }
        }

        // the last shot at this sea
        if (last >= 0 && b.shot[last]) {
            val ar = last / n
            val ac = last % n
            val pul = 0.65f + 0.35f * (if ((tick / 20) % 2 == 0) 1f else 0.4f)
            strokeRRect(
                c, l + ac * cs + 1.5f, tp + ar * cs + 1.5f, l + (ac + 1) * cs - 1.5f, tp + (ar + 1) * cs - 1.5f,
                min(6f, cs * 0.2f), withA(accent, pul), if (cs >= 30f) 2.2f else 1.6f
            )
        }
    }

    private companion object {
        val LEVEL_KEYS = arrayOf("lvl_easy", "lvl_med", "lvl_hard")
    }
}
