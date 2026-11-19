package com.example.minigames.checkers

import kotlin.math.abs
import kotlin.random.Random

/*
 * Pure-Kotlin draughts engine (no Android classes) for three rule sets, see [CVariant].
 *
 * Board: index = row * n + col (n = 8 or 10), row 0 is the top. Playable squares are the dark ones: (row + col) odd.
 * Pieces: 1 white man, 2 white king, -1 black man, -2 black king. White starts at the bottom and moves first.
 *
 * Common rules:
 *  - men move one step diagonally forward, kings fly any distance;
 *  - capturing is mandatory, men capture backwards too, kings capture from a distance;
 *  - a capture must be continued with the same piece as long as possible;
 *  - captured pieces are removed after the whole move and cannot be jumped twice ("Turkish strike");
 *  - a king that can continue capturing must land on a square from which it can.
 * Differences:
 *  - RUSSIAN (8x8): any capture sequence may be chosen; a man reaching the last row during a capture becomes a
 *    king at once and continues as a king; a player without moves loses.
 *  - INTERNATIONAL (10x10): the sequence capturing the MOST pieces must be chosen; a man passing the last row
 *    during a capture stays a man (it is crowned only when the move ends there); a player without moves loses.
 *  - GIVEAWAY (8x8, "poddavki"): Russian movement and capture rules, but the player who loses all pieces or
 *    cannot move WINS.
 */

enum class CVariant(val n: Int, val majority: Boolean, val promoteAtEnd: Boolean, val misere: Boolean, val quietLimit: Int) {
    RUSSIAN(8, false, false, false, 30),
    INTERNATIONAL(10, true, true, false, 50),
    GIVEAWAY(8, false, false, true, 30);

    companion object {
        fun byOrdinal(i: Int): CVariant = values()[i.coerceIn(0, values().size - 1)]
    }
}

class CPos(val v: CVariant = CVariant.RUSSIAN) {
    val n: Int = v.n
    val b = IntArray(n * n)
    var white = true

    /** Plies since the last capture or man move (for the draw rule). */
    var quiet = 0

    fun copy(): CPos {
        val p = CPos(v)
        System.arraycopy(b, 0, p.b, 0, b.size)
        p.white = white
        p.quiet = quiet
        return p
    }

    fun hash(): Long {
        var h = 1469598103934665603L
        for (i in b.indices) h = (h xor (b[i] + 3).toLong()) * 1099511628211L
        h = (h xor (if (white) 1L else 2L)) * 1099511628211L
        return h
    }

    companion object {
        fun start(v: CVariant = CVariant.RUSSIAN): CPos {
            val p = CPos(v)
            val n = v.n
            val rows = n / 2 - 1          // 3 rows for 8x8, 4 rows for 10x10
            for (r in 0 until n) for (c in 0 until n) {
                if ((r + c) % 2 == 1) {
                    if (r < rows) p.b[r * n + c] = -1
                    if (r >= n - rows) p.b[r * n + c] = 1
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
    private const val DEAD = 99          // a captured piece that still stands on the board during a capture
    private val EMPTY = IntArray(0)

    fun moves(p: CPos): List<CMove> {
        val n = p.n
        val caps = ArrayList<CMove>()
        for (sq in 0 until n * n) {
            val pc = p.b[sq]
            if (pc == 0 || (pc > 0) != p.white) continue
            captures(p, sq, caps)
        }
        if (caps.isNotEmpty()) {
            if (!p.v.majority) return caps
            var mx = 0
            for (m in caps) if (m.caps.size > mx) mx = m.caps.size
            return caps.filter { it.caps.size == mx }
        }
        val res = ArrayList<CMove>()
        for (sq in 0 until n * n) {
            val pc = p.b[sq]
            if (pc == 0 || (pc > 0) != p.white) continue
            simple(p, sq, res)
        }
        return res
    }

    /** Legal moves of one particular piece (respecting the mandatory-capture rule of the whole side). */
    fun movesFrom(p: CPos, sq: Int): List<CMove> = moves(p).filter { it.from == sq }

    private fun simple(p: CPos, sq: Int, out: MutableList<CMove>) {
        val n = p.n
        val b = p.b
        val pc = b[sq]
        val white = pc > 0
        val king = abs(pc) == 2
        val r = sq / n
        val c = sq % n
        for (d in 0 until 4) {
            val dr = DIRS[d * 2]
            val dc = DIRS[d * 2 + 1]
            if (!king) {
                if (dr != (if (white) -1 else 1)) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until n && cc in 0 until n && b[rr * n + cc] == 0) {
                    out.add(CMove(intArrayOf(sq, rr * n + cc), EMPTY))
                }
            } else {
                var rr = r + dr
                var cc = c + dc
                while (rr in 0 until n && cc in 0 until n && b[rr * n + cc] == 0) {
                    out.add(CMove(intArrayOf(sq, rr * n + cc), EMPTY))
                    rr += dr
                    cc += dc
                }
            }
        }
    }

    private fun captures(p: CPos, from: Int, out: MutableList<CMove>) {
        val b = p.b
        val pc = b[from]
        b[from] = 0 // the moving piece leaves its square
        val path = IntArray(48)
        val caps = IntArray(48)
        path[0] = from
        dfs(p.n, b, p.white, p.v.promoteAtEnd, from, abs(pc) == 2, path, 1, caps, 0, out)
        b[from] = pc
    }

    private fun canCaptureKing(n: Int, b: IntArray, white: Boolean, sq: Int): Boolean {
        val r = sq / n
        val c = sq % n
        for (d in 0 until 4) {
            val dr = DIRS[d * 2]
            val dc = DIRS[d * 2 + 1]
            var rr = r + dr
            var cc = c + dc
            while (rr in 0 until n && cc in 0 until n && b[rr * n + cc] == 0) {
                rr += dr
                cc += dc
            }
            if (rr !in 0 until n || cc !in 0 until n) continue
            val mp = b[rr * n + cc]
            if (mp == DEAD || (mp > 0) == white) continue
            val lr = rr + dr
            val lc = cc + dc
            if (lr in 0 until n && lc in 0 until n && b[lr * n + lc] == 0) return true
        }
        return false
    }

    private fun dfs(
        n: Int, b: IntArray, white: Boolean, promoteAtEnd: Boolean, sq: Int, king: Boolean,
        path: IntArray, pn: Int, caps: IntArray, cn: Int, out: MutableList<CMove>
    ) {
        var found = false
        val r = sq / n
        val c = sq % n
        for (d in 0 until 4) {
            val dr = DIRS[d * 2]
            val dc = DIRS[d * 2 + 1]
            if (!king) {
                val lr = r + 2 * dr
                val lc = c + 2 * dc
                if (lr !in 0 until n || lc !in 0 until n) continue
                val mid = (r + dr) * n + (c + dc)
                val land = lr * n + lc
                val mp = b[mid]
                if (mp == 0 || mp == DEAD || (mp > 0) == white) continue
                if (b[land] != 0) continue
                found = true
                path[pn] = land
                caps[cn] = mid
                val promoted = !promoteAtEnd && ((white && lr == 0) || (!white && lr == n - 1))
                b[mid] = DEAD
                dfs(n, b, white, promoteAtEnd, land, promoted, path, pn + 1, caps, cn + 1, out)
                b[mid] = mp
            } else {
                var rr = r + dr
                var cc = c + dc
                while (rr in 0 until n && cc in 0 until n && b[rr * n + cc] == 0) {
                    rr += dr
                    cc += dc
                }
                if (rr !in 0 until n || cc !in 0 until n) continue
                val mid = rr * n + cc
                val mp = b[mid]
                if (mp == DEAD || (mp > 0) == white) continue
                val lands = IntArray(n)
                var ln = 0
                var lr = rr + dr
                var lc = cc + dc
                while (lr in 0 until n && lc in 0 until n && b[lr * n + lc] == 0) {
                    lands[ln++] = lr * n + lc
                    lr += dr
                    lc += dc
                }
                if (ln == 0) continue
                found = true
                b[mid] = DEAD
                // landing squares from which the capture can continue are mandatory
                val cont = BooleanArray(ln)
                var any = false
                for (i in 0 until ln) {
                    cont[i] = canCaptureKing(n, b, white, lands[i])
                    if (cont[i]) any = true
                }
                for (i in 0 until ln) {
                    if (any && !cont[i]) continue
                    path[pn] = lands[i]
                    caps[cn] = mid
                    dfs(n, b, white, promoteAtEnd, lands[i], true, path, pn + 1, caps, cn + 1, out)
                }
                b[mid] = mp
            }
        }
        if (!found && cn > 0) out.add(CMove(path.copyOf(pn), caps.copyOf(cn)))
    }

    fun apply(p: CPos, m: CMove): CPos {
        val n = p.n
        val q = p.copy()
        val pc = q.b[m.from]
        q.b[m.from] = 0
        for (s in m.caps) q.b[s] = 0
        var np = pc
        if (abs(pc) == 1) {
            if (p.v.promoteAtEnd) {
                val r = m.to / n
                if ((pc > 0 && r == 0) || (pc < 0 && r == n - 1)) np = pc * 2
            } else {
                for (s in m.path) {
                    val r = s / n
                    if ((pc > 0 && r == 0) || (pc < 0 && r == n - 1)) np = pc * 2
                }
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

    /** Result for the side that has just moved when the other side has no moves: 1 white wins, 2 black wins. */
    fun winnerWhenStuck(v: CVariant, moverWhite: Boolean): Int {
        val moverWins = !v.misere
        return if (moverWins == moverWhite) 1 else 2
    }
}

/** Square names: file letter + rank number counted from the white side (a1 is the bottom-left dark square). */
object CheckersNotation {
    fun square(n: Int, sq: Int): String = "" + ('a' + sq % n) + (n - sq / n)

    fun move(n: Int, m: CMove): String {
        val sb = StringBuilder()
        for (i in m.path.indices) {
            if (i > 0) sb.append(if (m.isCapture) 'x' else '-')
            sb.append(square(n, m.path[i]))
        }
        return sb.toString()
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
        val n = p.n
        var s = 0
        val lo = n / 4
        val hi = n - 1 - lo
        for (sq in 0 until n * n) {
            val pc = p.b[sq]
            if (pc == 0) continue
            val r = sq / n
            val c = sq % n
            val center = if (c in lo..hi && r in lo..hi) 4 else 0
            val edge = if (c == 0 || c == n - 1) -2 else 0
            when (pc) {
                1 -> s += 100 + (n - 1 - r) * 6 + center + (if (r == n - 1) 8 else 0) + edge
                -1 -> s -= 100 + r * 6 + center + (if (r == 0) 8 else 0) + edge
                2 -> s += 330 + center
                -2 -> s -= 330 + center
            }
        }
        if (p.v.misere) s = -s       // giveaway: fewer / worse pieces are better
        return if (p.white) s else -s
    }

    private fun negamax(p: CPos, depth: Int, alphaIn: Int, beta: Int, ply: Int, ext: Int): Int {
        if ((++nodes and 1023) == 0 && System.nanoTime() > deadline) aborted = true
        if (aborted) return 0
        if (p.quiet >= p.v.quietLimit) return 0
        val ms = CheckersRules.moves(p)
        if (ms.isEmpty()) return if (p.v.misere) WIN - ply else -WIN + ply
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
