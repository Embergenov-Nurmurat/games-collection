package com.example.minigames.chess

import kotlin.math.abs

/** Result of a game analysis. Index i of [score]/[best] describes the position after i moves. */
class ReviewResult(
    val moves: IntArray,
    /** Evaluation in centipawns from WHITE's point of view (mate = +-2000). */
    val score: IntArray,
    /** Best move found in the position after i moves (0 = none). */
    val best: IntArray,
    /** For every played move: 0 none, 1 inaccuracy, 2 mistake, 3 blunder. */
    val mark: IntArray,
    /** Centipawn loss of every played move (compressed scale). */
    val loss: IntArray
) {
    fun count(side: Int, kind: Int): Int {
        var n = 0
        for (i in mark.indices) if (i % 2 == side && mark[i] == kind) n++
        return n
    }
}

/**
 * Game analysis with the engine: every position is searched shallowly, moves that look bad are re-checked
 * deeper. A move is judged by how much the evaluation (from the mover's side) drops compared with the
 * best move found.
 */
object ChessReview {
    const val INACCURACY = 70
    const val MISTAKE = 150
    const val BLUNDER = 300

    private const val MATE_CP = 1500
    private const val SHOW_CAP = 2000
    private const val MAX_VERIFY = 12

    /** Evaluation scale: equal-ish positions count fully, big advantages are compressed. */
    private fun cmp(x: Int): Int {
        val c = x.coerceIn(-MATE_CP, MATE_CP)
        val a = abs(c)
        val r = if (a <= 200) a else 200 + (a - 200) * 35 / 100
        return if (c < 0) -r else r
    }

    private class Eval(val score: Int, val best: Int)

    /** Score from the side to move and the best move of [p]. */
    private fun evalPos(p: ChessPos, ai: ChessAI): Eval {
        val legal = ChessRules.legal(p)
        if (legal.isEmpty()) return Eval(if (ChessRules.inCheck(p)) -100_000 else 0, 0)
        if (ChessRules.insufficient(p)) return Eval(0, legal[0])
        val m = ai.bestMove(p)
        return Eval(ai.lastScore, m)
    }

    private fun mark(loss: Int): Int = when {
        loss >= BLUNDER -> 3
        loss >= MISTAKE -> 2
        loss >= INACCURACY -> 1
        else -> 0
    }

    /**
     * Analyses [moves] (a game from the initial position). [progress] gets 0..100; [cancelled] is polled between
     * searches and makes the function return null.
     */
    fun analyse(
        moves: IntArray,
        depth: Int = 3,
        timeMs: Long = 300L,
        progress: (Int) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ): ReviewResult? {
        val n = moves.size
        val positions = ArrayList<ChessPos>(n + 1)
        var p = ChessPos.start()
        positions.add(p)
        for (m in moves) {
            p = ChessRules.make(p, m)
            positions.add(p)
        }
        val ai = ChessAI(depth, timeMs, 0)
        val stm = IntArray(n + 1)          // score from the side to move
        val best = IntArray(n + 1)
        for (i in 0..n) {
            if (cancelled()) return null
            val e = evalPos(positions[i], ai)
            stm[i] = e.score
            best[i] = e.best
            progress((i + 1) * 80 / (n + 1))
        }
        val loss = IntArray(n)
        val mk = IntArray(n)
        val forced = BooleanArray(n)
        for (k in 0 until n) {
            forced[k] = ChessRules.legal(positions[k]).size <= 1
            loss[k] = if (forced[k] || moves[k] == best[k] || ChessBook.isBookMove(moves, k, moves[k])) 0 else maxOf(0, cmp(stm[k]) + cmp(stm[k + 1]))
            mk[k] = mark(loss[k])
        }

        // second look at the suspicious moves (deeper search of the position before and after the move)
        val sus = (0 until n).filter { mk[it] > 0 }.sortedByDescending { loss[it] }.take(MAX_VERIFY)
        if (sus.isNotEmpty()) {
            val deep = ChessAI(depth + 1, timeMs * 3, 0)
            for ((j, k) in sus.withIndex()) {
                if (cancelled()) return null
                val a = evalPos(positions[k], deep)
                val b = evalPos(positions[k + 1], deep)
                best[k] = a.best
                loss[k] = if (moves[k] == a.best || ChessBook.isBookMove(moves, k, moves[k])) 0 else maxOf(0, cmp(a.score) + cmp(b.score))
                mk[k] = mark(loss[k])
                progress(80 + (j + 1) * 20 / sus.size)
            }
        }
        progress(100)

        val white = IntArray(n + 1) { i ->
            val s = stm[i].coerceIn(-SHOW_CAP, SHOW_CAP)
            if (i % 2 == 0) s else -s
        }
        return ReviewResult(moves.copyOf(), white, best, mk, loss)
    }
}
