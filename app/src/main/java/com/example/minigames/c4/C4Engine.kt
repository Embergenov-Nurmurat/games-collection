package com.example.minigames.c4

import kotlin.random.Random

/*
 * Pure-Kotlin Connect Four engine: 7 columns x 6 rows, gravity drops.
 * Side 0 (red) moves first, side 1 (yellow) second.
 *
 * Bitboard layout: bit index = col * 7 + row (row 0 = bottom, row 6 is an always-empty sentinel),
 * so the "four in a row" test is a handful of shifts.
 */

class C4Board {
    companion object {
        const val COLS = 7
        const val ROWS = 6
        const val CELLS = 42

        /** Columns ordered from the centre outwards. */
        val ORDER = intArrayOf(3, 2, 4, 1, 5, 0, 6)

        fun bit(col: Int, row: Int): Long = 1L shl (col * 7 + row)

        /** True when [b] contains four connected discs. */
        fun hasFour(b: Long): Boolean {
            var m = b and (b shr 7)
            if ((m and (m shr 14)) != 0L) return true
            m = b and (b shr 6)
            if ((m and (m shr 12)) != 0L) return true
            m = b and (b shr 8)
            if ((m and (m shr 16)) != 0L) return true
            m = b and (b shr 1)
            return (m and (m shr 2)) != 0L
        }
    }

    val bb = LongArray(2)
    val height = IntArray(COLS)
    private val hist = IntArray(CELLS)
    var count = 0
        private set

    /** Side to move (0 or 1). */
    val side: Int get() = count and 1

    val mask: Long get() = bb[0] or bb[1]

    fun canPlay(col: Int): Boolean = col in 0 until COLS && height[col] < ROWS

    fun isFull(): Boolean = count >= CELLS

    /** Plays a disc of the side to move. Returns false (and changes nothing) when the column is full / invalid. */
    fun play(col: Int): Boolean {
        if (!canPlay(col)) return false
        bb[side] = bb[side] or bit(col, height[col])
        height[col]++
        hist[count++] = col
        return true
    }

    /** Takes the last disc back; returns the column or -1. */
    fun undo(): Int {
        if (count == 0) return -1
        val col = hist[--count]
        height[col]--
        bb[side] = bb[side] and bit(col, height[col]).inv()
        return col
    }

    fun lastMove(): Int = if (count == 0) -1 else hist[count - 1]

    fun moveAt(i: Int): Int = hist[i]

    /** -1 empty, otherwise the side whose disc it is. */
    fun cell(col: Int, row: Int): Int {
        val b = bit(col, row)
        return if ((bb[0] and b) != 0L) 0 else if ((bb[1] and b) != 0L) 1 else -1
    }

    /** Winning side (0/1) or -1. */
    fun winner(): Int = if (hasFour(bb[0])) 0 else if (hasFour(bb[1])) 1 else -1

    /** Would [col] give the side to move four in a row? */
    fun winsAt(col: Int): Boolean = canPlay(col) && hasFour(bb[side] or bit(col, height[col]))

    /** The four winning cells encoded as col * 10 + row (or null). */
    fun winCells(): IntArray? {
        val w = winner()
        if (w < 0) return null
        val b = bb[w]
        val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))
        for (c in 0 until COLS) for (r in 0 until ROWS) for (d in dirs) {
            val ec = c + 3 * d[0]
            val er = r + 3 * d[1]
            if (ec !in 0 until COLS || er !in 0 until ROWS) continue
            var all = true
            for (i in 0..3) if ((b and bit(c + i * d[0], r + i * d[1])) == 0L) {
                all = false
                break
            }
            if (all) return IntArray(4) { (c + it * d[0]) * 10 + (r + it * d[1]) }
        }
        return null
    }

    fun legalMoves(): IntArray {
        val l = ArrayList<Int>()
        for (c in ORDER) if (canPlay(c)) l.add(c)
        return l.toIntArray()
    }

    fun copy(): C4Board {
        val n = C4Board()
        n.bb[0] = bb[0]
        n.bb[1] = bb[1]
        System.arraycopy(height, 0, n.height, 0, COLS)
        System.arraycopy(hist, 0, n.hist, 0, CELLS)
        n.count = count
        return n
    }

    /** Move list as column digits, e.g. "334". */
    fun serialize(): String {
        val sb = StringBuilder()
        for (i in 0 until count) sb.append(('0' + hist[i]))
        return sb.toString()
    }

    /** Replays a move list; returns null when it is not a legal game prefix. */
    fun deserialize(s: String): Boolean {
        val b = C4Board()
        for (ch in s) {
            if (ch !in '0'..'6') return false
            if (b.winner() >= 0) return false
            if (!b.play(ch - '0')) return false
        }
        bb[0] = b.bb[0]
        bb[1] = b.bb[1]
        System.arraycopy(b.height, 0, height, 0, COLS)
        System.arraycopy(b.hist, 0, hist, 0, CELLS)
        count = b.count
        return true
    }
}

/** Alpha-beta searcher with a transposition table; one instance per search. */
class C4Search(private val deadlineNs: Long, private val ttBits: Int = 18) {
    companion object {
        const val WIN = 10000

        private val WINDOWS: LongArray = run {
            val l = ArrayList<Long>()
            val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))
            for (c in 0 until 7) for (r in 0 until 6) for (d in dirs) {
                val ec = c + 3 * d[0]
                val er = r + 3 * d[1]
                if (ec !in 0 until 7 || er !in 0 until 6) continue
                var m = 0L
                for (i in 0..3) m = m or C4Board.bit(c + i * d[0], r + i * d[1])
                l.add(m)
            }
            l.toLongArray()
        }
        private val COL_W = intArrayOf(0, 1, 2, 4, 2, 1, 0)
        private val WEIGHT = intArrayOf(0, 1, 8, 40, 0)
    }

    private val keys = LongArray(1 shl ttBits)
    private val data = IntArray(1 shl ttBits)
    var nodes = 0L
        private set
    var aborted = false
        private set

    private fun ttIndex(key: Long): Int = ((key * -7046029254386353131L) ushr (64 - ttBits)).toInt()

    /** Static evaluation from the point of view of the side to move. */
    fun eval(b: C4Board): Int {
        val me = b.bb[b.side]
        val op = b.bb[1 - b.side]
        val occ = me or op
        val wantEvenRow = b.side == 0 // red (first) profits from threats on rows 1,3,5 (0-based even)
        var s = 0
        for (w in WINDOWS) {
            val a = java.lang.Long.bitCount(me and w)
            val o = java.lang.Long.bitCount(op and w)
            if (a > 0 && o > 0) continue
            if (o == 0 && a > 0) {
                s += WEIGHT[a]
                if (a == 3) {
                    val e = w and occ.inv()
                    val row = java.lang.Long.numberOfTrailingZeros(e) % 7
                    if ((row % 2 == 0) == wantEvenRow) s += 25
                }
            } else if (a == 0 && o > 0) {
                s -= WEIGHT[o]
                if (o == 3) {
                    val e = w and occ.inv()
                    val row = java.lang.Long.numberOfTrailingZeros(e) % 7
                    if ((row % 2 == 0) != wantEvenRow) s -= 25
                }
            }
        }
        for (c in 0 until 7) {
            val colMask = 0x3FL shl (c * 7)
            s += COL_W[c] * 2 * (java.lang.Long.bitCount(me and colMask) - java.lang.Long.bitCount(op and colMask))
        }
        return s
    }

    private fun checkTime() {
        if ((nodes and 1023L) == 0L && System.nanoTime() > deadlineNs) aborted = true
    }

    /**
     * Value for the side to move. Scores are WIN - plies for a win, -(WIN - plies) for a loss,
     * so a quicker win / slower loss is better.
     */
    fun negamax(b: C4Board, depth: Int, alphaIn: Int, betaIn: Int): Int {
        nodes++
        checkTime()
        if (aborted) return 0
        var alpha = alphaIn
        var beta = betaIn
        val cnt = b.count
        if (cnt >= C4Board.CELLS) return 0
        val side = b.side
        val me = b.bb[side]
        val op = b.bb[1 - side]
        val h = b.height

        // immediate win
        for (c in 0 until 7) if (h[c] < 6 && C4Board.hasFour(me or C4Board.bit(c, h[c]))) return WIN - (cnt + 1)

        // opponent threats: blocks that are forced
        var forced = -1
        var nThreat = 0
        for (c in 0 until 7) if (h[c] < 6 && C4Board.hasFour(op or C4Board.bit(c, h[c]))) {
            nThreat++
            forced = c
        }
        if (nThreat >= 2) return -(WIN - (cnt + 2))
        if (cnt + 1 >= C4Board.CELLS) return 0 // last disc and no win
        if (depth <= 0) return eval(b)

        // we cannot win sooner than 3 plies from now
        val maxScore = WIN - (cnt + 3)
        if (beta > maxScore) {
            beta = maxScore
            if (alpha >= beta) return beta
        }

        val key = me + (me or op)
        val idx = ttIndex(key)
        var ttMove = -1
        if (keys[idx] == key) {
            val d = data[idx]
            val ttDepth = (d shr 16) and 0xFF
            val flag = (d shr 24) and 3
            ttMove = ((d shr 26) and 7) - 1
            if (ttDepth >= depth) {
                val v = (d and 0xFFFF) - 32768
                when (flag) {
                    1 -> return v
                    2 -> if (v > alpha) alpha = v   // lower bound
                    3 -> if (v < beta) beta = v      // upper bound
                }
                if (alpha >= beta) return v
            }
        }

        var best = -WIN - 10
        var bestMove = -1
        val origAlpha = alpha
        var tried = 0

        // candidate moves
        val cand = IntArray(7)
        var n = 0
        if (forced >= 0) {
            cand[n++] = forced
        } else {
            if (ttMove >= 0 && h[ttMove] < 6) cand[n++] = ttMove
            for (c in C4Board.ORDER) if (c != ttMove && h[c] < 6) cand[n++] = c
        }
        for (i in 0 until n) {
            val c = cand[i]
            // never play under an opponent winning cell if avoidable
            if (forced < 0 && h[c] < 5 && C4Board.hasFour(op or C4Board.bit(c, h[c] + 1))) continue
            tried++
            b.play(c)
            val v = -negamax(b, depth - 1, -beta, -alpha)
            b.undo()
            if (aborted) return 0
            if (v > best) {
                best = v
                bestMove = c
                if (v > alpha) alpha = v
                if (alpha >= beta) break
            }
        }
        if (tried == 0) return -(WIN - (cnt + 2)) // every move hands the opponent a win

        val flag = if (best <= origAlpha) 3 else if (best >= beta) 2 else 1
        keys[idx] = key
        data[idx] = (best + 32768 and 0xFFFF) or (depth.coerceAtMost(255) shl 16) or (flag shl 24) or ((bestMove + 1) shl 26)
        return best
    }

    /** Exact (full window) value of every legal column at [depth]; Int.MIN_VALUE for illegal columns. */
    fun scoreMoves(b: C4Board, depth: Int): IntArray {
        val res = IntArray(7) { Int.MIN_VALUE }
        for (c in 0 until 7) {
            if (!b.canPlay(c)) continue
            if (b.winsAt(c)) {
                res[c] = WIN - (b.count + 1)
                continue
            }
            b.play(c)
            val v = if (b.isFull()) 0 else -negamax(b, depth - 1, -WIN - 20, WIN + 20)
            b.undo()
            if (aborted) return res
            res[c] = v
        }
        return res
    }

    /** Iterative deepening; returns the best column and the deepest completed depth via [lastDepth]. */
    var lastDepth = 0
        private set
    var lastScore = 0
        private set

    fun bestMove(b: C4Board, maxDepth: Int): Int {
        val moves = b.legalMoves()
        if (moves.isEmpty()) return -1
        if (moves.size == 1) return moves[0]
        for (c in moves) if (b.winsAt(c)) {
            lastScore = WIN
            return c
        }
        var order = moves.copyOf()
        var best = order[0]
        var d = 1
        while (d <= maxDepth) {
            var alpha = -WIN - 20
            val beta = WIN + 20
            var iterBest = -1
            var iterScore = -WIN - 20
            val scores = IntArray(order.size)
            for ((i, c) in order.withIndex()) {
                b.play(c)
                val v = if (b.winner() >= 0) WIN - b.count else if (b.isFull()) 0 else -negamax(b, d - 1, -beta, -alpha)
                b.undo()
                if (aborted) break
                scores[i] = v
                if (v > iterScore) {
                    iterScore = v
                    iterBest = c
                    if (v > alpha) alpha = v
                }
            }
            if (aborted) break
            best = iterBest
            lastDepth = d
            lastScore = iterScore
            if (iterScore >= WIN - 60 || iterScore <= -(WIN - 60)) break // proven result
            // best move first next time
            val l = ArrayList<Int>()
            l.add(iterBest)
            for (c in order) if (c != iterBest) l.add(c)
            order = l.toIntArray()
            d++
        }
        return best
    }
}

object C4Ai {
    /** level: 0 easy, 1 medium, 2 hard. Returns a legal column or -1 when the board is full. */
    fun choose(b: C4Board, level: Int, rnd: Random = Random.Default): Int {
        val legal = b.legalMoves()
        if (legal.isEmpty()) return -1
        if (legal.size == 1) return legal[0]
        val work = b.copy()
        return when (level) {
            0 -> {
                if (rnd.nextFloat() < 0.30f) return legal[rnd.nextInt(legal.size)]
                val s = C4Search(System.nanoTime() + 300_000_000L, 14)
                val sc = s.scoreMoves(work, 2)
                pickAmong(legal, sc, 2, rnd)
            }
            1 -> {
                val s = C4Search(System.nanoTime() + 500_000_000L, 16)
                val sc = s.scoreMoves(work, 5)
                if (s.aborted) legal[rnd.nextInt(legal.size)] else pickAmong(legal, sc, 4, rnd)
            }
            else -> hard(work, 42, 800L)
        }
    }

    private fun pickAmong(legal: IntArray, sc: IntArray, margin: Int, rnd: Random): Int {
        var best = Int.MIN_VALUE
        for (c in legal) if (sc[c] > best) best = sc[c]
        val l = legal.filter { sc[it] != Int.MIN_VALUE && sc[it] >= best - margin }
        return if (l.isEmpty()) legal[0] else l[rnd.nextInt(l.size)]
    }

    /** Strong move: iterative deepening up to [maxDepth] within [ms]. */
    fun hard(b: C4Board, maxDepth: Int, ms: Long): Int {
        if (b.count == 0) return 3 // the centre column is the only winning first move
        val s = C4Search(System.nanoTime() + ms * 1_000_000L, 20)
        val m = s.bestMove(b.copy(), maxDepth)
        return if (m >= 0 && b.canPlay(m)) m else b.legalMoves().firstOrNull() ?: -1
    }

    /** Hint: moderate-depth best column. */
    fun hint(b: C4Board): Int = hard(b, 9, 350L)
}
