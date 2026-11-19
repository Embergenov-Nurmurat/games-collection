package com.example.minigames.checkers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Achievements
import com.example.minigames.core.Host
import com.example.minigames.core.OUTLINE
import com.example.minigames.core.Sfx
import com.example.minigames.core.W
import com.example.minigames.core.easeOutCubic
import com.example.minigames.core.lerp
import com.example.minigames.core.t
import com.example.minigames.core.withA
import com.example.minigames.core.Ui
import com.example.minigames.core.Button
import com.example.minigames.table.TableGameView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private val LIGHT_SQ = Color.rgb(236, 232, 220)
private val DARK_SQ = Color.rgb(116, 142, 128)
private val GOLD = Color.rgb(245, 181, 68)

private class Snap(
    val pos: CPos,
    val path: IntArray?,       // squares of the move that led here
    val capW: Int,             // white pieces captured so far
    val capB: Int,
    val hash: Long
)

private class Ghost(val sq: Int, val piece: Int, var life: Int)

/**
 * Draughts in three rule sets (see [CVariant]): Russian 8x8, International 10x10 (majority capture), Giveaway 8x8.
 * Mandatory capture, flying kings, men capture backwards.
 */
class CheckersView(context: Context, host: Host) : TableGameView(context, host, "checkers", "checkers_title") {

    private val snaps = ArrayList<Snap>()
    private var rulesOpt = store.getI("checkers_rules", 0).coerceIn(0, CVariant.values().size - 1)
    private var variant = CVariant.RUSSIAN
    private var pos = CPos.start(CVariant.RUSSIAN)

    // board geometry depends on the board size
    private val bn: Int get() = variant.n
    private val sqSize: Float get() = if (bn == 10) 44f else 54f
    private val bx: Float get() = (W - bn * sqSize) / 2f
    private val by: Float get() = if (bn == 10) 176f else 178f
    private var legal: List<CMove> = emptyList()
    private var mustCapture = false
    private var flip = false

    // selection (a move can consist of several jumps: the player taps every landing square)
    private var selected = -1
    private val curPath = ArrayList<Int>()
    private var candidates: List<CMove> = emptyList()
    private var hintMove: CMove? = null

    // animation
    private var animPath: IntArray? = null
    private var animT = 0f
    private var animPiece = 0
    private val ghosts = ArrayList<Ghost>()

    private var pendingRes = 0
    private var pendingReason = ""
    private var pendingT = 0

    private val rulesBtn: Button = btn(
        { t("ck_rules_x", t(variantKey(CVariant.byOrdinal(rulesOpt)))) }, W / 2f, 526f, 320f, 52f, style = { 1 }
    ) {
        rulesOpt = (rulesOpt + 1) % CVariant.values().size
        store.putI("checkers_rules", rulesOpt)
    }

    init {
        addSetupButton(rulesBtn)
    }

    private fun variantKey(v: CVariant): String = when (v) {
        CVariant.RUSSIAN -> "ck_v_ru"
        CVariant.INTERNATIONAL -> "ck_v_int"
        CVariant.GIVEAWAY -> "ck_v_give"
    }

    private fun captionKey(v: CVariant): String = when (v) {
        CVariant.RUSSIAN -> "ck_cap_ru"
        CVariant.INTERNATIONAL -> "ck_cap_int"
        CVariant.GIVEAWAY -> "ck_cap_give"
    }

    override fun sideName(side: Int): String = if (side == 0) t("white") else t("black")

    override val supportsClock: Boolean get() = true
    override val supportsF2f: Boolean get() = true

    override fun onBack(): Boolean {
        if (!fading) flushPending()
        return super.onBack()
    }

    override val accent: Int get() = Color.rgb(255, 159, 90)

    private fun sideToMove(): Int = if (pos.white) 0 else 1

    // ------------------------------- game flow ------------------------------

    override fun onNewGame() {
        variant = CVariant.byOrdinal(rulesOpt)
        pos = CPos.start(variant)
        snaps.clear()
        snaps.add(Snap(pos, null, 0, 0, pos.hash()))
        flip = vsAi && humanSide == 1
        pendingRes = 0
        refresh()
        setClock(0)
        maybeAiMove()
    }

    private fun refresh() {
        legal = CheckersRules.moves(pos)
        mustCapture = legal.isNotEmpty() && legal[0].isCapture
        clearSelection()
        hintMove = null
        animPath = null
        animT = 0f
        ghosts.clear()
    }

    private fun clearSelection() {
        selected = -1
        curPath.clear()
        candidates = emptyList()
    }

    private fun maybeAiMove() {
        if (!vsAi || phase != Phase.PLAY || pendingRes != 0) return
        if (isHumanTurn(sideToMove())) return
        val snapPos = pos.copy()
        val lv = level
        runAi(500L, {
            val big = snapPos.n == 10
            val ai = when (lv) {
                0 -> CheckersAI(2, 300L, 120)
                1 -> CheckersAI(if (big) 4 else 5, 1000L, 10)
                else -> CheckersAI(if (big) 7 else 9, 2500L, 0)
            }
            ai.bestMove(snapPos)
        }) { m ->
            val mine = legal.firstOrNull { it.path.contentEquals(m.path) } ?: legal.randomOrNull()
            if (phase == Phase.PLAY && pendingRes == 0 && mine != null) playMove(mine, false)
        }
    }

    /** Appends the move to the history and returns the ghosts of the captured pieces. */
    private fun advance(m: CMove): ArrayList<Ghost> {
        val last = snaps[snaps.size - 1]
        var capW = last.capW
        var capB = last.capB
        val captured = ArrayList<Ghost>()
        for (s in m.caps) {
            val pc = pos.b[s]
            captured.add(Ghost(s, pc, 22))
            if (pc > 0) capW++ else capB++
        }
        val np = CheckersRules.apply(pos, m)
        snaps.add(Snap(np, m.path, capW, capB, np.hash()))
        pos = np
        return captured
    }

    private fun playMove(m: CMove, byHuman: Boolean) {
        val white = pos.white
        val piece = pos.b[m.from]
        val captured = advance(m)
        refresh()
        animPath = m.path
        animT = 0f
        animPiece = piece
        ghosts.addAll(captured)
        setClock(sideToMove())

        sfx.play(if (m.isCapture) Sfx.Kind.CAPTURE else Sfx.Kind.MOVE)
        haptic()
        if (byHuman && m.caps.size >= 3) Achievements.unlock(store, "checkers_multi")

        when {
            legal.isEmpty() -> beginFinish(CheckersRules.winnerWhenStuck(variant, white), "r_no_moves")
            pos.quiet >= variant.quietLimit -> beginFinish(3, if (variant.quietLimit == 30) "r_quiet" else "ck_quiet25")
            repetitions() >= 3 -> beginFinish(3, "r_repetition")
            else -> maybeAiMove()
        }
    }

    private fun repetitions(): Int {
        val h = snaps[snaps.size - 1].hash
        var n = 0
        for (s in snaps) if (s.hash == h) n++
        return n
    }

    private fun beginFinish(res: Int, reason: String) {
        pendingRes = res
        pendingReason = reason
        pendingT = 55
        cancelAi()
        stopClock()
    }

    /** Ends the game whose result is already decided (after the short pause, or at once when leaving). */
    private fun flushPending() {
        if (pendingRes == 0 || phase != Phase.PLAY) return
        val res = pendingRes
        pendingRes = 0
        finishGame(res, pendingReason)
    }

    // ------------------------- saving and continuing -------------------------

    /** "K1;variant;path,path,..." - every move since the start, a move being its squares joined by '-'. */
    override fun saveState(): String? {
        if (pendingRes != 0 || snaps.size < 2) return null
        val sb = StringBuilder("K1;").append(variant.ordinal).append(';')
        for (i in 1 until snaps.size) {
            if (i > 1) sb.append(',')
            sb.append(snaps[i].path!!.joinToString("-"))
        }
        return sb.toString()
    }

    override fun loadState(s: String): Boolean {
        val parts = s.trim().split(';')
        if (parts.size != 3 || parts[0] != "K1" || parts[2].isEmpty()) return false
        val vi = parts[1].toIntOrNull() ?: return false
        if (vi !in CVariant.values().indices) return false
        val list = ArrayList<IntArray>()
        for (tok in parts[2].split(',')) {
            val sq = tok.split('-')
            if (sq.size < 2) return false
            list.add(IntArray(sq.size) { sq[it].toIntOrNull() ?: return false })
        }
        variant = CVariant.byOrdinal(vi)
        pos = CPos.start(variant)
        snaps.clear()
        snaps.add(Snap(pos, null, 0, 0, pos.hash()))
        // replay: every move must be legal in its position
        for (path in list) {
            val m = CheckersRules.moves(pos).firstOrNull { it.path.contentEquals(path) } ?: return resetAfterBadLoad()
            advance(m)
        }
        // a finished game is never saved
        if (CheckersRules.moves(pos).isEmpty() || pos.quiet >= variant.quietLimit || repetitions() >= 3) return resetAfterBadLoad()
        flip = vsAi && humanSide == 1
        pendingRes = 0
        refresh()
        setClock(sideToMove())
        maybeAiMove()
        return true
    }

    private fun resetAfterBadLoad(): Boolean {
        pos = CPos.start(variant)
        snaps.clear()
        snaps.add(Snap(pos, null, 0, 0, pos.hash()))
        refresh()
        return false
    }

    override fun onHostPause() {
        flushPending()
        super.onHostPause()
    }

    // ------------------------------- input ----------------------------------

    private fun humanCanMove(): Boolean =
        phase == Phase.PLAY && !thinking && pendingRes == 0 && isHumanTurn(sideToMove())

    private fun squareAt(x: Float, y: Float): Int {
        val n = bn
        if (x < bx || y < by || x >= bx + n * sqSize || y >= by + n * sqSize) return -1
        val dc = ((x - bx) / sqSize).toInt().coerceIn(0, n - 1)
        val dr = ((y - by) / sqSize).toInt().coerceIn(0, n - 1)
        val c = if (flip) n - 1 - dc else dc
        val r = if (flip) n - 1 - dr else dr
        return r * n + c
    }

    private fun sx(sq: Int): Float {
        val c = sq % bn
        return bx + ((if (flip) bn - 1 - c else c) + 0.5f) * sqSize
    }

    private fun sy(sq: Int): Float {
        val r = sq / bn
        return by + ((if (flip) bn - 1 - r else r) + 0.5f) * sqSize
    }

    /** Squares the selected piece can go to next (given the jumps already chosen). */
    private fun nextSquares(): List<Int> {
        val step = curPath.size
        val res = ArrayList<Int>()
        for (m in candidates) if (m.path.size > step && !res.contains(m.path[step])) res.add(m.path[step])
        return res
    }

    override fun onPointerDown(x: Float, y: Float) {
        if (!humanCanMove()) return
        val sq = squareAt(x, y)
        if (sq < 0) {
            if (curPath.size <= 1) clearSelection()
            return
        }
        hintMove = null

        // continue / finish the move of the selected piece
        if (selected >= 0 && nextSquares().contains(sq)) {
            curPath.add(sq)
            val n = curPath.size
            candidates = candidates.filter { it.path.size >= n && it.path[n - 1] == sq }
            val done = candidates.firstOrNull { it.path.size == n }
            if (done != null) {
                playMove(done, true)
            } else {
                sfx.play(Sfx.Kind.MOVE, 0.5f)
            }
            return
        }

        // a jump sequence is in progress: it must be finished with the same piece
        if (curPath.size > 1) return

        val pc = pos.b[sq]
        if (pc != 0 && (pc > 0) == pos.white) {
            val mv = legal.filter { it.from == sq }
            if (mv.isEmpty()) {
                if (mustCapture) flashMessage(if (variant.majority) t("ck_majority") else t("must_capture"))
                clearSelection()
                return
            }
            selected = sq
            curPath.clear()
            curPath.add(sq)
            candidates = mv
            sfx.play(Sfx.Kind.CLICK, 0.4f)
        } else {
            clearSelection()
        }
    }

    override fun canUndo(): Boolean =
        phase == Phase.PLAY && !thinking && pendingRes == 0 && undoSize(snaps.size - 1) >= 0

    override fun doUndo() {
        val ns = undoSize(snaps.size - 1)
        if (ns < 0) return
        while (snaps.size > ns + 1) snaps.removeAt(snaps.size - 1)
        pos = snaps[snaps.size - 1].pos
        refresh()
        setClock(sideToMove())
        sfx.play(Sfx.Kind.MOVE)
    }

    override fun canHint(): Boolean = humanCanMove() && legal.isNotEmpty() && !f2f

    override fun doHint() {
        val snapPos = pos.copy()
        runAi(0L, { CheckersAI(if (snapPos.n == 10) 5 else 7, 1500L, 0).bestMove(snapPos) }) { m ->
            val mine = legal.firstOrNull { it.path.contentEquals(m.path) }
            if (phase == Phase.PLAY && mine != null) {
                hintMove = mine
                clearSelection()
            }
        }
    }

    // ------------------------------- update ---------------------------------

    override fun onUpdate() {
        val ap = animPath
        if (ap != null) {
            animT += 0.17f
            if (animT >= ap.size - 1) animPath = null
        }
        var i = ghosts.size - 1
        while (i >= 0) {
            ghosts[i].life--
            if (ghosts[i].life <= 0) ghosts.removeAt(i)
            i--
        }
        if (pendingRes != 0 && phase == Phase.PLAY) {
            pendingT--
            if (pendingT <= 0) flushPending()
        }
    }

    override fun statusText(): String {
        if (pendingRes != 0) return if (pendingRes == 3) t(pendingReason) else t("side_wins", sideName(pendingRes - 1))
        return if (vsAi) {
            if (isHumanTurn(sideToMove())) t("your_turn_as", sideName(humanSide)) else ""
        } else {
            t("turn_of", sideName(sideToMove()))
        }
    }

    // ------------------------------- drawing --------------------------------

    override fun drawSetupArt(c: Canvas, cx: Float, cy: Float) {
        drawDisc(c, cx - 50f, cy, true, false, 84f, 1f)
        drawDisc(c, cx + 50f, cy, false, true, 84f, 1f)
    }

    private fun drawDisc(c: Canvas, x: Float, y: Float, white: Boolean, king: Boolean, size: Float, alpha: Float, turned: Boolean = false) {
        val r = size * 0.4f
        fillOval(c, x - r, y - r + size * 0.07f, x + r, y + r + size * 0.07f, withA(Color.BLACK, 0.28f * alpha))
        val base = if (white) Color.rgb(248, 248, 242) else Color.rgb(44, 52, 72)
        val ring = if (white) Color.rgb(186, 190, 184) else Color.rgb(22, 27, 40)
        val inner = if (white) Color.rgb(234, 236, 228) else Color.rgb(58, 68, 92)
        fillOval(c, x - r, y - r, x + r, y + r, withA(base, alpha))
        strokeOval(c, x - r, y - r, x + r, y + r, withA(ring, alpha), size * 0.07f)
        fillOval(c, x - r * 0.68f, y - r * 0.68f, x + r * 0.68f, y + r * 0.68f, withA(inner, alpha))
        strokeOval(c, x - r * 0.68f, y - r * 0.68f, x + r * 0.68f, y + r * 0.68f, withA(ring, alpha), size * 0.035f)
        if (king) {
            val k = size / 54f
            val ky = if (turned) -k else k      // face-to-face mode: the crown of the far player's kings is upside down
            path.reset()
            path.moveTo(x - 10f * k, y + 6f * ky)
            path.lineTo(x - 11f * k, y - 6f * ky)
            path.lineTo(x - 5f * k, y - 1f * ky)
            path.lineTo(x, y - 9f * ky)
            path.lineTo(x + 5f * k, y - 1f * ky)
            path.lineTo(x + 11f * k, y - 6f * ky)
            path.lineTo(x + 10f * k, y + 6f * ky)
            path.close()
            fill.shader = null
            fill.color = withA(GOLD, alpha)
            canvasPath(c)
            line.color = withA(OUTLINE, alpha)
            line.strokeWidth = 2f * k
            c.drawPath(path, line)
        }
    }

    private fun canvasPath(c: Canvas) {
        c.drawPath(path, fill)
    }

    private fun tint(c: Canvas, sq: Int, color: Int) {
        fillRect(c, sx(sq) - sqSize / 2f, sy(sq) - sqSize / 2f, sx(sq) + sqSize / 2f, sy(sq) + sqSize / 2f, color)
    }

    override fun drawGame(c: Canvas) {
        val n = bn
        val sz = sqSize
        val x0 = bx
        val y0 = by
        fillRRect(c, x0 - 10f, y0 - 10f, x0 + n * sz + 10f, y0 + n * sz + 10f, 14f, Ui.SURFACE)
        strokeRRect(c, x0 - 10f, y0 - 10f, x0 + n * sz + 10f, y0 + n * sz + 10f, 14f, Ui.BORDER, 1.5f)
        for (dr in 0 until n) for (dc in 0 until n) {
            val light = (dr + dc) % 2 == 0
            fillRect(c, x0 + dc * sz, y0 + dr * sz, x0 + (dc + 1) * sz, y0 + (dr + 1) * sz, if (light) LIGHT_SQ else DARK_SQ)
        }

        // coordinates (on the dark squares' contrasting colour)
        for (i in 0 until n) {
            val file = if (flip) n - 1 - i else i
            val rank = if (flip) i + 1 else n - i
            plainText(c, ('a' + file).toString(), x0 + (i + 1) * sz - 7f, y0 + n * sz - 4f, 11f, if ((n - 1 + i) % 2 == 0) DARK_SQ else LIGHT_SQ, 1)
            plainText(c, rank.toString(), x0 + 4f, y0 + i * sz + 12f, 11f, if (i % 2 == 0) DARK_SQ else LIGHT_SQ, -1)
        }

        // rules caption
        plainText(c, t(captionKey(variant)), W / 2f, 98f, 12f, Ui.MUTED, 0, 440f)

        // last move
        val lastPath = snaps[snaps.size - 1].path
        if (lastPath != null) for (s in lastPath) tint(c, s, Color.argb(95, 255, 214, 60))

        // hint
        val hm = hintMove
        if (hm != null) {
            val a = 0.45f + 0.3f * (0.5f + 0.5f * sin(tick * 0.15f))
            for (s in hm.path) tint(c, s, withA(Color.rgb(190, 90, 255), a))
        }

        // selection
        if (selected >= 0) tint(c, selected, Color.argb(150, 70, 140, 255))
        for (i in 1 until curPath.size) tint(c, curPath[i], Color.argb(120, 70, 140, 255))

        // pieces that must capture (hint for beginners)
        if (humanCanMove() && mustCapture && selected < 0) {
            val a = 0.35f + 0.35f * (0.5f + 0.5f * sin(tick * 0.18f))
            val seen = HashSet<Int>()
            for (m in legal) if (seen.add(m.from)) tint(c, m.from, withA(Color.rgb(255, 120, 60), a))
        }

        // pieces
        val ap = animPath
        val skip = if (ap != null) ap[ap.size - 1] else -1
        for (sq in 0 until bn * bn) {
            val pc = pos.b[sq]
            if (pc == 0 || sq == skip) continue
            drawDisc(c, sx(sq), sy(sq), pc > 0, abs(pc) == 2, sqSize * 0.9f, 1f, f2f && pc < 0)
        }
        for (g in ghosts) {
            drawDisc(c, sx(g.sq), sy(g.sq), g.piece > 0, abs(g.piece) == 2, sqSize * 0.9f, g.life / 22f, f2f && g.piece < 0)
        }
        if (ap != null) {
            val seg = min(animT.toInt(), ap.size - 2)
            val f = easeOutCubic(animT - seg)
            val px = lerp(sx(ap[seg]), sx(ap[seg + 1]), f)
            val py = lerp(sy(ap[seg]), sy(ap[seg + 1]), f)
            drawDisc(c, px, py - 3f, animPiece > 0, abs(animPiece) == 2, sqSize * 0.96f, 1f, f2f && animPiece < 0)
        }

        // ghost of the piece on its current landing square while a jump sequence is chosen
        if (curPath.size > 1) {
            val pc = pos.b[selected]
            drawDisc(c, sx(curPath[curPath.size - 1]), sy(curPath[curPath.size - 1]), pc > 0, abs(pc) == 2, sqSize * 0.9f, 0.6f, f2f && pc < 0)
        }

        // next squares
        if (selected >= 0) {
            for (sq in nextSquares()) {
                val cap = candidates.any { it.isCapture }
                fillOval(c, sx(sq) - 9f, sy(sq) - 9f, sx(sq) + 9f, sy(sq) + 9f, if (cap) Color.argb(190, 255, 130, 60) else Color.argb(150, 240, 240, 220))
            }
        }

        drawCaptured(c)
    }

    private fun drawCaptured(c: Canvas) {
        val last = snaps[snaps.size - 1]
        val bottomWhite = !flip
        // pieces captured BY the bottom player are of the opposite colour
        val byBottom = if (bottomWhite) last.capB else last.capW
        val byTop = if (bottomWhite) last.capW else last.capB
        drawCapRow(c, byBottom, bottomWhite.not(), if (bn == 10) 644f else 641f)
        drawCapRow(c, byTop, bottomWhite, 152f)
    }

    private fun drawCapRow(c: Canvas, count: Int, white: Boolean, y: Float) {
        var x = bx + 12f
        for (i in 0 until count) {
            drawDisc(c, x, y, white, false, 28f, 1f)
            x += 17f
        }
    }
}
