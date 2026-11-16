package com.example.minigames.tictactoe

import kotlin.random.Random

/*
 * Pure-Kotlin tic-tac-toe engine for an n x n board where k marks in a row win.
 * Cells: 0 empty, 1 = X (always moves first), 2 = O.
 */

class TttBoard(val n: Int, val k: Int) {
    val cells = IntArray(n * n)
    private val windows: Array<IntArray> = windowsFor(n, k)

    fun copy(): TttBoard {
        val b = TttBoard(n, k)
        System.arraycopy(cells, 0, b.cells, 0, cells.size)
        return b
    }

    fun empties(): Int = cells.count { it == 0 }
    fun isFull(): Boolean = cells.none { it == 0 }

    /** The winning line (cell indices) or null. */
    fun winLine(): IntArray? {
        for (w in windows) {
            val v = cells[w[0]]
            if (v == 0) continue
            var all = true
            for (i in 1 until w.size) if (cells[w[i]] != v) {
                all = false
                break
            }
            if (all) return w
        }
        return null
    }

    fun winner(): Int {
        val l = winLine() ?: return 0
        return cells[l[0]]
    }

    fun windowList(): Array<IntArray> = windows

    companion object {
        private val cache = HashMap<Int, Array<IntArray>>()

        fun windowsFor(n: Int, k: Int): Array<IntArray> {
            val key = n * 100 + k
            cache[key]?.let { return it }
            val list = ArrayList<IntArray>()
            val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
            for (r in 0 until n) for (c in 0 until n) for (d in dirs) {
                val er = r + d[0] * (k - 1)
                val ec = c + d[1] * (k - 1)
                if (er !in 0 until n || ec !in 0 until n) continue
                list.add(IntArray(k) { (r + d[0] * it) * n + (c + d[1] * it) })
            }
            val res = list.toTypedArray()
            cache[key] = res
            return res
        }
    }
}

object TttAI {
    private const val WIN = 100_000

    /** Picks a cell for [me] (1 or 2). level: 0 easy, 1 medium, 2 hard. Returns -1 if the board is full. */
    fun choose(b: TttBoard, me: Int, level: Int): Int {
        val empty = ArrayList<Int>()
        for (i in b.cells.indices) if (b.cells[i] == 0) empty.add(i)
        if (empty.isEmpty()) return -1
        val opp = 3 - me

        return when (level) {
            0 -> {
                val win = findWinning(b, me)
                if (win >= 0 && Random.nextFloat() < 0.75f) return win
                val block = findWinning(b, opp)
                if (block >= 0 && Random.nextFloat() < 0.5f) return block
                empty[Random.nextInt(empty.size)]
            }
            1 -> {
                val win = findWinning(b, me)
                if (win >= 0) return win
                val block = findWinning(b, opp)
                if (block >= 0) return block
                heuristicPick(b, me, empty)
            }
            else -> hard(b, me, empty)
        }
    }

    private fun findWinning(b: TttBoard, who: Int): Int {
        for (i in b.cells.indices) {
            if (b.cells[i] != 0) continue
            b.cells[i] = who
            val w = b.winner() == who
            b.cells[i] = 0
            if (w) return i
        }
        return -1
    }

    private fun heuristicPick(b: TttBoard, me: Int, empty: List<Int>): Int {
        var bestScore = Int.MIN_VALUE
        val best = ArrayList<Int>()
        for (i in empty) {
            b.cells[i] = me
            val s = eval(b, me) + Random.nextInt(0, 3)
            b.cells[i] = 0
            if (s > bestScore) {
                bestScore = s
                best.clear()
                best.add(i)
            } else if (s == bestScore) best.add(i)
        }
        return best[Random.nextInt(best.size)]
    }

    private fun hard(b0: TttBoard, me: Int, empty: List<Int>): Int {
        val b = b0.copy()
        if (empty.size == b.cells.size) {
            // empty board: a good first move, with some variety
            val n = b.n
            val c = n / 2
            val options = if (n % 2 == 1) listOf(c * n + c, 0, n - 1, n * (n - 1), n * n - 1)
            else listOf((c - 1) * n + c - 1, (c - 1) * n + c, c * n + c - 1, c * n + c)
            return options[Random.nextInt(options.size)]
        }
        val depth = if (b.n == 3) 9 else 4
        var bestScore = Int.MIN_VALUE
        val best = ArrayList<Int>()
        val cands = candidates(b)
        for (i in cands) {
            b.cells[i] = me
            // only moves at least as good as the best so far need an exact score
            val alpha = if (bestScore == Int.MIN_VALUE) -WIN - 1 else bestScore - 1
            val s = if (b.winner() == me) WIN else -search(b, 3 - me, me, depth - 1, -(WIN + 1), -alpha, 1)
            b.cells[i] = 0
            if (s > bestScore) {
                bestScore = s
                best.clear()
                best.add(i)
            } else if (s == bestScore) best.add(i)
        }
        return best[Random.nextInt(best.size)]
    }

    /** Cells worth looking at: everything on small boards, otherwise cells next to existing marks. */
    private fun candidates(b: TttBoard): List<Int> {
        val res = ArrayList<Int>()
        val n = b.n
        if (n <= 3) {
            for (i in b.cells.indices) if (b.cells[i] == 0) res.add(i)
            return res
        }
        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            if (b.cells[i] != 0) continue
            var near = false
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until n && cc in 0 until n && b.cells[rr * n + cc] != 0) near = true
            }
            if (near) res.add(i)
        }
        if (res.isEmpty()) for (i in b.cells.indices) if (b.cells[i] == 0) res.add(i)
        return res
    }

    /** Negamax from the point of view of [toMove]; [root] is the player the heuristic is anchored to. */
    private fun search(b: TttBoard, toMove: Int, root: Int, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        // the previous mover may have just won
        val w = b.winner()
        if (w != 0) return if (w == toMove) WIN - ply else -(WIN - ply)
        if (b.isFull()) return 0
        if (depth <= 0) return eval(b, toMove)
        var alpha = alphaIn
        var best = -WIN - 1
        val cands = candidates(b)
        for (i in cands) {
            b.cells[i] = toMove
            val v = -search(b, 3 - toMove, root, depth - 1, -beta, -alpha, ply + 1)
            b.cells[i] = 0
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    /** Heuristic: weighted open lines for [me] minus those of the opponent. */
    fun eval(b: TttBoard, me: Int): Int {
        val opp = 3 - me
        var s = 0
        for (w in b.windowList()) {
            var mine = 0
            var theirs = 0
            for (i in w) {
                val v = b.cells[i]
                if (v == me) mine++ else if (v == opp) theirs++
            }
            if (mine > 0 && theirs > 0) continue
            if (mine > 0) s += weight(mine, b.k)
            if (theirs > 0) s -= weight(theirs, b.k)
        }
        return s
    }

    private fun weight(count: Int, k: Int): Int = when {
        count >= k -> 10_000
        count == k - 1 -> 100
        count == k - 2 -> 10
        else -> 1
    }
}
