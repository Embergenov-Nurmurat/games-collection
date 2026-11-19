package com.example.minigames.chess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Achievements
import com.example.minigames.core.Button
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.W
import com.example.minigames.core.easeOutCubic
import com.example.minigames.core.clampF
import com.example.minigames.core.lerp
import com.example.minigames.core.t
import com.example.minigames.core.withA
import com.example.minigames.core.Ui
import com.example.minigames.table.TableGameView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val S = 54f           // square size
private const val BX = 24f          // board left
private const val BY = 178f         // board top
private const val GX0 = 36f         // review graph: x of the first / last position
private const val GX1 = 444f

private val LIGHT_SQ = Color.rgb(236, 232, 220)
private val DARK_SQ = Color.rgb(116, 142, 128)

/** One position of the game with everything needed to undo and to display it. */
private class Snap(
    val pos: ChessPos,
    val from: Int,
    val to: Int,
    val capW: IntArray,     // white pieces captured so far, by type
    val capB: IntArray,     // black pieces captured so far, by type
    val hash: Long,
    val mv: Int = 0         // the move that led here (0 for the start position)
)

private class Ghost(val sq: Int, val piece: Int, var life: Int)

class ChessView(context: Context, host: Host) : TableGameView(context, host, "chess", "chess_title") {

    private val snaps = ArrayList<Snap>()
    private var pos = ChessPos.start()
    private var legal = IntArray(0)
    private var flip = false

    private var selected = -1
    private var selMoves = IntArray(0)
    private var checkSq = -1
    private var hintMove = 0

    // promotion dialog
    private var promoFrom = -1
    private var promoTo = -1
    private val promoTypes = intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)

    // animation of the last move
    private var animFrom = -1
    private var animTo = -1
    private var animT = 1f
    private val ghosts = ArrayList<Ghost>()

    // delayed end of the game
    private var pendingRes = 0
    private var pendingReason = ""
    private var pendingT = 0

    // opening book: id of the opening played so far (text key "chx_op_<id>")
    private var openingId: String? = null

    // game review (after the game): the board shows snaps[rvIdx], the engine marks inaccuracies / mistakes / blunders
    private var reviewMode = false
    private var rv: ReviewResult? = null
    private var rvIdx = 0
    private var rvSans: Array<String> = emptyArray()
    private var rvGen = 0
    @Volatile private var rvProgress = 0
    private var graphDrag = false

    // mate puzzles: a mode of this screen (phase stays PLAY, the normal game state is not touched in the store)
    private var puzzleMode = false
    private var puzIdx = 0
    private var puzLeft = 0
    private var puzFirst = true
    private var puzDone = false
    private var puzHinted = false
    private var puzMsg = ""
    private var puzMsgBad = false
    private var puzMsgT = 0

    private val puzzlesBtn: Button = btn(
        { t("chx_puzzles_x", puzzlesSolved(), ChessPuzzles.all.size) }, W / 2f, 0f, 320f, 52f, style = { 1 }
    ) { enterPuzzles() }

    private val pzPrev: Button = btn({ "<" }, 62f, 662f, 84f, 46f, style = { 1 }, enabled = { puzIdx > 0 && !thinking }) { loadPuzzle(puzIdx - 1) }
    private val pzHint: Button = btn({ t("hint") }, 172f, 662f, 112f, 46f, style = { 2 }, enabled = { !puzDone && !thinking }) { puzzleHint() }
    private val pzRestart: Button = btn({ t("chx_restart") }, 296f, 662f, 112f, 46f, style = { 1 }, enabled = { !thinking }) { loadPuzzle(puzIdx) }
    private val pzNext: Button = btn(
        { ">" }, 418f, 662f, 84f, 46f, style = { if (puzDone) 2 else 1 },
        enabled = { puzIdx < ChessPuzzles.all.size - 1 && !thinking }
    ) { loadPuzzle(puzIdx + 1) }
    private val pzAll: List<Button> by lazy { listOf(backBtn, pzPrev, pzHint, pzRestart, pzNext) }

    private val reviewBtn: Button = btn({ t("chx_review") }, 150f, 518f, 150f, 50f, style = { 2 }) { startReview() }
    private val shareBtn: Button = btn({ t("chx_share") }, 330f, 518f, 150f, 50f, style = { 1 }) { shareGame() }

    private val rvFirst: Button = btn({ "|<" }, 75f, 638f, 102f, 44f, style = { 1 }, enabled = { rvIdx > 0 }) { rvGo(0) }
    private val rvPrev: Button = btn({ "<" }, 185f, 638f, 102f, 44f, style = { 1 }, enabled = { rvIdx > 0 }) { rvGo(rvIdx - 1) }
    private val rvNext: Button = btn({ ">" }, 295f, 638f, 102f, 44f, style = { 1 }, enabled = { rvIdx < snaps.size - 1 }) { rvGo(rvIdx + 1) }
    private val rvLast: Button = btn({ ">|" }, 405f, 638f, 102f, 44f, style = { 1 }, enabled = { rvIdx < snaps.size - 1 }) { rvGo(snaps.size - 1) }
    private val rvPrevErr: Button = btn({ t("chx_prev_err") }, 93f, 686f, 138f, 44f, style = { 1 }, enabled = { findMark(-1) >= 0 }) { rvGo(findMark(-1)) }
    private val rvNextErr: Button = btn({ t("chx_next_err") }, 387f, 686f, 138f, 44f, style = { 1 }, enabled = { findMark(1) >= 0 }) { rvGo(findMark(1)) }
    private val rvShare: Button = btn({ t("chx_share") }, 240f, 686f, 138f, 44f, style = { 2 }) { shareGame() }
    private val rvAll: List<Button> by lazy { listOf(backBtn, rvFirst, rvPrev, rvNext, rvLast, rvPrevErr, rvShare, rvNextErr) }

    init {
        addSetupButton(puzzlesBtn)
    }

    override fun overExtraButtons(): List<Button> = listOf(reviewBtn, shareBtn)

    override fun sideName(side: Int): String = if (side == 0) t("white") else t("black")

    override val accent: Int get() = Color.rgb(98, 214, 164)

    override val supportsClock: Boolean get() = true
    override val supportsF2f: Boolean get() = true

    private fun sideToMove(): Int = if (pos.white) 0 else 1

    override fun buttons(): List<Button> =
        if (puzzleMode) pzAll else if (reviewMode) rvAll else if (promoFrom >= 0 && phase == Phase.PLAY) emptyList() else super.buttons()

    override fun onBack(): Boolean {
        if (!fading && puzzleMode) {
            exitPuzzles()
            return true
        }
        if (!fading && reviewMode) {
            exitReview()
            return true
        }
        if (!fading && promoFrom >= 0 && phase == Phase.PLAY) {      // close the promotion dialog first
            promoFrom = -1
            promoTo = -1
            return true
        }
        if (!fading) flushPending()
        return super.onBack()
    }

    // ------------------------------- game flow ------------------------------

    override fun onNewGame() {
        pos = ChessPos.start()
        snaps.clear()
        snaps.add(Snap(pos, -1, -1, IntArray(7), IntArray(7), pos.hash()))
        flip = vsAi && humanSide == 1
        pendingRes = 0
        reviewMode = false
        refresh()
        setClock(0)
        maybeAiMove()
    }

    /** Recomputes everything that depends only on the current position. */
    private fun refresh() {
        legal = ChessRules.legal(pos)
        selected = -1
        selMoves = IntArray(0)
        promoFrom = -1
        promoTo = -1
        hintMove = 0
        val k = ChessRules.findKing(pos.b, pos.white)
        checkSq = if (k >= 0 && ChessRules.inCheck(pos)) k else -1
        val last = snaps[snaps.size - 1]
        animFrom = last.from
        animTo = last.to
        animT = 1f
        ghosts.clear()
        openingId = ChessBook.nameFor(history())
    }

    /** The moves of the game so far (engine encoding). */
    private fun history(): IntArray = IntArray(snaps.size - 1) { snaps[it + 1].mv }

    private fun maybeAiMove() {
        if (!vsAi || phase != Phase.PLAY || pendingRes != 0) return
        if (isHumanTurn(sideToMove())) return
        val snapPos = pos.copy()
        val lv = level
        val hist = history()
        runAi(500L, {
            val bookMove = ChessBook.next(hist)
            if (bookMove != 0) return@runAi bookMove
            val ai = when (lv) {
                0 -> ChessAI(1, 400L, 220)
                1 -> ChessAI(3, 1500L, 15)
                else -> ChessAI(5, 3000L, 0)
            }
            ai.bestMove(snapPos)
        }) { m ->
            if (phase == Phase.PLAY && pendingRes == 0 && legal.isNotEmpty()) {
                playMove(if (m != 0 && legal.contains(m)) m else legal.random())
            }
        }
    }

    /** Plays [m] on the board (no sound, no animation, no end-of-game check). Returns the captured piece or 0. */
    private fun advance(m: Int): Int {
        val from = mvFrom(m)
        val to = mvTo(m)
        val white = pos.white
        var captured = pos.b[to]
        var capSq = to
        if (captured == 0 && ChessRules.isCapture(pos, m)) {
            // en passant
            captured = if (white) -PAWN else PAWN
            capSq = (from and 56) or (to and 7)
        }
        val np = ChessRules.make(pos, m)

        val last = snaps[snaps.size - 1]
        val capW = last.capW.copyOf()
        val capB = last.capB.copyOf()
        if (captured != 0) {
            if (captured > 0) capW[captured]++ else capB[-captured]++
        }
        snaps.add(Snap(np, from, to, capW, capB, np.hash(), m))
        pos = np
        refresh()
        lastCapSq = capSq
        return captured
    }

    private var lastCapSq = -1

    private fun playMove(m: Int) {
        if (puzzleMode) {
            puzzleMove(m)
            return
        }
        val white = pos.white
        val captured = advance(m)
        animT = 0f
        if (captured != 0) ghosts.add(Ghost(lastCapSq, captured, 16))
        setClock(sideToMove())

        sfx.play(if (captured != 0) Sfx.Kind.CAPTURE else Sfx.Kind.MOVE)
        haptic()

        // game end / check detection
        val mover = if (white) 1 else 2
        val mate = legal.isEmpty()
        if (mate && checkSq >= 0) {
            beginFinish(mover, "r_checkmate")
        } else if (mate) {
            beginFinish(3, "r_stalemate")
        } else if (ChessRules.insufficient(pos)) {
            beginFinish(3, "r_insufficient")
        } else if (pos.half >= 100) {
            beginFinish(3, "r_fifty")
        } else if (repetitions() >= 3) {
            beginFinish(3, "r_repetition")
        } else {
            if (checkSq >= 0) sfx.play(Sfx.Kind.CHECK)
            maybeAiMove()
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
        // "Blitz attack": the human checkmates the computer within 20 of their own moves
        if (vsAi && pendingReason == "r_checkmate" && res == humanSide + 1) {
            val plies = snaps.size - 1
            if ((plies + 1 - humanSide) / 2 <= 20) Achievements.unlock(store, "chess_fast")
        }
        finishGame(res, pendingReason)
    }

    // ------------------------- saving and continuing -------------------------

    /** "C1;m1,m2,..." - every move since the start position, in the engine's move encoding. */
    override fun saveState(): String? {
        if (puzzleMode || pendingRes != 0 || snaps.size < 2) return null
        val sb = StringBuilder("C1;")
        for (i in 1 until snaps.size) {
            if (i > 1) sb.append(',')
            sb.append(snaps[i].mv)
        }
        return sb.toString()
    }

    override fun loadState(s: String): Boolean {
        val parts = s.trim().split(';')
        if (parts.size != 2 || parts[0] != "C1" || parts[1].isEmpty()) return false
        val list = ArrayList<Int>()
        for (tok in parts[1].split(',')) list.add(tok.toIntOrNull() ?: return false)

        // replay from the start position; every move must be legal in its position
        pos = ChessPos.start()
        snaps.clear()
        snaps.add(Snap(pos, -1, -1, IntArray(7), IntArray(7), pos.hash()))
        for (m in list) {
            if (!ChessRules.legal(pos).contains(m)) return resetAfterBadLoad()
            advance(m)
        }
        // a finished game is never saved
        if (legal.isEmpty() || ChessRules.insufficient(pos) || pos.half >= 100 || repetitions() >= 3) return resetAfterBadLoad()

        flip = vsAi && humanSide == 1
        pendingRes = 0
        refresh()
        setClock(sideToMove())
        maybeAiMove()
        return true
    }

    private fun resetAfterBadLoad(): Boolean {
        pos = ChessPos.start()
        snaps.clear()
        snaps.add(Snap(pos, -1, -1, IntArray(7), IntArray(7), pos.hash()))
        refresh()
        return false
    }

    override fun onHostPause() {
        flushPending()
        super.onHostPause()
    }

    // ------------------------------- input ----------------------------------

    private fun humanCanMove(): Boolean =
        if (puzzleMode) (!puzDone && !thinking && pos.white == puzWhite) else
        phase == Phase.PLAY && !thinking && pendingRes == 0 && isHumanTurn(sideToMove())

    private fun squareAt(x: Float, y: Float): Int {
        if (x < BX || y < BY || x >= BX + 8 * S || y >= BY + 8 * S) return -1
        val dc = ((x - BX) / S).toInt().coerceIn(0, 7)
        val dr = ((y - BY) / S).toInt().coerceIn(0, 7)
        val c = if (flip) 7 - dc else dc
        val r = if (flip) 7 - dr else dr
        return r * 8 + c
    }

    private fun cx(sq: Int): Float {
        val c = sq and 7
        return BX + ((if (flip) 7 - c else c) + 0.5f) * S
    }

    private fun cy(sq: Int): Float {
        val r = sq shr 3
        return BY + ((if (flip) 7 - r else r) + 0.5f) * S
    }

    override fun onPointerDown(x: Float, y: Float) {
        if (reviewMode) {
            graphDrag = y in 128f..170f
            if (graphDrag) graphTap(x)
            return
        }
        if (promoFrom >= 0 && phase == Phase.PLAY) {
            handlePromoTap(x, y)
            return
        }
        if (!humanCanMove()) return
        val sq = squareAt(x, y)
        if (sq < 0) {
            selected = -1
            selMoves = IntArray(0)
            return
        }
        if (selected >= 0) {
            var chosen = 0
            for (m in selMoves) if (mvTo(m) == sq) chosen = m
            if (chosen != 0) {
                if (mvPromo(chosen) != 0) {
                    promoFrom = selected
                    promoTo = sq
                } else {
                    playMove(chosen)
                }
                return
            }
        }
        val pc = pos.b[sq]
        if (pc != 0 && (pc > 0) == pos.white) {
            selected = sq
            selMoves = legal.filter { mvFrom(it) == sq }.toIntArray()
            hintMove = 0
            sfx.play(Sfx.Kind.CLICK, 0.4f)
        } else {
            selected = -1
            selMoves = IntArray(0)
        }
    }

    override fun onPointerMove(x: Float, y: Float) {
        if (reviewMode && graphDrag) graphTap(x)
    }

    override fun onPointerUp(x: Float, y: Float) {
        graphDrag = false
    }

    private fun promoRectLeft(i: Int) = W / 2f - 150f + i * 78f + 6f

    private fun handlePromoTap(px: Float, py: Float) {
        val x = if (promoTurned) W - px else px
        val y = if (promoTurned) 750f - py else py
        for (i in promoTypes.indices) {
            val l = promoRectLeft(i)
            if (x >= l && x <= l + 66f && y >= 350f && y <= 416f) {
                val type = promoTypes[i]
                var chosen = 0
                for (m in selMoves) if (mvTo(m) == promoTo && mvPromo(m) == type) chosen = m
                if (chosen != 0) playMove(chosen)
                return
            }
        }
        // tap outside cancels
        promoFrom = -1
        promoTo = -1
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
        runAi(0L, { ChessAI(4, 1500L, 0).bestMove(snapPos) }) { m ->
            if (phase == Phase.PLAY && m != 0 && legal.contains(m)) {
                hintMove = m
                selected = -1
                selMoves = IntArray(0)
            }
        }
    }

    // ------------------------------- update ---------------------------------

    override fun onUpdate() {
        if (puzMsgT > 0) puzMsgT--
        if (animT < 1f) animT = min(1f, animT + 0.09f)
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
        if (pendingRes != 0) {
            return if (pendingRes == 3) t(pendingReason) else t("r_checkmate")
        }
        if (checkSq >= 0) return t("check_excl")
        return if (vsAi) {
            if (isHumanTurn(sideToMove())) t("your_turn_as", sideName(humanSide)) else ""
        } else {
            t("turn_of", sideName(sideToMove()))
        }
    }

    // ------------------------------- puzzles -------------------------------

    private var puzWhite = true          // the side the player solves for (the side to move in the puzzle)

    private fun puzzlesSolved(): Int {
        val s = store.getS("cp_solved") ?: return 0
        return s.count { it == '1' }
    }

    private fun puzzleSolvedFlag(i: Int): Boolean {
        val s = store.getS("cp_solved") ?: return false
        return i < s.length && s[i] == '1'
    }

    private fun markPuzzleSolved(i: Int) {
        val n = ChessPuzzles.all.size
        val old = store.getS("cp_solved") ?: ""
        val sb = StringBuilder(old)
        while (sb.length < n) sb.append('0')
        sb.setCharAt(i, '1')
        store.putS("cp_solved", sb.toString())
    }

    private fun enterPuzzles() {
        if (phase != Phase.SETUP) return
        puzzleMode = true
        phase = Phase.PLAY
        stopClock()
        var start = store.getI("cp_cur", 0).coerceIn(0, ChessPuzzles.all.size - 1)
        // continue with the first unsolved puzzle when the remembered one is already solved
        if (puzzleSolvedFlag(start)) {
            for (k in 1..ChessPuzzles.all.size) {
                val j = (start + k) % ChessPuzzles.all.size
                if (!puzzleSolvedFlag(j)) {
                    start = j
                    break
                }
            }
        }
        loadPuzzle(start)
    }

    private fun exitPuzzles() {
        puzzleMode = false
        cancelAi()
        phase = Phase.SETUP
        resetAfterBadLoad()
        flip = false
        selected = -1
        selMoves = IntArray(0)
        hintMove = 0
        puzMsg = ""
    }

    private fun loadPuzzle(i: Int) {
        cancelAi()
        puzIdx = i.coerceIn(0, ChessPuzzles.all.size - 1)
        store.putI("cp_cur", puzIdx)
        val pz = ChessPuzzles.all[puzIdx]
        val p = ChessFen.parse(pz.fen) ?: ChessPos.start()
        pos = p
        snaps.clear()
        snaps.add(Snap(p, -1, -1, IntArray(7), IntArray(7), p.hash()))
        refresh()
        puzWhite = p.white
        flip = !p.white
        puzLeft = pz.mateIn
        puzFirst = true
        puzDone = false
        puzHinted = false
        puzMsg = ""
        puzMsgT = 0
        selected = -1
        selMoves = IntArray(0)
        promoFrom = -1
        promoTo = -1
        hintMove = 0
    }

    private fun puzzleNote(s: String, bad: Boolean) {
        puzMsg = s
        puzMsgBad = bad
        puzMsgT = 150
    }

    /** The player tried [m] in a puzzle. */
    private fun puzzleMove(m: Int) {
        selected = -1
        selMoves = IntArray(0)
        promoFrom = -1
        promoTo = -1
        hintMove = 0
        val q = ChessRules.make(pos, m)
        val mated = ChessRules.legal(q).isEmpty() && ChessRules.inCheck(q)
        val good = when {
            mated -> true
            puzLeft < 2 -> false
            puzFirst -> m == ChessSan.parse(pos, ChessPuzzles.all[puzIdx].first)
            else -> ChessMate.defenderLoses(q, puzLeft - 1)
        }
        if (!good) {
            shake = 2f
            sfx.play(Sfx.Kind.HIT)
            puzzleNote(t("chx_p_wrong"), true)
            return
        }
        val captured = advance(m)
        animT = 0f
        if (captured != 0) ghosts.add(Ghost(lastCapSq, captured, 16))
        sfx.play(if (captured != 0) Sfx.Kind.CAPTURE else Sfx.Kind.MOVE)
        haptic()
        puzFirst = false
        if (mated) {
            puzDone = true
            sfx.play(Sfx.Kind.WIN)
            confetti()
            haptic(true)
            if (puzHinted) {
                puzzleNote(t("chx_p_solved_hint"), false)
            } else {
                puzzleNote(t("chx_p_solved"), false)
                if (!puzzleSolvedFlag(puzIdx)) {
                    markPuzzleSolved(puzIdx)
                    val n = Achievements.addCounter(store, "puzzles")
                    if (n >= 10) Achievements.unlock(store, "chess_puzzles")
                }
            }
            return
        }
        puzzleNote(t("chx_p_ok"), false)
        val left = puzLeft - 1
        val gen = puzIdx
        runAi(450L, { ChessMate.bestDefense(q, left) }) { r ->
            if (puzzleMode && gen == puzIdx && r != 0 && ChessRules.legal(pos).contains(r)) {
                val cap = advance(r)
                animT = 0f
                if (cap != 0) ghosts.add(Ghost(lastCapSq, cap, 16))
                sfx.play(if (cap != 0) Sfx.Kind.CAPTURE else Sfx.Kind.MOVE)
                puzLeft = left
            }
        }
    }

    private fun puzzleHint() {
        if (puzDone) return
        val m = if (puzFirst) ChessSan.parse(pos, ChessPuzzles.all[puzIdx].first)
        else ChessMate.solutions(pos, puzLeft).firstOrNull() ?: 0
        if (m != 0) {
            hintMove = m
            puzHinted = true
        }
    }

    private fun drawPuzzleScreen(c: Canvas) {
        drawBackdrop(c)
        text(c, t("chx_puzzles"), W / 2f + 20f, 62f, 24f, Ui.TEXT, maxW = 280f, shadow = false)
        val pz = ChessPuzzles.all[puzIdx]
        val solvedBefore = puzzleSolvedFlag(puzIdx)
        text(c, t("chx_p_title", pz.mateIn), W / 2f, 104f, 22f, if (solvedBefore || puzDone) Ui.OK else accent, maxW = 300f, shadow = false)
        val line2 = if (puzMsgT > 0 && puzMsg.isNotEmpty()) puzMsg
        else t("chx_p_turn", if (puzWhite) t("white") else t("black")) + "   ·   " + t("chx_p_no", puzIdx + 1, ChessPuzzles.all.size)
        val col2 = if (puzMsgT > 0 && puzMsg.isNotEmpty()) (if (puzMsgBad) Ui.DANGER else Ui.OK) else Ui.MUTED
        plainText(c, line2, W / 2f, 134f, 15f, col2, 0, 440f)
        drawGame(c)
        // solved marks on the progress strip under the board
        val n = ChessPuzzles.all.size
        val gap = 432f / n
        for (i in 0 until n) {
            val x = 24f + gap * (i + 0.5f)
            val col = if (i == puzIdx) accent else if (puzzleSolvedFlag(i)) Ui.OK else Ui.BORDER
            fillOval(c, x - 2.5f, 626f - 2.5f, x + 2.5f, 626f + 2.5f, col)
        }
        for (b in pzAll) drawButton(c, b)
    }

    // ------------------------------- share / review -------------------------

    private fun shareGame() {
        val res = when (result) {
            1 -> "1-0"
            2 -> "0-1"
            3 -> "1/2-1/2"
            else -> "*"
        }
        val white: String
        val black: String
        if (!vsAi) {
            white = "White"
            black = "Black"
        } else if (humanSide == 0) {
            white = "Player"
            black = "Computer"
        } else {
            white = "Computer"
            black = "Player"
        }
        val date = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US).format(java.util.Date())
        host.shareText(ChessPgn.build(history(), white, black, res, date))
    }

    private fun startReview() {
        if (phase != Phase.OVER) return
        val n = snaps.size - 1
        reviewMode = true
        rv = null
        rvProgress = 0
        graphDrag = false
        val gen = ++rvGen
        rvSans = Array(n) { ChessSan.san(snaps[it].pos, snaps[it + 1].mv) }
        val moves = history()
        rvGo(0)
        runAi(0L, {
            ChessReview.analyse(moves, progress = { rvProgress = it }, cancelled = { gen != rvGen })
        }) { r ->
            if (gen == rvGen) rv = r
        }
    }

    private fun exitReview() {
        reviewMode = false
        rvGen++
        cancelAi()
        pos = snaps[snaps.size - 1].pos
        refresh()
    }

    /** Shows the position after [i] moves. */
    private fun rvGo(i: Int) {
        rvIdx = i.coerceIn(0, snaps.size - 1)
        val sn = snaps[rvIdx]
        pos = sn.pos
        legal = IntArray(0)
        selected = -1
        selMoves = IntArray(0)
        promoFrom = -1
        promoTo = -1
        hintMove = 0
        val k = ChessRules.findKing(pos.b, pos.white)
        checkSq = if (k >= 0 && ChessRules.inCheck(pos)) k else -1
        animFrom = sn.from
        animTo = sn.to
        animT = 1f
        ghosts.clear()
    }

    /** Position index of the next ([dir] = 1) or previous (-1) marked move, or -1. */
    private fun findMark(dir: Int): Int {
        val r = rv ?: return -1
        if (dir > 0) {
            for (k in rvIdx until r.mark.size) if (r.mark[k] > 0) return k + 1
        } else {
            for (k in rvIdx - 2 downTo 0) if (r.mark[k] > 0) return k + 1
        }
        return -1
    }

    private fun graphTap(x: Float) {
        val n = snaps.size - 1
        if (n <= 0) return
        val f = ((x - GX0) / (GX1 - GX0)).coerceIn(0f, 1f)
        val i = (f * n).roundToInt()
        if (i != rvIdx) rvGo(i)
    }

    private fun markColor(m: Int): Int = when (m) {
        3 -> Color.rgb(232, 72, 62)
        2 -> Color.rgb(240, 140, 48)
        else -> Color.rgb(236, 200, 60)
    }

    private fun markName(m: Int): String = when (m) {
        3 -> t("chx_blunder")
        2 -> t("chx_mistake")
        else -> t("chx_inacc")
    }

    private fun markSymbol(m: Int): String = when (m) {
        3 -> "??"
        2 -> "?"
        else -> "?!"
    }

    private fun evalText(cp: Int): String {
        if (cp >= 1900) return "+M"
        if (cp <= -1900) return "-M"
        val v = cp / 100f
        return (if (v > 0.04f) "+" else "") + "%.1f".format(java.util.Locale.US, v)
    }

    private fun drawReviewMarks(c: Canvas) {
        val r = rv ?: return
        if (rvIdx <= 0) return
        val k = rvIdx - 1
        val m = r.mark[k]
        if (m <= 0) return
        val b = r.best[k]
        if (b != 0) drawArrow(c, mvFrom(b), mvTo(b), withA(Color.rgb(60, 200, 120), 0.85f))
        // badge on the square the moved piece landed on
        val bx = cx(animTo) + S * 0.34f
        val by = cy(animTo) - S * 0.34f
        fillOval(c, bx - 10f, by - 10f, bx + 10f, by + 10f, markColor(m))
        strokeOval(c, bx - 10f, by - 10f, bx + 10f, by + 10f, Color.argb(200, 20, 20, 20), 1.5f)
        plainText(c, markSymbol(m), bx, by + 4f, 11f, Color.rgb(24, 24, 24), 0, 18f)
    }

    private fun drawArrow(c: Canvas, from: Int, to: Int, color: Int) {
        val x0 = cx(from)
        val y0 = cy(from)
        val x1 = cx(to)
        val y1 = cy(to)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        val ux = dx / len
        val uy = dy / len
        val head = 17f
        line.color = color
        line.strokeWidth = 7f
        c.drawLine(x0, y0, x1 - ux * head * 0.6f, y1 - uy * head * 0.6f, line)
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x1 - ux * head - uy * 10f, y1 - uy * head + ux * 10f)
        path.lineTo(x1 - ux * head + uy * 10f, y1 - uy * head - ux * 10f)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun drawReviewScreen(c: Canvas) {
        drawBackdrop(c)
        text(c, t("chx_review"), W / 2f + 20f, 62f, 24f, Ui.TEXT, maxW = 280f, shadow = false)

        val r = rv
        val n = snaps.size - 1
        var l1: String
        var l2 = ""
        var col1 = Ui.TEXT
        if (rvIdx == 0) {
            l1 = t("chx_start")
            if (r != null) {
                l2 = t("chx_sum", t("white"), r.count(0, 3), r.count(0, 2), r.count(0, 1)) + "     " +
                    t("chx_sum", t("black"), r.count(1, 3), r.count(1, 2), r.count(1, 1))
            }
        } else {
            val k = rvIdx - 1
            l1 = (if (k % 2 == 0) "${k / 2 + 1}. " else "${k / 2 + 1}... ") + rvSans[k]
            if (r != null) {
                val m = r.mark[k]
                if (m > 0) {
                    l1 += "   " + markName(m)
                    col1 = markColor(m)
                    val b = r.best[k]
                    if (b != 0) l2 = t("chx_better", ChessSan.san(snaps[k].pos, b))
                } else {
                    l2 = t("chx_eval", evalText(r.score[rvIdx]))
                }
            }
        }
        if (r == null && thinking) l2 = t("chx_analyzing", rvProgress)
        val op = openingId
        if (rvIdx == n && op != null && r != null && l2.isEmpty()) l2 = t("chx_op_$op")
        text(c, l1, W / 2f, 104f, 20f, col1, maxW = 440f, shadow = false)
        if (l2.isNotEmpty()) plainText(c, l2, W / 2f, 126f, 14f, Ui.MUTED, 0, 440f)

        drawGraph(c, r, n)
        drawGame(c)
        for (b in rvAll) drawButton(c, b)
    }

    private fun drawGraph(c: Canvas, r: ReviewResult?, n: Int) {
        fillRRect(c, 24f, 132f, 456f, 166f, 10f, Ui.SURFACE)
        strokeRRect(c, 24f, 132f, 456f, 166f, 10f, Ui.BORDER, 1.5f)
        val mid = 149f
        line.color = withA(Ui.MUTED, 0.35f)
        line.strokeWidth = 1f
        c.drawLine(GX0, mid, GX1, mid, line)
        if (r == null) {
            if (thinking) {
                fillRRect(c, GX0, mid - 3f, GX0 + (GX1 - GX0) * rvProgress / 100f, mid + 3f, 3f, accent)
            }
            return
        }
        fun gx(i: Int): Float = if (n <= 0) GX0 else GX0 + (GX1 - GX0) * i / n
        fun gy(i: Int): Float = mid - clampF(r.score[i].toFloat(), -500f, 500f) / 500f * 13f
        line.color = accent
        line.strokeWidth = 2f
        for (i in 1..n) c.drawLine(gx(i - 1), gy(i - 1), gx(i), gy(i), line)
        for (k in 0 until n) {
            val m = r.mark[k]
            if (m > 0) fillOval(c, gx(k + 1) - 3.5f, gy(k + 1) - 3.5f, gx(k + 1) + 3.5f, gy(k + 1) + 3.5f, markColor(m))
        }
        line.color = Ui.TEXT
        line.strokeWidth = 2f
        c.drawLine(gx(rvIdx), 136f, gx(rvIdx), 162f, line)
    }

    // ------------------------------- drawing --------------------------------

    override fun drawScreen(c: Canvas) {
        if (puzzleMode) drawPuzzleScreen(c) else if (reviewMode) drawReviewScreen(c) else super.drawScreen(c)
    }

    override fun drawSetupArt(c: Canvas, cx: Float, cy: Float) {
        PieceArt.draw(c, KNIGHT, true, cx - 52f, cy, 92f)
        PieceArt.draw(c, QUEEN, false, cx + 52f, cy, 92f)
    }

    override fun drawGame(c: Canvas) {
        // frame and squares
        fillRRect(c, BX - 10f, BY - 10f, BX + 8 * S + 10f, BY + 8 * S + 10f, 14f, Ui.SURFACE)
        strokeRRect(c, BX - 10f, BY - 10f, BX + 8 * S + 10f, BY + 8 * S + 10f, 14f, Ui.BORDER, 1.5f)
        for (dr in 0..7) for (dc in 0..7) {
            val light = (dr + dc) % 2 == 0
            fillRect(c, BX + dc * S, BY + dr * S, BX + (dc + 1) * S, BY + (dr + 1) * S, if (light) LIGHT_SQ else DARK_SQ)
        }

        // last move, selection, check, hint
        if (animFrom >= 0) {
            tint(c, animFrom, Color.argb(95, 255, 214, 60))
            tint(c, animTo, Color.argb(120, 255, 214, 60))
        }
        if (selected >= 0) tint(c, selected, Color.argb(150, 70, 140, 255))
        if (checkSq >= 0) {
            fillOval(c, cx(checkSq) - S * 0.5f, cy(checkSq) - S * 0.5f, cx(checkSq) + S * 0.5f, cy(checkSq) + S * 0.5f, Color.argb(150, 235, 60, 50))
        }
        if (hintMove != 0) {
            val a = 0.45f + 0.3f * (0.5f + 0.5f * sin(tick * 0.15f))
            tint(c, mvFrom(hintMove), withA(Color.rgb(190, 90, 255), a))
            tint(c, mvTo(hintMove), withA(Color.rgb(190, 90, 255), a))
        }

        // coordinates
        for (i in 0..7) {
            val file = if (flip) 7 - i else i
            val rank = if (flip) i + 1 else 8 - i
            val onLightBottom = (7 + i) % 2 == 0
            plainText(c, ('a' + file).toString(), BX + (i + 1) * S - 7f, BY + 8 * S - 4f, 11f, if (onLightBottom) DARK_SQ else LIGHT_SQ, 1)
            val onLightLeft = i % 2 == 0
            plainText(c, rank.toString(), BX + 4f, BY + i * S + 12f, 11f, if (onLightLeft) DARK_SQ else LIGHT_SQ, -1)
        }

        // pieces
        val moving = if (animT < 1f && animTo >= 0) animTo else -1
        for (sq in 0 until 64) {
            val pc = pos.b[sq]
            if (pc == 0 || sq == moving) continue
            drawPc(c, abs(pc), pc > 0, cx(sq), cy(sq), S * 0.9f)
        }
        for (g in ghosts) {
            drawPc(c, abs(g.piece), g.piece > 0, cx(g.sq), cy(g.sq), S * 0.9f, g.life / 16f)
        }
        if (moving >= 0) {
            val e = easeOutCubic(animT)
            val px = lerp(cx(animFrom), cx(animTo), e)
            val py = lerp(cy(animFrom), cy(animTo), e)
            val pc = pos.b[animTo]
            drawPc(c, abs(pc), pc > 0, px, py, S * 0.96f)
        }

        // legal move markers
        for (m in selMoves) {
            val to = mvTo(m)
            if (mvPromo(m) != 0 && mvPromo(m) != QUEEN) continue
            val capture = pos.b[to] != 0 || ChessRules.isCapture(pos, m)
            if (capture) {
                strokeOval(c, cx(to) - S * 0.44f, cy(to) - S * 0.44f, cx(to) + S * 0.44f, cy(to) + S * 0.44f, Color.argb(170, 60, 60, 60), 5f)
            } else {
                fillOval(c, cx(to) - 8f, cy(to) - 8f, cx(to) + 8f, cy(to) + 8f, Color.argb(130, 50, 60, 50))
            }
        }

        if (reviewMode) {
            drawReviewMarks(c)
        } else if (!puzzleMode) {
            drawCaptured(c)
            val op = openingId
            if (op != null) plainText(c, t("chx_op_$op"), BX + 8 * S, 143f, 13f, Ui.MUTED, 1, 260f)
        }
        if (promoFrom >= 0 && phase == Phase.PLAY) drawPromo(c)
    }

    /** Draws a piece; in "face to face" mode the black pieces are upside down (they face the second player). */
    private fun drawPc(c: Canvas, type: Int, white: Boolean, x: Float, y: Float, size: Float, alpha: Float = 1f) {
        if (f2f && !white && !puzzleMode) {
            c.save()
            c.translate(x, y)
            c.rotate(180f)
            c.translate(-x, -y)
            PieceArt.draw(c, type, white, x, y, size, alpha)
            c.restore()
        } else {
            PieceArt.draw(c, type, white, x, y, size, alpha)
        }
    }

    private fun tint(c: Canvas, sq: Int, color: Int) {
        fillRect(c, cx(sq) - S / 2f, cy(sq) - S / 2f, cx(sq) + S / 2f, cy(sq) + S / 2f, color)
    }

    private val pieceValue = intArrayOf(0, 1, 3, 3, 5, 9, 0)

    private fun drawCaptured(c: Canvas) {
        val last = snaps[snaps.size - 1]
        val bottomWhite = !flip
        // pieces captured BY the bottom player are of the opposite colour
        val byBottom = if (bottomWhite) last.capB else last.capW
        val byTop = if (bottomWhite) last.capW else last.capB
        val bottomPiecesWhite = !bottomWhite   // colour of the pieces in the bottom row
        drawCapRow(c, byBottom, bottomPiecesWhite, 641f)
        drawCapRow(c, byTop, !bottomPiecesWhite, 152f)
        var diff = 0
        for (i in 1..5) diff += (byBottom[i] - byTop[i]) * pieceValue[i]
        if (diff > 0) plainText(c, "+$diff", BX + 8 * S, 647f, 16f, Ui.MUTED, 1)
        if (diff < 0) plainText(c, "+${-diff}", BX + 8 * S, 158f, 16f, Ui.MUTED, 1)
    }

    private fun drawCapRow(c: Canvas, counts: IntArray, white: Boolean, y: Float) {
        var x = BX + 12f
        val order = intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT, PAWN)
        for (type in order) {
            for (k in 0 until counts[type]) {
                drawPc(c, type, white, x, y, 27f)
                x += 15f
            }
            if (counts[type] > 0) x += 6f
        }
    }

    /** In "face to face" mode the dialog of the black player is turned around (it is centred on (W/2, 375)). */
    private val promoTurned: Boolean get() = f2f && !puzzleMode && !pos.white

    private fun drawPromo(c: Canvas) {
        fillRect(c, -200f, -400f, W + 200f, 1600f, Color.argb(150, 0, 0, 0))
        c.save()
        if (promoTurned) {
            c.translate(W / 2f, 375f)
            c.rotate(180f)
            c.translate(-W / 2f, -375f)
        }
        drawPanel(c, W / 2f - 170f, 300f, W / 2f + 170f, 450f)
        plainText(c, t("promote"), W / 2f, 336f, 16f, Ui.MUTED, 0, 300f)
        val white = pos.white
        for (i in promoTypes.indices) {
            val l = promoRectLeft(i)
            fillRRect(c, l, 350f, l + 66f, 416f, 12f, Ui.SURFACE_HI)
            strokeRRect(c, l, 350f, l + 66f, 416f, 12f, accent, 1.5f)
            PieceArt.draw(c, promoTypes[i], white, l + 33f, 383f, 58f)
        }
        c.restore()
    }
}
