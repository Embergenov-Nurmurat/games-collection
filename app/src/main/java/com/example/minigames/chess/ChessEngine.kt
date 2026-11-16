package com.example.minigames.chess

import kotlin.math.abs
import kotlin.random.Random

/*
 * Pure-Kotlin chess engine (no Android classes), so it can be unit-tested on the JVM.
 *
 * Board: index = row * 8 + col, row 0 = rank 8 (top), col 0 = file a.
 * Pieces: 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king; white > 0, black < 0.
 */

const val PAWN = 1
const val KNIGHT = 2
const val BISHOP = 3
const val ROOK = 4
const val QUEEN = 5
const val KING = 6

private const val F_EP = 1
private const val F_CASTLE = 2
private const val F_DOUBLE = 4

const val C_WK = 1
const val C_WQ = 2
const val C_BK = 4
const val C_BQ = 8

fun mvFrom(m: Int) = m and 63
fun mvTo(m: Int) = (m shr 6) and 63
fun mvPromo(m: Int) = (m shr 12) and 7
fun mvFlags(m: Int) = m shr 15
fun mkMove(from: Int, to: Int, promo: Int = 0, flags: Int = 0) =
    from or (to shl 6) or (promo shl 12) or (flags shl 15)

class ChessPos {
    val b = IntArray(64)
    var white = true
    var castle = 15
    var ep = -1
    var half = 0
    var full = 1

    fun copy(): ChessPos {
        val p = ChessPos()
        System.arraycopy(b, 0, p.b, 0, 64)
        p.white = white
        p.castle = castle
        p.ep = ep
        p.half = half
        p.full = full
        return p
    }

    /** 64-bit fingerprint for repetition detection. */
    fun hash(): Long {
        var h = -3750763034362895579L
        for (i in 0 until 64) {
            h = (h xor (b[i] + 8).toLong()) * 1099511628211L
        }
        h = (h xor castle.toLong()) * 1099511628211L
        h = (h xor (ep + 2).toLong()) * 1099511628211L
        h = (h xor (if (white) 1L else 2L)) * 1099511628211L
        return h
    }

    companion object {
        fun start(): ChessPos {
            val p = ChessPos()
            val back = intArrayOf(ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK)
            for (c in 0..7) {
                p.b[c] = -back[c]
                p.b[8 + c] = -PAWN
                p.b[48 + c] = PAWN
                p.b[56 + c] = back[c]
            }
            return p
        }
    }
}

object ChessRules {

    private val KN = intArrayOf(-2, -1, -2, 1, -1, -2, -1, 2, 1, -2, 1, 2, 2, -1, 2, 1)
    private val KG = intArrayOf(-1, -1, -1, 0, -1, 1, 0, -1, 0, 1, 1, -1, 1, 0, 1, 1)
    private val DIAG = intArrayOf(-1, -1, -1, 1, 1, -1, 1, 1)
    private val ORTH = intArrayOf(-1, 0, 1, 0, 0, -1, 0, 1)

    fun findKing(b: IntArray, white: Boolean): Int {
        val k = if (white) KING else -KING
        for (i in 0 until 64) if (b[i] == k) return i
        return -1
    }

    /** Is [sq] attacked by a piece of the given colour? */
    fun attacked(b: IntArray, sq: Int, byWhite: Boolean): Boolean {
        val r = sq shr 3
        val c = sq and 7
        val s = if (byWhite) 1 else -1

        // pawns (a white pawn attacks towards smaller rows, so it stands one row below the target)
        val pr = if (byWhite) r + 1 else r - 1
        if (pr in 0..7) {
            if (c > 0 && b[pr * 8 + c - 1] == s * PAWN) return true
            if (c < 7 && b[pr * 8 + c + 1] == s * PAWN) return true
        }
        // knights
        var i = 0
        while (i < 16) {
            val rr = r + KN[i]
            val cc = c + KN[i + 1]
            if (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == s * KNIGHT) return true
            i += 2
        }
        // king
        i = 0
        while (i < 16) {
            val rr = r + KG[i]
            val cc = c + KG[i + 1]
            if (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == s * KING) return true
            i += 2
        }
        // diagonal sliders
        i = 0
        while (i < 8) {
            var rr = r + DIAG[i]
            var cc = c + DIAG[i + 1]
            while (rr in 0..7 && cc in 0..7) {
                val pc = b[rr * 8 + cc]
                if (pc != 0) {
                    if (pc == s * BISHOP || pc == s * QUEEN) return true
                    break
                }
                rr += DIAG[i]
                cc += DIAG[i + 1]
            }
            i += 2
        }
        // straight sliders
        i = 0
        while (i < 8) {
            var rr = r + ORTH[i]
            var cc = c + ORTH[i + 1]
            while (rr in 0..7 && cc in 0..7) {
                val pc = b[rr * 8 + cc]
                if (pc != 0) {
                    if (pc == s * ROOK || pc == s * QUEEN) return true
                    break
                }
                rr += ORTH[i]
                cc += ORTH[i + 1]
            }
            i += 2
        }
        return false
    }

    fun inCheck(p: ChessPos, white: Boolean = p.white): Boolean {
        val k = findKing(p.b, white)
        return k >= 0 && attacked(p.b, k, !white)
    }

    private fun addPromos(out: IntArray, n0: Int, from: Int, to: Int): Int {
        var n = n0
        out[n++] = mkMove(from, to, QUEEN)
        out[n++] = mkMove(from, to, ROOK)
        out[n++] = mkMove(from, to, BISHOP)
        out[n++] = mkMove(from, to, KNIGHT)
        return n
    }

    /** Pseudo-legal moves (may leave the king in check). [capsOnly] = captures and promotions only. */
    fun pseudo(p: ChessPos, out: IntArray, capsOnly: Boolean): Int {
        var n = 0
        val b = p.b
        val white = p.white
        for (sq in 0 until 64) {
            val pc = b[sq]
            if (pc == 0 || (pc > 0) != white) continue
            val r = sq shr 3
            val c = sq and 7
            when (abs(pc)) {
                PAWN -> {
                    val dr = if (white) -1 else 1
                    val startRow = if (white) 6 else 1
                    val promoRow = if (white) 0 else 7
                    val r1 = r + dr
                    if (!capsOnly || r1 == promoRow) {
                        if (b[r1 * 8 + c] == 0) {
                            if (r1 == promoRow) {
                                n = addPromos(out, n, sq, r1 * 8 + c)
                            } else {
                                out[n++] = mkMove(sq, r1 * 8 + c)
                                if (!capsOnly && r == startRow && b[(r + 2 * dr) * 8 + c] == 0) {
                                    out[n++] = mkMove(sq, (r + 2 * dr) * 8 + c, 0, F_DOUBLE)
                                }
                            }
                        }
                    }
                    var dc = -1
                    while (dc <= 1) {
                        val cc = c + dc
                        if (cc in 0..7) {
                            val to = r1 * 8 + cc
                            val tp = b[to]
                            if (tp != 0 && (tp > 0) != white) {
                                if (r1 == promoRow) n = addPromos(out, n, sq, to)
                                else out[n++] = mkMove(sq, to)
                            } else if (tp == 0 && to == p.ep) {
                                out[n++] = mkMove(sq, to, 0, F_EP)
                            }
                        }
                        dc += 2
                    }
                }
                KNIGHT -> {
                    var i = 0
                    while (i < 16) {
                        val rr = r + KN[i]
                        val cc = c + KN[i + 1]
                        if (rr in 0..7 && cc in 0..7) {
                            val tp = b[rr * 8 + cc]
                            if (tp == 0) {
                                if (!capsOnly) out[n++] = mkMove(sq, rr * 8 + cc)
                            } else if ((tp > 0) != white) {
                                out[n++] = mkMove(sq, rr * 8 + cc)
                            }
                        }
                        i += 2
                    }
                }
                KING -> {
                    var i = 0
                    while (i < 16) {
                        val rr = r + KG[i]
                        val cc = c + KG[i + 1]
                        if (rr in 0..7 && cc in 0..7) {
                            val tp = b[rr * 8 + cc]
                            if (tp == 0) {
                                if (!capsOnly) out[n++] = mkMove(sq, rr * 8 + cc)
                            } else if ((tp > 0) != white) {
                                out[n++] = mkMove(sq, rr * 8 + cc)
                            }
                        }
                        i += 2
                    }
                    if (!capsOnly) n = castling(p, sq, out, n)
                }
                else -> {
                    val a = abs(pc)
                    val bishopLike = a == BISHOP || a == QUEEN
                    val rookLike = a == ROOK || a == QUEEN
                    if (bishopLike) n = slide(b, white, sq, DIAG, out, n, capsOnly)
                    if (rookLike) n = slide(b, white, sq, ORTH, out, n, capsOnly)
                }
            }
        }
        return n
    }

    private fun slide(b: IntArray, white: Boolean, sq: Int, dirs: IntArray, out: IntArray, n0: Int, capsOnly: Boolean): Int {
        var n = n0
        val r = sq shr 3
        val c = sq and 7
        var i = 0
        while (i < dirs.size) {
            var rr = r + dirs[i]
            var cc = c + dirs[i + 1]
            while (rr in 0..7 && cc in 0..7) {
                val tp = b[rr * 8 + cc]
                if (tp == 0) {
                    if (!capsOnly) out[n++] = mkMove(sq, rr * 8 + cc)
                } else {
                    if ((tp > 0) != white) out[n++] = mkMove(sq, rr * 8 + cc)
                    break
                }
                rr += dirs[i]
                cc += dirs[i + 1]
            }
            i += 2
        }
        return n
    }

    private fun castling(p: ChessPos, sq: Int, out: IntArray, n0: Int): Int {
        var n = n0
        val b = p.b
        if (p.white) {
            if (sq != 60) return n
            if (p.castle and (C_WK or C_WQ) == 0) return n
            if (attacked(b, 60, false)) return n
            if (p.castle and C_WK != 0 && b[63] == ROOK && b[61] == 0 && b[62] == 0 &&
                !attacked(b, 61, false) && !attacked(b, 62, false)
            ) out[n++] = mkMove(60, 62, 0, F_CASTLE)
            if (p.castle and C_WQ != 0 && b[56] == ROOK && b[57] == 0 && b[58] == 0 && b[59] == 0 &&
                !attacked(b, 59, false) && !attacked(b, 58, false)
            ) out[n++] = mkMove(60, 58, 0, F_CASTLE)
        } else {
            if (sq != 4) return n
            if (p.castle and (C_BK or C_BQ) == 0) return n
            if (attacked(b, 4, true)) return n
            if (p.castle and C_BK != 0 && b[7] == -ROOK && b[5] == 0 && b[6] == 0 &&
                !attacked(b, 5, true) && !attacked(b, 6, true)
            ) out[n++] = mkMove(4, 6, 0, F_CASTLE)
            if (p.castle and C_BQ != 0 && b[0] == -ROOK && b[1] == 0 && b[2] == 0 && b[3] == 0 &&
                !attacked(b, 3, true) && !attacked(b, 2, true)
            ) out[n++] = mkMove(4, 2, 0, F_CASTLE)
        }
        return n
    }

    private fun rightsLost(sq: Int): Int = when (sq) {
        60 -> C_WK or C_WQ
        4 -> C_BK or C_BQ
        63 -> C_WK
        56 -> C_WQ
        7 -> C_BK
        0 -> C_BQ
        else -> 0
    }

    /** Returns a new position with the move played (the move is assumed to be pseudo-legal). */
    fun make(p: ChessPos, m: Int): ChessPos {
        val q = p.copy()
        val from = mvFrom(m)
        val to = mvTo(m)
        val promo = mvPromo(m)
        val fl = mvFlags(m)
        val pc = q.b[from]
        val cap = q.b[to]
        q.b[from] = 0
        q.b[to] = if (promo != 0) (if (pc > 0) promo else -promo) else pc
        if (fl and F_EP != 0) q.b[(from and 56) or (to and 7)] = 0
        if (fl and F_CASTLE != 0) {
            if (to > from) {
                q.b[to - 1] = q.b[to + 1]
                q.b[to + 1] = 0
            } else {
                q.b[to + 1] = q.b[to - 2]
                q.b[to - 2] = 0
            }
        }
        q.ep = if (fl and F_DOUBLE != 0) (from + to) / 2 else -1
        q.castle = q.castle and (rightsLost(from) or rightsLost(to)).inv()
        q.half = if (abs(pc) == PAWN || cap != 0) 0 else p.half + 1
        if (!p.white) q.full = p.full + 1
        q.white = !p.white
        return q
    }

    fun isLegal(p: ChessPos, m: Int): Boolean {
        val q = make(p, m)
        val k = findKing(q.b, p.white)
        return k >= 0 && !attacked(q.b, k, !p.white)
    }

    fun legal(p: ChessPos): IntArray {
        val buf = IntArray(320)
        val n = pseudo(p, buf, false)
        val res = IntArray(n)
        var k = 0
        for (i in 0 until n) if (isLegal(p, buf[i])) res[k++] = buf[i]
        return res.copyOf(k)
    }

    fun isCapture(p: ChessPos, m: Int): Boolean =
        p.b[mvTo(m)] != 0 || (mvFlags(m) and F_EP != 0)

    fun isPromotion(m: Int) = mvPromo(m) != 0

    /** King (or king+single minor) vs king, or bishops all on the same colour: no mate possible. */
    fun insufficient(p: ChessPos): Boolean {
        var minors = 0
        var knights = 0
        var bishopsLight = 0
        var bishopsDark = 0
        for (i in 0 until 64) {
            val a = abs(p.b[i])
            when (a) {
                PAWN, ROOK, QUEEN -> return false
                KNIGHT -> {
                    minors++
                    knights++
                }
                BISHOP -> {
                    minors++
                    if (((i shr 3) + (i and 7)) % 2 == 0) bishopsLight++ else bishopsDark++
                }
            }
        }
        if (minors <= 1) return true
        if (knights == 0 && (bishopsLight == 0 || bishopsDark == 0)) return true
        return false
    }
}

// --------------------------------------------------------------------------
//  Search
// --------------------------------------------------------------------------

object ChessEval {
    val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 0)

    private val PST_P = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )
    private val PST_N = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )
    private val PST_B = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )
    private val PST_R = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0
    )
    private val PST_Q = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20
    )
    private val PST_K_MID = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20
    )
    private val PST_K_END = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50
    )
    private val PST = arrayOf(PST_P, PST_P, PST_N, PST_B, PST_R, PST_Q)

    private fun centerDist(sq: Int): Int {
        val r = sq shr 3
        val c = sq and 7
        return maxOf(3 - r, r - 4) + maxOf(3 - c, c - 4)
    }

    private fun kingDist(a: Int, b: Int) =
        abs((a shr 3) - (b shr 3)) + abs((a and 7) - (b and 7))

    /** Static evaluation from the point of view of the side to move (centipawns). */
    fun eval(p: ChessPos): Int {
        var score = 0
        var wk = 0
        var bk = 0
        var pieceMat = 0
        var wMat = 0
        var bMat = 0
        var wBishops = 0
        var bBishops = 0
        for (sq in 0 until 64) {
            val pc = p.b[sq]
            if (pc == 0) continue
            val a = abs(pc)
            if (a == KING) {
                if (pc > 0) wk = sq else bk = sq
                continue
            }
            val v = VAL[a]
            if (pc > 0) {
                score += v + PST[a][sq]
                wMat += v
                if (a == BISHOP) wBishops++
            } else {
                score -= v + PST[a][sq xor 56]
                bMat += v
                if (a == BISHOP) bBishops++
            }
            if (a != PAWN) pieceMat += v
        }
        if (wBishops >= 2) score += 30
        if (bBishops >= 2) score -= 30

        val loneBlack = bMat == 0 && wMat >= 300
        val loneWhite = wMat == 0 && bMat >= 300
        if (loneBlack) {
            // mop-up: drive the lone king to the edge and bring our king closer
            score += centerDist(bk) * 14 + (14 - kingDist(wk, bk)) * 8
        } else if (loneWhite) {
            score -= centerDist(wk) * 14 + (14 - kingDist(wk, bk)) * 8
        } else {
            val endgame = pieceMat <= 2600
            score += if (endgame) PST_K_END[wk] - PST_K_END[bk xor 56] else PST_K_MID[wk] - PST_K_MID[bk xor 56]
        }

        return if (p.white) score else -score
    }
}

class ChessAI(
    private val maxDepth: Int,
    private val timeMs: Long,
    private val margin: Int
) {
    private companion object {
        const val INF = 1_000_000
        const val MATE = 100_000
    }

    private var deadline = 0L
    private var aborted = false
    private var nodes = 0
    private val bufs = Array(48) { IntArray(320) }
    private val scs = Array(48) { IntArray(320) }

    private fun order(p: ChessPos, buf: IntArray, sc: IntArray, n: Int) {
        for (i in 0 until n) {
            val m = buf[i]
            val victim = abs(p.b[mvTo(m)])
            var s = 0
            if (victim != 0) s += ChessEval.VAL[victim] * 10 - ChessEval.VAL[abs(p.b[mvFrom(m)])] / 10
            else if (mvFlags(m) and F_EP != 0) s += 1000
            if (mvPromo(m) != 0) s += ChessEval.VAL[mvPromo(m)] * 8
            sc[i] = s
        }
    }

    private fun pick(buf: IntArray, sc: IntArray, from: Int, n: Int) {
        var best = from
        for (i in from + 1 until n) if (sc[i] > sc[best]) best = i
        if (best != from) {
            val tm = buf[from]; buf[from] = buf[best]; buf[best] = tm
            val ts = sc[from]; sc[from] = sc[best]; sc[best] = ts
        }
    }

    private fun checkTime() {
        if ((++nodes and 1023) == 0 && System.nanoTime() > deadline) aborted = true
    }

    private fun quiesce(p: ChessPos, alphaIn: Int, beta: Int, ply: Int, qd: Int): Int {
        checkTime()
        if (aborted) return 0
        var alpha = alphaIn
        val stand = ChessEval.eval(p)
        if (stand >= beta) return stand
        if (stand > alpha) alpha = stand
        if (qd >= 6 || ply >= 46) return alpha
        val buf = bufs[ply]
        val sc = scs[ply]
        val n = ChessRules.pseudo(p, buf, true)
        order(p, buf, sc, n)
        for (i in 0 until n) {
            pick(buf, sc, i, n)
            val q = ChessRules.make(p, buf[i])
            val k = ChessRules.findKing(q.b, p.white)
            if (k < 0 || ChessRules.attacked(q.b, k, !p.white)) continue
            val v = -quiesce(q, -beta, -alpha, ply + 1, qd + 1)
            if (aborted) return 0
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    private fun negamax(p: ChessPos, depthIn: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        checkTime()
        if (aborted) return 0
        if (p.half >= 100) return 0
        var depth = depthIn
        if (depth <= 0) {
            // check extension: a mate delivered at the horizon must be seen
            if (ply < 40 && ChessRules.inCheck(p)) depth = 1
            else return quiesce(p, alphaIn, beta, ply, 0)
        }
        if (ply >= 44) return quiesce(p, alphaIn, beta, ply, 0)
        var alpha = alphaIn
        val buf = bufs[ply]
        val sc = scs[ply]
        val n = ChessRules.pseudo(p, buf, false)
        order(p, buf, sc, n)
        var legal = 0
        var best = -INF
        for (i in 0 until n) {
            pick(buf, sc, i, n)
            val q = ChessRules.make(p, buf[i])
            val k = ChessRules.findKing(q.b, p.white)
            if (k < 0 || ChessRules.attacked(q.b, k, !p.white)) continue
            legal++
            val v = -negamax(q, depth - 1, -beta, -alpha, ply + 1)
            if (aborted) return 0
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        if (legal == 0) return if (ChessRules.inCheck(p)) -MATE + ply else 0
        return best
    }

    /** Returns the chosen move, or 0 when there is no legal move. */
    fun bestMove(p: ChessPos): Int {
        val root = ChessRules.legal(p)
        if (root.isEmpty()) return 0
        if (root.size == 1) return root[0]

        deadline = System.nanoTime() + timeMs * 1_000_000L
        nodes = 0

        // initial order: captures first
        val rootSc = IntArray(root.size)
        order(p, root, rootSc, root.size)
        var ordered = root.copyOf()
        run {
            val idx = (0 until ordered.size).sortedByDescending { rootSc[it] }
            val tmp = IntArray(ordered.size) { ordered[idx[it]] }
            ordered = tmp
        }

        var finalMoves = ordered
        var finalScores = IntArray(ordered.size)

        for (d in 1..maxDepth) {
            aborted = false
            val scores = IntArray(ordered.size) { -INF }
            var bestScore = -INF
            for (i in ordered.indices) {
                val q = ChessRules.make(p, ordered[i])
                val low = if (bestScore == -INF) -INF else bestScore - margin
                val v = -negamax(q, d - 1, -INF, -low, 1)
                if (aborted) break
                scores[i] = v
                if (v > bestScore) bestScore = v
            }
            if (aborted) break
            finalMoves = ordered
            finalScores = scores
            // next iteration: best first
            val idx = (0 until ordered.size).sortedByDescending { scores[it] }
            ordered = IntArray(ordered.size) { ordered[idx[it]] }
            if (bestScore >= MATE - 100) break
        }

        var best = -INF
        for (s in finalScores) if (s > best) best = s
        val candidates = ArrayList<Int>()
        for (i in finalMoves.indices) {
            if (finalScores[i] > -INF && finalScores[i] >= best - margin) candidates.add(finalMoves[i])
        }
        if (candidates.isEmpty()) return finalMoves[0]
        if (margin == 0) {
            // exact best (first with max score)
            for (i in finalMoves.indices) if (finalScores[i] == best) return finalMoves[i]
        }
        return candidates[Random.nextInt(candidates.size)]
    }
}
