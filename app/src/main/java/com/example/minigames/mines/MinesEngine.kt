package com.example.minigames.mines

import kotlin.random.Random

/** One difficulty level: board size and number of mines. */
class MinesLevel(val cols: Int, val rows: Int, val mines: Int)

/** Pure Kotlin Minesweeper (no android.*): the rules, the first-tap guarantee, chording and save/load. */
object MinesEngine {
    /** Easy 9x9 / 10, medium 12x14 / 30, hard 16x18 / 55 (columns x rows). */
    val LEVELS: Array<MinesLevel> = arrayOf(
        MinesLevel(9, 9, 10),
        MinesLevel(12, 14, 30),
        MinesLevel(16, 18, 55)
    )

    // cell states
    const val HIDDEN = 0
    const val OPEN = 1
    const val FLAG = 2

    // game status
    const val PLAYING = 0
    const val WON = 1
    const val LOST = 2

    // results of reveal / chord
    const val NOOP = 0
    const val OPENED = 1
    const val BOOM = 2
    const val CLEARED = 3

    /** Neighbour lists of a cols x rows board (index = row * cols + col). */
    fun neighbours(cols: Int, rows: Int): Array<IntArray> = Array(cols * rows) { i ->
        val r = i / cols
        val c = i % cols
        val l = ArrayList<Int>(8)
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = r + dr
            val cc = c + dc
            if (rr in 0 until rows && cc in 0 until cols) l.add(rr * cols + cc)
        }
        l.toIntArray()
    }
}

/**
 * A game on one board. Mines are placed on the first [reveal] so that the tapped cell (and, when the board has room,
 * all its neighbours) is safe. [st] holds the cell states, [mine] the mines (all false until placed), [adj] the
 * number of neighbouring mines.
 */
class MinesGame(val level: Int) {
    private val lv = MinesEngine.LEVELS[level]
    val cols: Int = lv.cols
    val rows: Int = lv.rows
    val mines: Int = lv.mines
    val n: Int = cols * rows

    val nb: Array<IntArray> = cache(cols, rows)

    val mine = BooleanArray(n)
    val adj = IntArray(n)
    val st = IntArray(n)

    var placed = false
        private set
    var status = MinesEngine.PLAYING
        private set
    /** The first mine that was hit (-1 when none). */
    var boom = -1
        private set
    /** Number of opened SAFE cells (an exploded mine is not counted). */
    var opened = 0
        private set
    var flags = 0
        private set
    /** Elapsed time in 1/60 s ticks, maintained by the screen. */
    var frames = 0

    /** Cells opened by the last [reveal] / [chord] (for animations). */
    val newly = ArrayList<Int>()

    val safeTotal: Int get() = n - mines

    /** Mines minus flags (can be negative). */
    val minesLeft: Int get() = mines - flags

    val percent: Int get() = opened * 100 / safeTotal

    // ------------------------------- actions --------------------------------

    /** Opens a hidden, unflagged cell. */
    fun reveal(i: Int, rnd: Random = Random.Default): Int {
        newly.clear()
        if (status != MinesEngine.PLAYING || i < 0 || i >= n || st[i] != MinesEngine.HIDDEN) return MinesEngine.NOOP
        if (!placed) place(i, rnd)
        if (mine[i]) return lose(i)
        flood(i)
        return finishStep()
    }

    /** Puts a flag on a hidden cell or removes it. Returns true when something changed. */
    fun toggleFlag(i: Int): Boolean {
        if (status != MinesEngine.PLAYING || i < 0 || i >= n) return false
        when (st[i]) {
            MinesEngine.HIDDEN -> {
                st[i] = MinesEngine.FLAG
                flags++
            }
            MinesEngine.FLAG -> {
                st[i] = MinesEngine.HIDDEN
                flags--
            }
            else -> return false
        }
        return true
    }

    fun flagsAround(i: Int): Int {
        var k = 0
        for (j in nb[i]) if (st[j] == MinesEngine.FLAG) k++
        return k
    }

    /** True when [chord] on [i] would do something: an open number with exactly that many flags around it. */
    fun canChord(i: Int): Boolean {
        if (status != MinesEngine.PLAYING || i < 0 || i >= n) return false
        if (st[i] != MinesEngine.OPEN || adj[i] == 0 || flagsAround(i) != adj[i]) return false
        for (j in nb[i]) if (st[j] == MinesEngine.HIDDEN) return true
        return false
    }

    /** Opens every hidden, unflagged neighbour of an open number whose flag count equals the number. */
    fun chord(i: Int): Int {
        newly.clear()
        if (!canChord(i)) return MinesEngine.NOOP
        var hit = -1
        for (j in nb[i]) {
            if (st[j] != MinesEngine.HIDDEN) continue
            if (mine[j]) {
                st[j] = MinesEngine.OPEN
                if (hit < 0) hit = j
            } else {
                flood(j)
            }
        }
        if (hit >= 0) return lose(hit)
        return finishStep()
    }

    // ------------------------------- internals ------------------------------

    private fun place(first: Int, rnd: Random) {
        val banned = BooleanArray(n)
        banned[first] = true
        if (n - 1 - nb[first].size >= mines) for (j in nb[first]) banned[j] = true
        val cand = ArrayList<Int>(n)
        for (i in 0 until n) if (!banned[i]) cand.add(i)
        cand.shuffle(rnd)
        for (k in 0 until mines) mine[cand[k]] = true
        computeAdj()
        placed = true
    }

    private fun computeAdj() {
        for (i in 0 until n) {
            var k = 0
            for (j in nb[i]) if (mine[j]) k++
            adj[i] = k
        }
    }

    private fun flood(start: Int) {
        val stack = ArrayList<Int>()
        st[start] = MinesEngine.OPEN
        opened++
        newly.add(start)
        stack.add(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeAt(stack.size - 1)
            if (adj[i] != 0) continue
            for (j in nb[i]) {
                if (st[j] != MinesEngine.HIDDEN) continue
                st[j] = MinesEngine.OPEN
                opened++
                newly.add(j)
                stack.add(j)
            }
        }
    }

    private fun lose(i: Int): Int {
        st[i] = MinesEngine.OPEN
        boom = i
        status = MinesEngine.LOST
        return MinesEngine.BOOM
    }

    private fun finishStep(): Int {
        if (opened < safeTotal) return MinesEngine.OPENED
        status = MinesEngine.WON
        // flag every remaining mine
        for (i in 0 until n) {
            if (mine[i] && st[i] == MinesEngine.HIDDEN) {
                st[i] = MinesEngine.FLAG
                flags++
            }
        }
        return MinesEngine.CLEARED
    }

    // ------------------------------- save / load ----------------------------

    /** `M1;level;frames;status;boom;placed;cells` where each cell is one letter: 'a' + state * 2 + (mine ? 1 : 0). */
    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("M1;").append(level).append(';').append(frames).append(';').append(status).append(';')
            .append(boom).append(';').append(if (placed) 1 else 0).append(';')
        for (i in 0 until n) sb.append(('a' + st[i] * 2 + (if (mine[i]) 1 else 0)))
        return sb.toString()
    }

    companion object {
        private val NB_CACHE = HashMap<Int, Array<IntArray>>()

        private fun cache(cols: Int, rows: Int): Array<IntArray> {
            val key = cols * 100 + rows
            var a = NB_CACHE[key]
            if (a == null) {
                a = MinesEngine.neighbours(cols, rows)
                NB_CACHE[key] = a
            }
            return a
        }

        /** Restores a game, or returns null when the text is damaged or inconsistent. */
        fun deserialize(s: String): MinesGame? {
            try {
                val p = s.split(';')
                if (p.size != 7 || p[0] != "M1") return null
                val level = p[1].toInt()
                if (level !in MinesEngine.LEVELS.indices) return null
                val g = MinesGame(level)
                val frames = p[2].toInt()
                val status = p[3].toInt()
                val boom = p[4].toInt()
                val placed = p[5].toInt()
                val cells = p[6]
                if (frames < 0 || status !in 0..2 || placed !in 0..1 || cells.length != g.n) return null
                if (boom < -1 || boom >= g.n) return null
                var mineCount = 0
                var opened = 0
                var flags = 0
                for (i in 0 until g.n) {
                    val v = cells[i] - 'a'
                    if (v < 0 || v > 5) return null
                    g.st[i] = v / 2
                    g.mine[i] = v % 2 == 1
                    if (g.mine[i]) mineCount++
                    if (g.st[i] == MinesEngine.OPEN && !g.mine[i]) opened++
                    if (g.st[i] == MinesEngine.FLAG) flags++
                }
                if (placed == 1) {
                    if (mineCount != g.mines) return null
                } else {
                    if (mineCount != 0 || opened != 0 || status != MinesEngine.PLAYING || boom != -1) return null
                }
                // status must agree with the cells
                var openMines = 0
                for (i in 0 until g.n) if (g.mine[i] && g.st[i] == MinesEngine.OPEN) openMines++
                when (status) {
                    MinesEngine.PLAYING -> if (openMines != 0 || boom != -1 || opened >= g.safeTotal) return null
                    MinesEngine.WON -> if (openMines != 0 || boom != -1 || opened != g.safeTotal) return null
                    else -> if (openMines == 0 || boom < 0 || !g.mine[boom] || g.st[boom] != MinesEngine.OPEN) return null
                }
                g.frames = frames
                g.status = status
                g.boom = boom
                g.placed = placed == 1
                g.opened = opened
                g.flags = flags
                if (g.placed) g.computeAdj()
                return g
            } catch (e: Exception) {
                return null
            }
        }
    }
}
