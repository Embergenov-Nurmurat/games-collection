package com.example.minigames.chess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.core.Button
import com.example.minigames.core.Host
import com.example.minigames.core.Sfx
import com.example.minigames.core.W
import com.example.minigames.core.easeOutCubic
import com.example.minigames.core.lerp
import com.example.minigames.core.t
import com.example.minigames.core.withA
import com.example.minigames.core.Ui
import com.example.minigames.table.TableGameView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private const val S = 54f           // square size
private const val BX = 24f          // board left
private const val BY = 178f         // board top

private val LIGHT_SQ = Color.rgb(236, 232, 220)
private val DARK_SQ = Color.rgb(116, 142, 128)

/** One position of the game with everything needed to undo and to display it. */
private class Snap(
    val pos: ChessPos,
    val from: Int,
    val to: Int,
    val capW: IntArray,     // white pieces captured so far, by type
    val capB: IntArray,     // black pieces captured so far, by type
    val hash: Long
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

    override fun sideName(side: Int): String = if (side == 0) t("white") else t("black")

    override val accent: Int get() = Color.rgb(98, 214, 164)

    private fun sideToMove(): Int = if (pos.white) 0 else 1

    override fun buttons(): List<Button> =
        if (promoFrom >= 0 && phase == Phase.PLAY) emptyList() else super.buttons()

    override fun onBack(): Boolean {
        if (!fading && promoFrom >= 0) {      // close the promotion dialog first
            promoFrom = -1
            promoTo = -1
            return true
        }
        return super.onBack()
    }

    // ------------------------------- game flow ------------------------------

    override fun onNewGame() {
        pos = ChessPos.start()
        snaps.clear()
        snaps.add(Snap(pos, -1, -1, IntArray(7), IntArray(7), pos.hash()))
        flip = vsAi && humanSide == 1
        pendingRes = 0
        refresh()
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
    }

    private fun maybeAiMove() {
        if (!vsAi || phase != Phase.PLAY || pendingRes != 0) return
        if (isHumanTurn(sideToMove())) return
        val snapPos = pos.copy()
        val lv = level
        runAi(500L, {
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

    private fun playMove(m: Int) {
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
        snaps.add(Snap(np, from, to, capW, capB, np.hash()))
        pos = np
        refresh()
        animT = 0f
        if (captured != 0) ghosts.add(Ghost(capSq, captured, 16))

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
    }

    // ------------------------------- input ----------------------------------

    private fun humanCanMove(): Boolean =
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
        if (promoFrom >= 0) {
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

    private fun promoRectLeft(i: Int) = W / 2f - 150f + i * 78f + 6f

    private fun handlePromoTap(x: Float, y: Float) {
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
        sfx.play(Sfx.Kind.MOVE)
    }

    override fun canHint(): Boolean = humanCanMove() && legal.isNotEmpty()

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
        if (animT < 1f) animT = min(1f, animT + 0.09f)
        var i = ghosts.size - 1
        while (i >= 0) {
            ghosts[i].life--
            if (ghosts[i].life <= 0) ghosts.removeAt(i)
            i--
        }
        if (pendingRes != 0 && phase == Phase.PLAY) {
            pendingT--
            if (pendingT <= 0) {
                val res = pendingRes
                pendingRes = 0
                finishGame(res, pendingReason)
            }
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

    // ------------------------------- drawing --------------------------------

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
            PieceArt.draw(c, abs(pc), pc > 0, cx(sq), cy(sq), S * 0.9f)
        }
        for (g in ghosts) {
            PieceArt.draw(c, abs(g.piece), g.piece > 0, cx(g.sq), cy(g.sq), S * 0.9f, g.life / 16f)
        }
        if (moving >= 0) {
            val e = easeOutCubic(animT)
            val px = lerp(cx(animFrom), cx(animTo), e)
            val py = lerp(cy(animFrom), cy(animTo), e)
            val pc = pos.b[animTo]
            PieceArt.draw(c, abs(pc), pc > 0, px, py, S * 0.96f)
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

        drawCaptured(c)
        if (promoFrom >= 0) drawPromo(c)
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
                PieceArt.draw(c, type, white, x, y, 27f)
                x += 15f
            }
            if (counts[type] > 0) x += 6f
        }
    }

    private fun drawPromo(c: Canvas) {
        fillRect(c, -200f, -400f, W + 200f, 1600f, Color.argb(150, 0, 0, 0))
        drawPanel(c, W / 2f - 170f, 300f, W / 2f + 170f, 450f)
        plainText(c, t("promote"), W / 2f, 336f, 16f, Ui.MUTED, 0, 300f)
        val white = pos.white
        for (i in promoTypes.indices) {
            val l = promoRectLeft(i)
            fillRRect(c, l, 350f, l + 66f, 416f, 12f, Ui.SURFACE_HI)
            strokeRRect(c, l, 350f, l + 66f, 416f, 12f, accent, 1.5f)
            PieceArt.draw(c, promoTypes[i], white, l + 33f, 383f, 58f)
        }
    }
}
