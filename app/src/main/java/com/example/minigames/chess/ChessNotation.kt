package com.example.minigames.chess

import kotlin.math.abs

/*
 * Pure-Kotlin move notation: SAN (e4, Nbd7, exd5, O-O, e8=Q+, Qxf7#) and PGN text.
 * Moves are the engine's Int encoding; the game always starts from [ChessPos.start].
 */
object ChessSan {
    private val LETTER = charArrayOf(' ', ' ', 'N', 'B', 'R', 'Q', 'K')

    fun squareName(sq: Int): String = "" + ('a' + (sq and 7)) + ('8' - (sq shr 3))

    /** SAN without the check / mate suffix. */
    private fun base(p: ChessPos, m: Int, legal: IntArray): String {
        val from = mvFrom(m)
        val to = mvTo(m)
        val pc = abs(p.b[from])
        if (pc == KING && abs(to - from) == 2) return if (to > from) "O-O" else "O-O-O"
        val cap = ChessRules.isCapture(p, m)
        val sb = StringBuilder()
        if (pc == PAWN) {
            if (cap) sb.append('a' + (from and 7)).append('x')
            sb.append(squareName(to))
            if (mvPromo(m) != 0) sb.append('=').append(LETTER[mvPromo(m)])
        } else {
            sb.append(LETTER[pc])
            var ambiguous = false
            var sameFile = false
            var sameRank = false
            for (o in legal) {
                if (o == m) continue
                val of = mvFrom(o)
                if (mvTo(o) == to && of != from && abs(p.b[of]) == pc) {
                    ambiguous = true
                    if ((of and 7) == (from and 7)) sameFile = true
                    if ((of shr 3) == (from shr 3)) sameRank = true
                }
            }
            if (ambiguous) {
                if (!sameFile) sb.append('a' + (from and 7))
                else if (!sameRank) sb.append('8' - (from shr 3))
                else sb.append(squareName(from))
            }
            if (cap) sb.append('x')
            sb.append(squareName(to))
        }
        return sb.toString()
    }

    /** SAN of [m] in position [p] ([legal] = all legal moves of [p]). */
    fun san(p: ChessPos, m: Int, legal: IntArray = ChessRules.legal(p)): String {
        val b = base(p, m, legal)
        val q = ChessRules.make(p, m)
        if (!ChessRules.inCheck(q)) return b
        return if (ChessRules.legal(q).isEmpty()) "$b#" else "$b+"
    }

    private fun norm(s: String): String = s.filter { it !in "+#!?= " }.replace('0', 'O')

    /** The legal move whose SAN equals [text] (check marks, "=" and "!?" are ignored), or 0. */
    fun parse(p: ChessPos, text: String, legal: IntArray = ChessRules.legal(p)): Int {
        val want = norm(text)
        if (want.isEmpty()) return 0
        for (m in legal) if (norm(base(p, m, legal)) == want) return m
        return 0
    }
}

object ChessPgn {
    /** "1. e4 e5 2. Nf3 ..." for a game from the initial position (no result). */
    fun tokens(moves: IntArray): List<String> {
        val out = ArrayList<String>()
        var p = ChessPos.start()
        for ((i, m) in moves.withIndex()) {
            val legal = ChessRules.legal(p)
            if (i % 2 == 0) out.add("${i / 2 + 1}.")
            out.add(ChessSan.san(p, m, legal))
            p = ChessRules.make(p, m)
        }
        return out
    }

    /** PGN text of a game. [result] is "1-0", "0-1", "1/2-1/2" or "*"; [date] "yyyy.MM.dd". */
    fun build(moves: IntArray, white: String, black: String, result: String, date: String, event: String = "MiniGames"): String {
        val sb = StringBuilder()
        sb.append("[Event \"").append(event).append("\"]\n")
        sb.append("[Site \"?\"]\n")
        sb.append("[Date \"").append(date).append("\"]\n")
        sb.append("[Round \"-\"]\n")
        sb.append("[White \"").append(white).append("\"]\n")
        sb.append("[Black \"").append(black).append("\"]\n")
        sb.append("[Result \"").append(result).append("\"]\n\n")
        val toks = ArrayList(tokens(moves))
        toks.add(result)
        var col = 0
        for (tk in toks) {
            if (col > 0 && col + 1 + tk.length > 80) {
                sb.append('\n')
                col = 0
            }
            if (col > 0) {
                sb.append(' ')
                col++
            }
            sb.append(tk)
            col += tk.length
        }
        sb.append('\n')
        return sb.toString()
    }
}
