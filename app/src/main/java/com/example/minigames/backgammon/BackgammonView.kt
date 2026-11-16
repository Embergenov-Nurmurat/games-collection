package com.example.minigames.backgammon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Achievements
import com.example.minigames.core.Button
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.Ui
import com.example.minigames.core.W
import com.example.minigames.core.easeOutCubic
import com.example.minigames.core.lerp
import com.example.minigames.core.mixC
import com.example.minigames.core.t
import com.example.minigames.core.withA
import com.example.minigames.table.TableGameView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Short backgammon against the computer or a friend.
 * Flow: roll the dice -> tap a checker (legal ones are ringed) -> tap a highlighted target; repeat for every die.
 * While the turn is not finished (or during the short pause after the last die) "UNDO" takes steps back.
 */
class BackgammonView(context: Context, host: Host) : TableGameView(context, host, "bg", "bg_title") {

    private enum class Stage { ROLL, ROLLING, MOVE, PASS, END, DONE }

    private class MoveAnim(
        val fx: Float, val fy: Float, val tx: Float, val ty: Float,
        val side: Int, val dest: Int
    ) {
        var t = 0
    }

    private var game = BgGame()
    private var stage = Stage.ROLL
    private var stageT = 0
    private var selected = -2                  // -2 nothing, 0..23 point, Bg.BAR
    private var legal = IntArray(0)
    private var hintMove = -1
    private var aiSeq: IntArray? = null
    private var aiWait = 0
    private var aiRequested = false
    private var aiToken = 0
    private var anim: MoveAnim? = null
    private var pendingRes = 0
    private var pendingT = 0
    private val faces = IntArray(4) { it + 1 }
    private var lastMoves = IntArray(0)
    private val rng = Random.Default

    private var px = 0f
    private var py = 0f

    override val accent: Int get() = ACCENT_COLOR

    private val rollBtn: Button = btn(
        { t("bg_roll") }, 328f, MID_Y, 164f, 46f,
        visible = { phase == Phase.PLAY && stage == Stage.ROLL && result == 0 && isHumanTurn(game.turn) }
    ) {
        if (stage == Stage.ROLL && isHumanTurn(game.turn)) startRoll(rng.nextInt(6) + 1, rng.nextInt(6) + 1)
    }

    override fun buttons(): List<Button> = if (phase == Phase.PLAY) super.buttons() + rollBtn else super.buttons()

    /** The side whose home board is drawn at the bottom. */
    private val viewSide: Int get() = if (vsAi) humanSide else 0

    override fun sideName(side: Int): String = if (side == 0) t("white") else t("black")

    // ------------------------------- colours --------------------------------

    private val boardBg: Int get() = if (Ui.dark) Color.rgb(40, 30, 26) else Color.rgb(240, 228, 210)
    private val barBg: Int get() = if (Ui.dark) Color.rgb(31, 23, 20) else Color.rgb(222, 205, 182)
    private val ptA: Int get() = if (Ui.dark) Color.rgb(156, 100, 58) else Color.rgb(184, 122, 70)
    private val ptB: Int get() = if (Ui.dark) Color.rgb(100, 68, 48) else Color.rgb(216, 178, 132)
    private val whiteFill: Int get() = Color.rgb(246, 240, 228)
    private val whiteEdge: Int get() = if (Ui.dark) Color.rgb(196, 184, 166) else Color.rgb(146, 126, 104)
    private val blackFill: Int get() = if (Ui.dark) Color.rgb(54, 42, 38) else Color.rgb(48, 36, 32)
    private val blackEdge: Int get() = if (Ui.dark) Color.rgb(176, 146, 118) else Color.rgb(20, 14, 12)

    // ------------------------------ game flow -------------------------------

    override fun onNewGame() {
        resetFlow()
        game = BgGame()
        // opening roll: each side rolls one die, the higher one moves first with both dice
        val a = rng.nextInt(6) + 1
        var b: Int
        do { b = rng.nextInt(6) + 1 } while (b == a)
        game.turn = if (a > b) 0 else 1
        flashMessage(
            if (!vsAi) t("bg_first_x", sideName(game.turn))
            else if (game.turn == humanSide) t("bg_first_you") else t("bg_first_cpu")
        )
        startRoll(a, b)
    }

    private fun resetFlow() {
        stage = Stage.ROLL
        stageT = 0
        selected = -2
        legal = IntArray(0)
        hintMove = -1
        aiSeq = null
        aiRequested = false
        aiToken++
        anim = null
        pendingRes = 0
        lastMoves = IntArray(0)
    }

    private fun startRoll(a: Int, b: Int) {
        game.roll(a, b)
        stage = Stage.ROLLING
        stageT = 28
        selected = -2
        hintMove = -1
        sfx.play(Sfx.Kind.MOVE)
    }

    private fun afterRoll() {
        legal = game.legalMoves()
        selected = -2
        hintMove = -1
        if (legal.isEmpty()) {
            stage = Stage.PASS
            stageT = 60
            flashMessage(t("bg_no_moves"))
        } else {
            stage = Stage.MOVE
            aiSeq = null
            aiRequested = false
            if (isHumanTurn(game.turn)) autoSelect()
        }
    }

    private fun autoSelect() {
        var src = -2
        for (m in legal) {
            val f = Bg.from(m)
            if (src == -2) src = f else if (src != f) {
                selected = -2
                return
            }
        }
        selected = src
    }

    private fun endTurn() {
        lastMoves = game.turnMoves()
        game.endTurn()
        stage = Stage.ROLL
        stageT = 0
        selected = -2
        hintMove = -1
        legal = IntArray(0)
        aiSeq = null
        aiRequested = false
    }

    private fun doMove(m: Int) {
        val side = game.turn
        val from = Bg.from(m)
        val to = Bg.to(m)
        srcPos(side, from)
        val fx = px
        val fy = py
        val hit = game.play(m)
        dstPos(side, to)
        anim = MoveAnim(fx, fy, px, py, side, to)
        sfx.play(if (hit) Sfx.Kind.CAPTURE else Sfx.Kind.PLACE)
        if (hit) haptic()
        selected = -2
        hintMove = -1
        if (game.winner() >= 0) {
            stage = Stage.DONE
            pendingRes = game.winner() + 1
            pendingT = 60
            legal = IntArray(0)
            return
        }
        legal = game.legalMoves()
        if (game.allUsed() || legal.isEmpty()) {
            if (!game.allUsed()) flashMessage(t("bg_no_more"))
            stage = Stage.END
            stageT = if (isHumanTurn(game.turn)) 42 else 26
            legal = IntArray(0)
        } else if (isHumanTurn(game.turn)) {
            autoSelect()
        }
    }

    private fun requestAi() {
        aiRequested = true
        val token = ++aiToken
        val snap = game.copy()
        val lvl = level
        runAi(450L, { BgAi.choose(snap, lvl, 600L) }) { seq ->
            if (token == aiToken && phase == Phase.PLAY && stage == Stage.MOVE) {
                aiSeq = seq
                aiWait = 6
            }
        }
    }

    private fun playAiStep() {
        val seq = aiSeq ?: return
        if (legal.isEmpty()) return
        // the sequence is the rest of the turn: the next move is the first one that is still legal
        var m = -1
        for (c in seq) if (legal.contains(c)) {
            m = c
            break
        }
        if (m < 0) m = legal[rng.nextInt(legal.size)]
        // consume the chosen move from the plan
        val rest = ArrayList<Int>()
        var removed = false
        for (c in seq) if (!removed && c == m) removed = true else rest.add(c)
        aiSeq = rest.toIntArray()
        aiWait = 8
        doMove(m)
    }

    private fun humanCanMove(): Boolean =
        phase == Phase.PLAY && result == 0 && stage == Stage.MOVE && anim == null && !thinking &&
            pendingRes == 0 && isHumanTurn(game.turn)

    // ------------------------------- input ----------------------------------

    private fun hitTarget(x: Float, y: Float): Int {
        if (x >= TRAY_L && x <= TRAY_R && y >= PANEL_T && y <= PANEL_B) return Bg.OFF
        if (y < TOP_Y || y > END_Y) return NONE
        if (x >= BAR_L && x <= BAR_L + 32f) return Bg.BAR
        if (x < 8f || x >= 424f) return NONE
        val col = if (x < BAR_L) ((x - 8f) / 32f).toInt() else ((x - BAR_L - 32f) / 32f).toInt() + 6
        val row = when {
            y <= TOP_Y + TRI_H -> 0
            y >= BOT_Y -> 1
            else -> return NONE
        }
        val p = if (row == 1) 12 - col else 13 + col
        if (p !in 1..24) return NONE
        return if (viewSide == 0) p - 1 else 24 - p
    }

    override fun onPointerDown(x: Float, y: Float) {
        if (!humanCanMove()) return
        val tgt = hitTarget(x, y)
        if (tgt == NONE) {
            selected = -2
            return
        }
        if (selected >= 0) {
            var best = -1
            for (m in legal) {
                if (Bg.from(m) == selected && Bg.to(m) == tgt && (best < 0 || Bg.die(m) < Bg.die(best))) best = m
            }
            if (best >= 0) {
                doMove(best)
                return
            }
        }
        var isSource = false
        for (m in legal) if (Bg.from(m) == tgt) isSource = true
        selected = if (isSource && selected != tgt) tgt else -2
    }

    override fun canUndo(): Boolean =
        phase == Phase.PLAY && result == 0 && pendingRes == 0 && !thinking && isHumanTurn(game.turn) &&
            (stage == Stage.MOVE || stage == Stage.END) && game.canUndoStep()

    override fun doUndo() {
        if (!canUndo()) return
        if (!game.undoStep()) return
        anim = null
        selected = -2
        hintMove = -1
        stage = Stage.MOVE
        legal = game.legalMoves()
        autoSelect()
        sfx.play(Sfx.Kind.MOVE)
    }

    override fun canHint(): Boolean = humanCanMove() && legal.isNotEmpty()

    override fun doHint() {
        if (!canHint()) return
        val snap = game.copy()
        val token = aiToken
        runAi(0L, { BgAi.choose(snap, 2, 500L) }) { seq ->
            if (token == aiToken && phase == Phase.PLAY && stage == Stage.MOVE && legal.isNotEmpty()) {
                var m = -1
                for (c in seq) if (legal.contains(c)) {
                    m = c
                    break
                }
                hintMove = if (m >= 0) m else legal[0]
            }
        }
    }

    // ------------------------------ save / load -----------------------------

    override fun saveState(): String? = if (game.winner() >= 0) null else game.serialize()

    override fun loadState(s: String): Boolean {
        val g = BgGame()
        if (!g.deserialize(s) || g.winner() >= 0) return false
        resetFlow()
        game = g
        if (!g.rolled) {
            stage = Stage.ROLL
        } else {
            legal = g.legalMoves()
            if (g.allUsed() || legal.isEmpty()) {
                endTurn()
            } else {
                stage = Stage.MOVE
                if (isHumanTurn(g.turn)) autoSelect()
            }
        }
        return true
    }

    // ------------------------------- update ---------------------------------

    override fun onUpdate() {
        rollBtn.cx = if (game.turn == viewSide) 328f else 104f
        val a = anim
        if (a != null) {
            a.t++
            if (a.t >= MOVE_FRAMES) anim = null
        }
        if (phase != Phase.PLAY || result != 0) return
        when (stage) {
            Stage.ROLL -> {
                if (!isHumanTurn(game.turn)) {
                    stageT++
                    if (stageT >= 24) startRoll(rng.nextInt(6) + 1, rng.nextInt(6) + 1)
                }
            }
            Stage.ROLLING -> {
                stageT--
                if (stageT % 3 == 0) for (i in 0 until 4) faces[i] = rng.nextInt(6) + 1
                if (stageT <= 0) afterRoll()
            }
            Stage.MOVE -> if (!isHumanTurn(game.turn)) {
                if (aiSeq == null) {
                    if (!thinking && !aiRequested) requestAi()
                    else if (!thinking && aiRequested) aiSeq = IntArray(0)   // the search failed: fall back to random moves
                } else if (anim == null) {
                    if (aiWait > 0) aiWait-- else playAiStep()
                }
            }
            Stage.PASS, Stage.END -> {
                stageT--
                if (stageT <= 0 && anim == null) endTurn()
            }
            Stage.DONE -> {
                pendingT--
                if (pendingT <= 0 && pendingRes != 0) {
                    val res = pendingRes
                    pendingRes = 0
                    if (vsAi && res == humanSide + 1 && game.b.off[1 - humanSide] == 0) {
                        Achievements.unlock(store, "bg_gammon")
                    }
                    finishGame(res, "bg_r_borne")
                }
            }
        }
    }

    override fun statusText(): String {
        if (!vsAi) return t("turn_of", sideName(game.turn))
        val mine = game.turn == humanSide
        return when {
            !mine -> t("bg_cpu_turn")
            stage == Stage.ROLL || stage == Stage.ROLLING -> t("bg_your_roll")
            else -> t("bg_your_move")
        }
    }

    // ------------------------------ geometry --------------------------------

    private fun ownPoint(i: Int): Int = if (viewSide == 0) i + 1 else 24 - i
    private fun rowOf(i: Int): Int = if (ownPoint(i) <= 12) 1 else 0
    private fun colOf(i: Int): Int {
        val p = ownPoint(i)
        return if (p <= 12) 12 - p else p - 13
    }

    private fun colX(col: Int): Float = 8f + col * 32f + (if (col >= 6) 32f else 0f)
    private fun pointCx(i: Int): Float = colX(colOf(i)) + 16f

    private fun stackStep(count: Int): Float =
        if (count <= 5) 2f * R + 1f else (TRI_H - 2f * R - 4f) / (count - 1)

    private fun pointY(i: Int, k: Int, count: Int): Float {
        val st = stackStep(count)
        return if (rowOf(i) == 0) TOP_Y + R + 2f + k * st else END_Y - R - 2f - k * st
    }

    private fun barY(side: Int, k: Int, count: Int): Float {
        val st = if (count <= 4) 30f else 120f / (count - 1)
        return if (side == viewSide) MID_Y + 28f + k * st else MID_Y - 28f - k * st
    }

    private fun trayY(side: Int, k: Int): Float =
        if (side == viewSide) END_Y - 6f - k * 9f - 3.5f else TOP_Y + 6f + k * 9f + 3.5f

    /** Position of the top checker of [side] on [from] (call before the move). */
    private fun srcPos(side: Int, from: Int) {
        px = if (from == Bg.BAR) BAR_L + 16f else pointCx(from)
        py = if (from == Bg.BAR) {
            val n = game.b.bar[side]
            barY(side, (n - 1).coerceAtLeast(0), n)
        } else {
            val n = game.b.own(side, from)
            pointY(from, (n - 1).coerceAtLeast(0), n)
        }
    }

    /** Position where the checker of [side] that just arrived on [to] rests (call after the move). */
    private fun dstPos(side: Int, to: Int) {
        if (to == Bg.OFF) {
            px = (TRAY_L + TRAY_R) / 2f
            py = trayY(side, (game.b.off[side] - 1).coerceAtLeast(0))
        } else {
            val n = game.b.own(side, to)
            px = pointCx(to)
            py = pointY(to, (n - 1).coerceAtLeast(0), n)
        }
    }

    // ------------------------------- drawing --------------------------------

    override fun drawSetupArt(c: Canvas, cx: Float, cy: Float) {
        drawDie(c, cx - 76f, cy - 18f, 44f, 5, 1f)
        drawDie(c, cx - 20f, cy - 18f, 44f, 3, 1f)
        drawChecker(c, cx + 50f, cy + 10f, 0, 1f)
        drawChecker(c, cx + 84f, cy + 10f, 1, 1f)
    }

    override fun drawGame(c: Canvas) {
        val b = game.b
        // board
        fillRRect(c, 4f, PANEL_T, 428f, PANEL_B, 14f, boardBg)
        strokeRRect(c, 4f, PANEL_T, 428f, PANEL_B, 14f, Ui.BORDER, 1.5f)
        fillRect(c, BAR_L, PANEL_T + 1f, BAR_L + 32f, PANEL_B - 1f, barBg)
        fillRRect(c, TRAY_L, PANEL_T, TRAY_R, PANEL_B, 12f, boardBg)
        strokeRRect(c, TRAY_L, PANEL_T, TRAY_R, PANEL_B, 12f, Ui.BORDER, 1.5f)
        fillRect(c, TRAY_L + 3f, MID_Y - 1f, TRAY_R - 3f, MID_Y + 1f, withA(Ui.BORDER, 0.8f))

        // points
        for (col in 0 until 12) for (row in 0..1) {
            val col2 = if ((col + row) % 2 == 0) ptA else ptB
            triangle(c, col, row, col2)
        }
        // last turn markers
        for (m in lastMoves) {
            val f = Bg.from(m)
            val to = Bg.to(m)
            if (f != Bg.BAR) tint(c, f, withA(Ui.TEXT, 0.07f))
            if (to != Bg.OFF) tint(c, to, withA(Ui.TEXT, 0.14f))
        }

        val can = humanCanMove()
        val pulse = 0.5f + 0.5f * sin(tick * 0.14f)
        // targets of the selected checker
        var trayLit = false
        if (can && selected >= -1 && selected != -2) {
            for (m in legal) {
                if (Bg.from(m) != selected) continue
                val to = Bg.to(m)
                if (to == Bg.OFF) trayLit = true else tint(c, to, withA(accent, 0.30f + 0.12f * pulse))
            }
        }
        if (hintMove >= 0 && can) {
            val to = Bg.to(hintMove)
            if (to == Bg.OFF) trayLit = true else tint(c, to, withA(Ui.OK, 0.28f + 0.16f * pulse))
        }
        if (trayLit && can) {
            fillRRect(c, TRAY_L, PANEL_T, TRAY_R, PANEL_B, 12f, withA(accent, 0.14f + 0.1f * pulse))
            strokeRRect(c, TRAY_L, PANEL_T, TRAY_R, PANEL_B, 12f, accent, 2f)
        }

        // checkers on the points
        val an = anim
        for (i in 0 until 24) {
            val v = b.pt[i]
            if (v == 0) continue
            val n = abs(v)
            val side = if (v > 0) 0 else 1
            var shown = n
            if (an != null && an.dest == i && an.side == side) shown--
            for (k in 0 until shown) drawChecker(c, pointCx(i), pointY(i, k, n), side, 1f)
            if (n > 5 && shown == n) {
                plainText(c, n.toString(), pointCx(i), pointY(i, n - 1, n) + 5f, 14f, if (side == 0) Ui.INK else Color.WHITE)
            }
        }
        // bar
        for (s in 0..1) {
            val n = b.bar[s]
            for (k in 0 until n) drawChecker(c, BAR_L + 16f, barY(s, k, n), s, 1f)
            if (n > 4) plainText(c, n.toString(), BAR_L + 16f, barY(s, n - 1, n) + 5f, 14f, if (s == 0) Ui.INK else Color.WHITE)
        }
        // bear-off trays
        val trayCx = (TRAY_L + TRAY_R) / 2f
        for (s in 0..1) {
            var n = b.off[s]
            if (an != null && an.dest == Bg.OFF && an.side == s) n--
            for (k in 0 until n) {
                val y = trayY(s, k)
                fillRRect(c, trayCx - 15f, y - 3.5f, trayCx + 15f, y + 3.5f, 2.5f, if (s == 0) whiteFill else blackFill)
                strokeRRect(c, trayCx - 15f, y - 3.5f, trayCx + 15f, y + 3.5f, 2.5f, if (s == 0) whiteEdge else blackEdge, 1f)
            }
            if (b.off[s] > 0) {
                val by = if (s == viewSide) MID_Y + 20f else MID_Y - 10f
                plainText(c, b.off[s].toString(), trayCx, by, 14f, Ui.MUTED)
            }
        }

        // source rings and hint
        if (can) {
            for (m in legal) {
                val f = Bg.from(m)
                if (!isFirstOfSource(m)) continue
                ringTop(c, f, if (f == selected) accent else withA(accent, 0.55f + 0.3f * pulse), if (f == selected) 3f else 2f)
            }
            if (hintMove >= 0) ringTop(c, Bg.from(hintMove), Ui.OK, 3f)
        }

        drawDice(c)
        drawButton(c, rollBtn)
        drawStrips(c)

        // flying checker
        if (an != null) {
            val k = easeOutCubic(an.t.toFloat() / MOVE_FRAMES)
            val x = lerp(an.fx, an.tx, k)
            val y = lerp(an.fy, an.ty, k) - sin(k * PI.toFloat()) * 12f
            val a = if (an.dest == Bg.OFF) 1f - 0.5f * k else 1f
            drawChecker(c, x, y, an.side, a)
        }
    }

    private fun isFirstOfSource(m: Int): Boolean {
        val f = Bg.from(m)
        for (o in legal) {
            if (o == m) return true
            if (Bg.from(o) == f) return false
        }
        return true
    }

    private fun ringTop(c: Canvas, from: Int, color: Int, w: Float) {
        val side = game.turn
        val x: Float
        val y: Float
        if (from == Bg.BAR) {
            val n = game.b.bar[side]
            x = BAR_L + 16f
            y = barY(side, (n - 1).coerceAtLeast(0), n)
        } else {
            val n = game.b.own(side, from)
            x = pointCx(from)
            y = pointY(from, (n - 1).coerceAtLeast(0), n)
        }
        strokeOval(c, x - R - 2.5f, y - R - 2.5f, x + R + 2.5f, y + R + 2.5f, color, w)
    }

    private fun triangle(c: Canvas, col: Int, row: Int, color: Int) {
        val x0 = colX(col)
        path.reset()
        if (row == 0) {
            path.moveTo(x0 + 1f, TOP_Y)
            path.lineTo(x0 + 31f, TOP_Y)
            path.lineTo(x0 + 16f, TOP_Y + TRI_H * 0.9f)
        } else {
            path.moveTo(x0 + 1f, END_Y)
            path.lineTo(x0 + 31f, END_Y)
            path.lineTo(x0 + 16f, END_Y - TRI_H * 0.9f)
        }
        path.close()
        fill.shader = null
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun tint(c: Canvas, i: Int, color: Int) {
        val col = colOf(i)
        val row = rowOf(i)
        triangle(c, col, row, color)
    }

    private fun drawChecker(c: Canvas, x: Float, y: Float, side: Int, alpha: Float) {
        val fc = if (side == 0) whiteFill else blackFill
        val ec = if (side == 0) whiteEdge else blackEdge
        fillOval(c, x - R, y - R, x + R, y + R, if (alpha < 1f) withA(fc, alpha) else fc)
        strokeOval(c, x - R + 0.75f, y - R + 0.75f, x + R - 0.75f, y + R - 0.75f, if (alpha < 1f) withA(ec, alpha) else ec, 1.5f)
        strokeOval(c, x - R * 0.58f, y - R * 0.58f, x + R * 0.58f, y + R * 0.58f, withA(ec, 0.4f * alpha), 1.2f)
    }

    private fun drawDie(c: Canvas, x: Float, y: Float, s: Float, v: Int, alpha: Float) {
        val face = mixC(Color.rgb(250, 246, 238), boardBg, 1f - alpha)
        val pip = mixC(Color.rgb(58, 40, 30), boardBg, 1f - alpha)
        fillRRect(c, x, y, x + s, y + s, s * 0.2f, face)
        strokeRRect(c, x, y, x + s, y + s, s * 0.2f, mixC(Color.rgb(190, 170, 146), boardBg, 1f - alpha), 1.5f)
        val q = s * 0.26f
        val r = s * 0.085f
        val cx = x + s / 2f
        val cy = y + s / 2f
        fun dot(dx: Float, dy: Float) = fillOval(c, cx + dx * q - r, cy + dy * q - r, cx + dx * q + r, cy + dy * q + r, pip)
        if (v % 2 == 1) dot(0f, 0f)
        if (v >= 2) { dot(-1f, -1f); dot(1f, 1f) }
        if (v >= 4) { dot(1f, -1f); dot(-1f, 1f) }
        if (v == 6) { dot(-1f, 0f); dot(1f, 0f) }
    }

    private fun drawDice(c: Canvas) {
        if (!game.rolled) return
        val n = game.nDice
        val s = if (n == 2) 44f else 38f
        val gap = if (n == 2) 10f else 6f
        val total = n * s + (n - 1) * gap
        val cx = if (game.turn == viewSide) 328f else 104f
        val rolling = stage == Stage.ROLLING
        for (i in 0 until n) {
            val x = cx - total / 2f + i * (s + gap)
            val y = MID_Y - s / 2f
            val used = (game.used shr i) and 1 == 1
            val jit = if (rolling) sin((tick + i * 5) * 0.9f) * 3f else 0f
            drawDie(c, x, y + jit, s, if (rolling) faces[i] else game.dice[i], if (used) 0.38f else 1f)
        }
    }

    private fun drawStrips(c: Canvas) {
        val b = game.b
        for (pos in 0..1) {
            val side = if (pos == 0) 1 - viewSide else viewSide
            val y0 = if (pos == 0) 138f else 602f
            val y1 = y0 + 28f
            val active = phase == Phase.PLAY && game.turn == side
            fillRRect(c, 8f, y0, 472f, y1, 12f, Ui.SURFACE)
            strokeRRect(c, 8f, y0, 472f, y1, 12f, if (active) accent else Ui.BORDER, 1.5f)
            drawChecker(c, 28f, (y0 + y1) / 2f, side, 1f)
            val name = if (vsAi) (if (side == humanSide) t("bg_you") else t("bg_cpu")) else sideName(side)
            plainText(c, name, 48f, y0 + 20f, 16f, Ui.TEXT, -1, 190f)
            plainText(c, t("bg_pips", b.pips(side)), 462f, y0 + 20f, 16f, Ui.MUTED, 1, 190f)
        }
    }

    private companion object {
        val ACCENT_COLOR: Int = Color.rgb(222, 138, 62)
        const val R = 14.5f
        const val PANEL_T = 172f
        const val PANEL_B = 596f
        const val TOP_Y = 176f
        const val TRI_H = 166f
        const val MID_Y = 384f
        const val BOT_Y = 426f
        const val END_Y = 592f
        const val BAR_L = 200f
        const val TRAY_L = 434f
        const val TRAY_R = 474f
        const val NONE = -3
        const val MOVE_FRAMES = 14
    }
}
