package com.example.minigames.chess

import kotlin.math.abs

/** FEN reading / writing (castling and en passant are kept; counters default to 0 / 1). */
object ChessFen {
    private const val LETTERS = " pnbrqk"

    fun parse(fen: String): ChessPos? {
        val parts = fen.trim().split(' ').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val rows = parts[0].split('/')
        if (rows.size != 8) return null
        val p = ChessPos()
        for ((r, row) in rows.withIndex()) {
            var c = 0
            for (ch in row) {
                if (ch in '1'..'8') {
                    c += ch - '0'
                } else {
                    val idx = LETTERS.indexOf(ch.lowercaseChar())
                    if (idx < 1 || c > 7) return null
                    p.b[r * 8 + c] = if (ch.isUpperCase()) idx else -idx
                    c++
                }
            }
            if (c != 8) return null
        }
        p.white = when (parts[1]) {
            "w" -> true
            "b" -> false
            else -> return null
        }
        var castle = 0
        val cs = if (parts.size > 2) parts[2] else "-"
        for (ch in cs) when (ch) {
            'K' -> castle = castle or 1
            'Q' -> castle = castle or 2
            'k' -> castle = castle or 4
            'q' -> castle = castle or 8
        }
        p.castle = castle
        p.ep = -1
        if (parts.size > 3 && parts[3] != "-" && parts[3].length == 2) {
            val f = parts[3][0] - 'a'
            val rk = parts[3][1] - '0'
            if (f in 0..7 && rk in 1..8) p.ep = (8 - rk) * 8 + f
        }
        p.half = if (parts.size > 4) parts[4].toIntOrNull() ?: 0 else 0
        p.full = if (parts.size > 5) parts[5].toIntOrNull() ?: 1 else 1
        var wk = 0
        var bk = 0
        for (v in p.b) {
            if (v == KING) wk++
            if (v == -KING) bk++
        }
        return if (wk == 1 && bk == 1) p else null
    }

    fun write(p: ChessPos): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var empty = 0
            for (c in 0..7) {
                val v = p.b[r * 8 + c]
                if (v == 0) {
                    empty++
                } else {
                    if (empty > 0) sb.append(empty)
                    empty = 0
                    val ch = LETTERS[abs(v)]
                    sb.append(if (v > 0) ch.uppercaseChar() else ch)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(if (p.white) " w " else " b ")
        var cs = ""
        if (p.castle and 1 != 0) cs += "K"
        if (p.castle and 2 != 0) cs += "Q"
        if (p.castle and 4 != 0) cs += "k"
        if (p.castle and 8 != 0) cs += "q"
        sb.append(if (cs.isEmpty()) "-" else cs)
        sb.append(' ')
        sb.append(if (p.ep >= 0) ChessSan.squareName(p.ep) else "-")
        sb.append(' ').append(p.half).append(' ').append(p.full)
        return sb.toString()
    }
}

/**
 * Forced-mate search for chess puzzles. "n" counts moves of the attacking side (the side to move in [mateIn]).
 * Stalemate is never a mate; the fifty-move rule and repetition are ignored (puzzle positions are short).
 */
object ChessMate {

    private fun isMate(q: ChessPos): Boolean = ChessRules.inCheck(q) && ChessRules.legal(q).isEmpty()

    /** The side to move can force checkmate within [n] of its own moves. */
    fun mateIn(p: ChessPos, n: Int): Boolean {
        if (n < 1) return false
        for (m in ChessRules.legal(p)) if (works(p, m, n)) return true
        return false
    }

    /** After the attacker's move [m] in [p]: mate is forced within [n] attacker moves (counting [m]). */
    private fun works(p: ChessPos, m: Int, n: Int): Boolean {
        val q = ChessRules.make(p, m)
        val replies = ChessRules.legal(q)
        if (replies.isEmpty()) return ChessRules.inCheck(q)      // mate (stalemate is not)
        if (n < 2) return false
        for (r in replies) if (!mateIn(ChessRules.make(q, r), n - 1)) return false
        return true
    }

    /** Is [q] (the defender to move) lost by force within [n] more attacker moves (n >= 1)? False when it is already mate. */
    fun defenderLoses(q: ChessPos, n: Int): Boolean {
        val replies = ChessRules.legal(q)
        if (replies.isEmpty() || n < 1) return false
        for (r in replies) if (!mateIn(ChessRules.make(q, r), n)) return false
        return true
    }

    /** Attacker moves of [p] that force mate within [n] moves. */
    fun solutions(p: ChessPos, n: Int): IntArray {
        val out = ArrayList<Int>()
        for (m in ChessRules.legal(p)) if (works(p, m, n)) out.add(m)
        return out.toIntArray()
    }

    /** Length of the shortest forced mate from [p] (side to move attacks), or 0 when none within [max]. */
    fun shortest(p: ChessPos, max: Int): Int {
        for (k in 1..max) if (mateIn(p, k)) return k
        return 0
    }

    /** The defender's reply in [q] that delays the mate longest (attacker needs at most [n] moves); 0 if no moves. */
    fun bestDefense(q: ChessPos, n: Int): Int {
        val replies = ChessRules.legal(q)
        var best = 0
        var bestK = -1
        for (r in replies) {
            val k = shortest(ChessRules.make(q, r), n).let { if (it == 0) n + 1 else it }
            if (k > bestK) {
                bestK = k
                best = r
            }
        }
        return best
    }
}
