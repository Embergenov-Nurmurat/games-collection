package com.example.minigames.checkers

import kotlin.math.abs
import kotlin.random.Random

/*
 * Pure-Kotlin Russian draughts engine (no Android classes).
 *
 * Board: index = row * 8 + col, row 0 is the top. Playable squares are the dark ones: (row + col) odd.
 * Pieces: 1 white man, 2 white king, -1 black man, -2 black king. White starts at the bottom and moves first.
 *
 * Rules implemented:
 *  - men move one step diagonally forward, kings fly any distance;
 *  - capturing is mandatory, men capture backwards too, kings capture from a distance;
 *  - a capture must be continued with the same piece as long as possible (any sequence may be chosen);
 *  - captured pieces are removed after the whole move and cannot be jumped twice ("Turkish strike");
 *  - a man that reaches the last row during a capture becomes a king and keeps capturing as a king;
 *  - a player without legal moves loses.
 */

class CPos {
    val b = IntArray(64)
    var white = true

    /** Plies since the last capture or man move (for the draw rule). */
    var quiet = 0

    fun copy(): CPos {
        val p = CPos()
        System.arraycopy(b, 0, p.b, 0, 64)
        p.white = white
        p.quiet = quiet
        return p
    }

    fun hash(): Long {
        var h = 1469598103934665603L
        for (i in 0 until 64) h = (h xor (b[i] + 3).toLong()) * 1099511628211L
        h = (h xor (if (white) 1L else 2L)) * 1099511628211L
        return h
    }

    companion object {
        fun start(): CPos {
            val p = CPos()
            for (r in 0..7) for (c in 0..7) {
                if ((r + c) % 2 == 1) {
                    if (r <= 2) p.b[r * 8 + c] = -1
                    if (r >= 5) p.b[r * 8 + c] = 1
                }
            }
            return p
        }
    }
}

/** A move as a path of squares; [caps] are the captured squares in order (empty for a quiet move). */
class CMove(val path: IntArray, val caps: IntArray) {
    val from: Int get() = path[0]
    val to: Int get() = path[path.size - 1]
    val isCapture: Boolean get() = caps.isNotEmpty()
}

object CheckersRules {

    private val DIRS = intArrayOf(-1, -1, -1, 1, 1, -1, 1, 1)

    fun moves(p: CPos): List<CMove> {
        val caps = ArrayList<CMove>()
        for (sq in 0 until 64) {
            val pc = p.b[sq]
            if (pc == 0 || (pc > 0) != p.white) continue
            captures(p, sq, caps)
        }
        if (caps.isNotEmpty()) return caps
        val res = ArrayList<CMove>()
        for (sq in 0 until 64) {
            val pc = p.b[sq]
            if (pc == 0 || (pc > 0) != p.white) continue
            simple(p, sq, res)
        }
        return res
    }

    /** Legal moves of one particular piece (respecting the mandatory-capture rule of the whole side). */
    fun movesFrom(p: CPos, sq: Int): List<CMove> = moves(p).filter { it.from == sq }

    private fun simple(p: CPos, sq: Int, out: MutableList<CMove>) {
        val b = p.b
        val pc = b[sq]
        val white = pc > 0
        val king = abs(pc) == 2
        val r = sq shr 3
        val c = sq and 7
        for (d in 0 until 4) {
            val dr = DIRS[d * 2]
            val dc = DIRS[d * 2 + 1]
            if (!king) {
                if (dr != (if (white) -1 else 1)) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == 0) {
                    out.add(CMove(intArrayOf(sq, rr * 8 + cc), EMPTY))
                }
            } else {
                var rr = r + dr
                var cc = c + dc
                while (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == 0) {
                    out.add(CMove(intArrayOf(sq, rr * 8 + cc), EMPTY))
                    rr += dr
                    cc += dc
                }
            }
        }
    }

    private val EMPTY = IntArray(0)

    private fun captures(p: CPos, from: Int, out: MutableList<CMove>) {
        val b = p.b
        val pc = b[from]
        b[from] = 0 // the moving piece leaves its square
        val path = IntArray(40)
        val caps = IntArray(40)
        path[0] = from
        dfs(b, p.white, from, abs(pc) == 2, 0L, path, 1, caps, 0, out)
        b[from] = pc
    }

    private fun canCaptureKing(b: IntArray, white: Boolean, sq: Int, mask: Long): Boolean {
        val r = sq shr 3
        val c = sq and 7
        for (d in 0 until 4) {
            val dr = DIRS[d * 2]
            val dc = DIRS[d * 2 + 1]
            var rr = r + dr
            var cc = c + dc
            while (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == 0) {
                rr += dr
                cc += dc
            }
            if (rr !in 0..7 || cc !in 0..7) continue
            val mid = rr * 8 + cc
            val mp = b[mid]
            if ((mp > 0) == white) continue
            if (mask and (1L shl mid) != 0L) continue
            val lr = rr + dr
            val lc = cc + dc
            if (lr in 0..7 && lc in 0..7 && b[lr * 8 + lc] == 0) return true
        }
        return false
    }

    private fun dfs(
        b: IntArray, white: Boolean, sq: Int, king: Boolean, mask: Long,
        path: IntArray, pn: Int, caps: IntArray, cn: Int, out: MutableList<CMove>
    ) {
        var found = false
        val r = sq shr 3
        val c = sq and 7
        for (d in 0 until 4) {
            val dr = DIRS[d * 2]
            val dc = DIRS[d * 2 + 1]
            if (!king) {
                val lr = r + 2 * dr
                val lc = c + 2 * dc
                if (lr !in 0..7 || lc !in 0..7) continue
                val mid = (r + dr) * 8 + (c + dc)
                val land = lr * 8 + lc
                val mp = b[mid]
                if (mp == 0 || (mp > 0) == white) continue
                if (mask and (1L shl mid) != 0L) continue
                if (b[land] != 0) continue
                found = true
                path[pn] = land
                caps[cn] = mid
                val promoted = (white && lr == 0) || (!white && lr == 7)
                dfs(b, white, land, promoted, mask or (1L shl mid), path, pn + 1, caps, cn + 1, out)
            } else {
                var rr = r + dr
                var cc = c + dc
                while (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == 0) {
                    rr += dr
                    cc += dc
                }
                if (rr !in 0..7 || cc !in 0..7) continue
                val mid = rr * 8 + cc
                val mp = b[mid]
                if ((mp > 0) == white) continue
                if (mask and (1L shl mid) != 0L) continue
                val lands = IntArray(8)
                var ln = 0
                var lr = rr + dr
                var lc = cc + dc
                while (lr in 0..7 && lc in 0..7 && b[lr * 8 + lc] == 0) {
                    lands[ln++] = lr * 8 + lc
                    lr += dr
                    lc += dc
                }
                if (ln == 0) continue
                found = true
                val newMask = mask or (1L shl mid)
                // landing squares from which the capture can continue are mandatory
                val cont = BooleanArray(ln)
                var any = false
                for (i in 0 until ln) {
                    cont[i] = canCaptureKing(b, white, lands[i], newMask)
                    if (cont[i]) any = true
                }
                for (i in 0 until ln) {
                    if (any && !cont[i]) continue
                    path[pn] = lands[i]
                    caps[cn] = mid
                    dfs(b, white, lands[i], true, newMask, path, pn + 1, caps, cn + 1, out)
                }
            }
        }
        if (!found && cn > 0) out.add(CMove(path.copyOf(pn), caps.copyOf(cn)))
    }

    fun apply(p: CPos, m: CMove): CPos {
        val q = p.copy()
        val pc = q.b[m.from]
        q.b[m.from] = 0
        for (s in m.caps) q.b[s] = 0
        var np = pc
        if (abs(pc) == 1) {
            for (s in m.path) {
                val r = s shr 3
                if ((pc > 0 && r == 0) || (pc < 0 && r == 7)) np = pc * 2
            }
        }
        q.b[m.to] = np
        q.quiet = if (m.caps.isNotEmpty() || abs(pc) == 1) 0 else p.quiet + 1
        q.white = !p.white
        return q
    }

    fun countPieces(p: CPos, white: Boolean): Int {
        var n = 0
        for (v in p.b) if (v != 0 && (v > 0) == white) n++
        return n
    }
}

class CheckersAI(
    private val maxDepth: Int,
    private val timeMs: Long,
    private val margin: Int
) {
    private companion object {
        const val INF = 1_000_000
        const val WIN = 100_000
    }

    private var deadline = 0L
    private var aborted = false
    private var nodes = 0

    private fun evalStm(p: CPos): Int {
        var s = 0
        for (sq in 0 until 64) {
            val pc = p.b[sq]
            if (pc == 0) continue
            val r = sq shr 3
            val c = sq and 7
            val center = if (c in 2..5 && r in 2..5) 4 else 0
            when (pc) {
                1 -> s += 100 + (7 - r) * 6 + center + (if (r == 7) 8 else 0) + (if (c == 0 || c == 7) -2 else 0)
                -1 -> s -= 100 + r * 6 + center + (if (r == 0) 8 else 0) + (if (c == 0 || c == 7) -2 else 0)
                2 -> s += 330 + center
                -2 -> s -= 330 + center
            }
        }
        return if (p.white) s else -s
    }

    private fun negamax(p: CPos, depth: Int, alphaIn: Int, beta: Int, ply: Int, ext: Int): Int {
        if ((++nodes and 1023) == 0 && System.nanoTime() > deadline) aborted = true
        if (aborted) return 0
        if (p.quiet >= 30) return 0
        val ms = CheckersRules.moves(p)
        if (ms.isEmpty()) return -WIN + ply
        var d = depth
        var e = ext
        if (d <= 0) {
            // keep going while a capture is forced (short quiescence)
            if (ms[0].isCapture && e < 6) {
                d = 1
                e++
            } else {
                return evalStm(p)
            }
        }
        var alpha = alphaIn
        var best = -INF
        for (m in ms) {
            val v = -negamax(CheckersRules.apply(p, m), d - 1, -beta, -alpha, ply + 1, e)
            if (aborted) return 0
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    fun bestMove(p: CPos): CMove? {
        val root = CheckersRules.moves(p)
        if (root.isEmpty()) return null
        if (root.size == 1) return root[0]

        deadline = System.nanoTime() + timeMs * 1_000_000L
        nodes = 0

        var ordered: List<CMove> = root
        var finalMoves: List<CMove> = root
        var finalScores = IntArray(root.size)

        for (d in 1..maxDepth) {
            aborted = false
            val scores = IntArray(ordered.size) { -INF }
            var bestScore = -INF
            for (i in ordered.indices) {
                val low = if (bestScore == -INF) -INF else bestScore - margin
                val v = -negamax(CheckersRules.apply(p, ordered[i]), d - 1, -INF, -low, 1, 0)
                if (aborted) break
                scores[i] = v
                if (v > bestScore) bestScore = v
            }
            if (aborted) break
            finalMoves = ordered
            finalScores = scores
            val idx = ordered.indices.sortedByDescending { scores[it] }
            val prev = ordered
            ordered = idx.map { prev[it] }
            if (bestScore >= WIN - 100) break
        }

        var best = -INF
        for (s in finalScores) if (s > best) best = s
        if (margin == 0) {
            for (i in finalMoves.indices) if (finalScores[i] == best) return finalMoves[i]
        }
        val cands = ArrayList<CMove>()
        for (i in finalMoves.indices) {
            if (finalScores[i] > -INF && finalScores[i] >= best - margin) cands.add(finalMoves[i])
        }
        if (cands.isEmpty()) return finalMoves[0]
        return cands[Random.nextInt(cands.size)]
    }
}
