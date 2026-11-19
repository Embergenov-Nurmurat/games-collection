package com.example.minigames.chess

import kotlin.random.Random

/*
 * A small opening book (pure Kotlin). Lines are written in SAN from the initial position.
 * A line with an id gives a name to the opening (text key "chx_op_<id>"); lines without an id only
 * add variety for the computer. The computer plays book moves in the first plies, and the screen shows
 * the name of the deepest named line that has been played.
 */
object ChessBook {

    private class Def(val id: String?, val san: String)

    private val DEFS = listOf(
        Def("kp", "e4"), Def("qp", "d4"), Def("eng", "c4"), Def("reti", "Nf3"),
        Def("open", "e4 e5"),
        Def("bishop", "e4 e5 Bc4"),
        Def("vienna", "e4 e5 Nc3"),
        Def("kg", "e4 e5 f4"),
        Def("ruy", "e4 e5 Nf3 Nc6 Bb5"),
        Def("ital", "e4 e5 Nf3 Nc6 Bc4"),
        Def("piano", "e4 e5 Nf3 Nc6 Bc4 Bc5"),
        Def("twok", "e4 e5 Nf3 Nc6 Bc4 Nf6"),
        Def("scotch", "e4 e5 Nf3 Nc6 d4"),
        Def("four", "e4 e5 Nf3 Nc6 Nc3 Nf6"),
        Def("petrov", "e4 e5 Nf3 Nf6"),
        Def("philidor", "e4 e5 Nf3 d6"),
        Def("sicilian", "e4 c5"),
        Def("alapin", "e4 c5 c3"),
        Def("najdorf", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6"),
        Def("dragon", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6"),
        Def("french", "e4 e6"),
        Def("winawer", "e4 e6 d4 d5 Nc3 Bb4"),
        Def("caro", "e4 c6"),
        Def("scand", "e4 d5"),
        Def("pirc", "e4 d6 d4 Nf6 Nc3 g6"),
        Def("modern", "e4 g6"),
        Def("alekhine", "e4 Nf6"),
        Def("qg", "d4 d5 c4"),
        Def("qgd", "d4 d5 c4 e6"),
        Def("qga", "d4 d5 c4 dxc4"),
        Def("slav", "d4 d5 c4 c6"),
        Def("london", "d4 d5 Bf4"),
        Def("indian", "d4 Nf6"),
        Def("kid", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6"),
        Def("nimzo", "d4 Nf6 c4 e6 Nc3 Bb4"),
        Def("qid", "d4 Nf6 c4 e6 Nf3 b6"),
        Def("grunfeld", "d4 Nf6 c4 g6 Nc3 d5"),
        Def("dutch", "d4 f5"),
        Def("sym", "c4 c5"),
        // deeper lines (variety for the computer; they keep the name of the line they extend)
        Def(null, "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O"),
        Def(null, "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5"),
        Def(null, "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3 d6 O-O O-O"),
        Def(null, "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bxb4 c3 Ba5 d4 exd4 O-O"),
        Def(null, "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5 Bb5 c6 dxc6 bxc6 Be2 h6 Nf3 e4"),
        Def(null, "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nc3 Bb4 Nxc6 bxc6 Bd3 d5"),
        Def(null, "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5 Be3 Qf6 c3 Nge7 Bc4 Ne5"),
        Def(null, "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4 d4 d5 Bd3 Nc6 O-O Be7"),
        Def(null, "e4 e5 Nf3 Nc6 Nc3 Nf6 Bb5 Bb4 O-O O-O d3 d6 Bg5 Bxc3 bxc3 Qe7"),
        Def(null, "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5 Nf6 Bc4 d5 exd5 Bd6"),
        Def(null, "e4 e5 Nc3 Nf6 f4 d5 fxe5 Nxe4 Nf3 Be7 d4 O-O Bd3 f5"),
        Def(null, "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 e5 Nb3 Be6 f3 Be7 Qd2 O-O"),
        Def(null, "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O Qd2 Nc6"),
        Def(null, "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 d6 Bg5 e6 Qd2 a6 O-O-O Bd7"),
        Def(null, "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7 Be3 a6 Be2 Nf6 O-O Bb4"),
        Def(null, "e4 c5 c3 d5 exd5 Qxd5 d4 Nf6 Nf3 e6 Bd3 Nc6 O-O Be7"),
        Def(null, "e4 c5 c3 Nf6 e5 Nd5 d4 cxd4 Nf3 Nc6 cxd4 d6 Bc4 Nb6 Bb5 dxe5"),
        Def(null, "e4 e6 d4 d5 Nc3 Nf6 Bg5 Be7 e5 Nfd7 Bxe7 Qxe7 f4 O-O Nf3 c5"),
        Def(null, "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6 a3 Nh6 b4 cxd4 cxd4 Nf5"),
        Def(null, "e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7 Bd3 c5 c3 Nc6 Ne2 cxd4 cxd4 f6"),
        Def(null, "e4 e6 d4 d5 Nc3 Bb4 e5 c5 a3 Bxc3 bxc3 Ne7 Qg4 Qc7 Qxg7 Rg8 Qxh7 cxd4"),
        Def(null, "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6 Nf3 Nd7 h5 Bh7"),
        Def(null, "e4 c6 d4 d5 e5 Bf5 Nf3 e6 Be2 c5 Be3 Nd7 O-O Ne7"),
        Def(null, "e4 c6 d4 d5 exd5 cxd5 c4 Nf6 Nc3 e6 Nf3 Be7 cxd5 Nxd5 Bd3 Nc6"),
        Def(null, "e4 d5 exd5 Qxd5 Nc3 Qa5 d4 Nf6 Nf3 c6 Bc4 Bf5 Bd2 e6 Nd5 Qd8"),
        Def(null, "e4 d5 exd5 Nf6 d4 Nxd5 Nf3 g6 Be2 Bg7 O-O O-O c4 Nb6"),
        Def(null, "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2 O-O O-O c6 a4 Nbd7"),
        Def(null, "e4 g6 d4 Bg7 Nc3 d6 Be3 a6 Qd2 b5 f3 Nd7 Bd3 Bb7"),
        Def(null, "e4 Nf6 e5 Nd5 d4 d6 Nf3 Bg4 Be2 e6 O-O Be7 h3 Bh5 c4 Nb6"),
        Def(null, "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 Nbd7 Rc1 c6 Bd3 dxc4"),
        Def(null, "d4 d5 c4 e6 Nc3 Nf6 Nf3 Be7 Bf4 O-O e3 c5 dxc5 Bxc5 Qc2 Nc6"),
        Def(null, "d4 d5 c4 e6 Nf3 Nf6 g3 Be7 Bg2 O-O O-O dxc4 Qc2 a6 Qxc4 b5 Qc2 Bb7"),
        Def(null, "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5 O-O a6 dxc5 Qxd1 Rxd1 Bxc5"),
        Def(null, "d4 d5 c4 dxc4 e3 Nf6 Bxc4 e6 Nf3 c5 O-O a6 Qe2 b5 Bb3 Nc6"),
        Def(null, "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4 a4 Bf5 e3 e6 Bxc4 Bb4 O-O Nbd7"),
        Def(null, "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6 e3 Nbd7 Bd3 dxc4 Bxc4 b5 Bd3 Bb7"),
        Def(null, "d4 d5 Bf4 Nf6 e3 e6 Nf3 Bd6 Bg3 O-O Nbd2 c5 c3 Nc6 Bd3 b6"),
        Def(null, "d4 d5 Nf3 Nf6 Bf4 e6 e3 c5 c3 Nc6 Nbd2 Bd6 Bg3 O-O Bd3 Qe7"),
        Def(null, "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7"),
        Def(null, "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3 O-O Be3 e5 Nge2 c6 Qd2 Nbd7"),
        Def(null, "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7 Nf3 c5 Be3 Qa5"),
        Def(null, "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O Nc6 a3 Bxc3"),
        Def(null, "d4 Nf6 c4 e6 Nc3 Bb4 Qc2 O-O a3 Bxc3 Qxc3 b6 Bg5 Bb7 e3 d6"),
        Def(null, "d4 Nf6 c4 e6 Nf3 b6 g3 Ba6 b3 Bb4 Bd2 Be7 Bg2 c6 Bc3 d5"),
        Def(null, "d4 Nf6 c4 e6 Nf3 b6 g3 Bb7 Bg2 Be7 O-O O-O Nc3 Ne4 Qc2 Nxc3 Qxc3 c5"),
        Def(null, "d4 f5 g3 Nf6 Bg2 e6 Nf3 Be7 O-O O-O c4 d6 Nc3 Qe8"),
        Def(null, "d4 f5 Nf3 Nf6 g3 e6 Bg2 Be7 O-O O-O c4 d6 Nc3 Qe8 Qc2 Qh5"),
        Def(null, "c4 e5 Nc3 Nf6 Nf3 Nc6 g3 d5 cxd5 Nxd5 Bg2 Nb6 O-O Be7"),
        Def(null, "c4 e5 Nc3 Nc6 g3 g6 Bg2 Bg7 d3 d6 Rb1 Nge7 Nf3 O-O"),
        Def(null, "c4 c5 Nf3 Nf6 Nc3 Nc6 g3 g6 Bg2 Bg7 O-O O-O d4 cxd4 Nxd4 Nxd4"),
        Def(null, "c4 Nf6 Nc3 e6 e4 d5 e5 d4 exf6 dxc3 bxc3 Qxf6 d4 Be7"),
        Def(null, "c4 e6 Nc3 d5 d4 Nf6 Nf3 Be7 Bg5 O-O e3 h6 Bh4 b6"),
        Def(null, "Nf3 d5 g3 Nf6 Bg2 e6 O-O Be7 d3 O-O Nbd2 c5 e4 Nc6"),
        Def(null, "Nf3 d5 c4 d4 e3 Nc6 exd4 Nxd4 Nxd4 Qxd4 Nc3 e5 d3 Nf6"),
        Def(null, "Nf3 Nf6 c4 g6 Nc3 Bg7 e4 d6 d4 O-O Be2 e5 O-O Nc6 d5 Ne7"),
        Def(null, "Nf3 Nf6 g3 g6 Bg2 Bg7 O-O O-O d4 d6 c4 Nbd7 Nc3 e5 e4 c6")
    )

    class Line(val id: String?, val moves: IntArray)

    /** All lines parsed into engine moves. A SAN that does not parse is dropped (the tests make sure none is). */
    val lines: List<Line> by lazy {
        val out = ArrayList<Line>()
        for (d in DEFS) {
            var p = ChessPos.start()
            val mv = ArrayList<Int>()
            var okLine = true
            for (tok in d.san.split(' ')) {
                val m = ChessSan.parse(p, tok)
                if (m == 0) {
                    okLine = false
                    break
                }
                mv.add(m)
                p = ChessRules.make(p, m)
            }
            if (okLine) out.add(Line(d.id, mv.toIntArray()))
        }
        out
    }

    /** Number of definitions (for the tests: it must equal [lines].size). */
    val definedCount: Int get() = DEFS.size

    private fun startsWith(h: IntArray, n: Int, line: IntArray): Boolean {
        if (n > line.size) return false
        for (i in 0 until n) if (h[i] != line[i]) return false
        return true
    }

    /** Id of the opening (the longest named line that is a prefix of [history]) or null. */
    fun nameFor(history: IntArray): String? {
        var best: Line? = null
        for (l in lines) {
            if (l.id == null || l.moves.size > history.size) continue
            if (!startsWith(history, l.moves.size, l.moves)) continue
            if (best == null || l.moves.size > best.moves.size) best = l
        }
        return best?.id
    }

    /** Is [m] a book continuation of the first [n] moves of [history]? */
    fun isBookMove(history: IntArray, n: Int, m: Int): Boolean {
        for (l in lines) {
            if (l.moves.size <= n) continue
            if (startsWith(history, n, l.moves) && l.moves[n] == m) return true
        }
        return false
    }

    /** A random book move that continues [history], or 0 when the game has left the book. */
    fun next(history: IntArray, rnd: Random = Random.Default): Int {
        val cands = ArrayList<Int>()
        for (l in lines) {
            if (l.moves.size <= history.size) continue
            if (startsWith(history, history.size, l.moves)) cands.add(l.moves[history.size])
        }
        if (cands.isEmpty()) return 0
        return cands[rnd.nextInt(cands.size)]
    }
}
