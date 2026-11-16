package com.example.minigames.sudoku

import kotlin.random.Random

/** One generated puzzle: [givens] (0 = empty) and its unique [solution]. */
class SudokuPuzzle(val diff: Int, val givens: IntArray, val solution: IntArray) {
    val clues: Int get() = givens.count { it != 0 }
}

/**
 * Sudoku rules, a backtracking solver (counts solutions, so uniqueness can be proven) and a generator.
 * Cells are numbered 0..80 row by row; digits are 1..9, 0 means empty. Pure Kotlin (no android.*).
 */
object SudokuEngine {

    const val EASY = 0
    const val MEDIUM = 1
    const val HARD = 2

    /** Target clue counts (the symmetric removal ends at the target or one below it). */
    val CLUE_TARGET = intArrayOf(40, 32, 26)

    private const val ALL = 0x1FF

    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (i / 27) * 3 + (i % 9) / 3

    /** The 20 cells that share a row, column or box with each cell. */
    val PEERS: Array<IntArray> = Array(81) { i ->
        val list = ArrayList<Int>()
        for (j in 0 until 81) {
            if (j != i && (row(j) == row(i) || col(j) == col(i) || box(j) == box(i))) list.add(j)
        }
        list.toIntArray()
    }

    // ------------------------------- solver ---------------------------------

    private class Solver(val g: IntArray, val rnd: Random?) {
        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)
        var count = 0
        var limit = 1
        var first: IntArray? = null
        var valid = true

        init {
            for (i in 0 until 81) {
                val d = g[i]
                if (d == 0) continue
                if (d !in 1..9) {
                    valid = false
                    continue
                }
                val bit = 1 shl (d - 1)
                if ((rows[row(i)] or cols[col(i)] or boxes[box(i)]) and bit != 0) valid = false
                rows[row(i)] = rows[row(i)] or bit
                cols[col(i)] = cols[col(i)] or bit
                boxes[box(i)] = boxes[box(i)] or bit
            }
        }

        fun run(lim: Int): Int {
            limit = lim
            count = 0
            first = null
            if (!valid) return 0
            search()
            return count
        }

        private fun search() {
            var best = -1
            var bestMask = 0
            var bestN = 10
            for (i in 0 until 81) {
                if (g[i] != 0) continue
                val m = ALL and (rows[row(i)] or cols[col(i)] or boxes[box(i)]).inv()
                val n = m.countOneBits()
                if (n < bestN) {
                    bestN = n
                    best = i
                    bestMask = m
                    if (n <= 1) break
                }
            }
            if (best == -1) {
                count++
                if (first == null) first = g.copyOf()
                return
            }
            if (bestN == 0) return
            val digits = IntArray(bestN)
            var k = 0
            for (d in 1..9) if (bestMask and (1 shl (d - 1)) != 0) digits[k++] = d
            if (rnd != null) {
                for (a in digits.size - 1 downTo 1) {
                    val b = rnd.nextInt(a + 1)
                    val tmp = digits[a]; digits[a] = digits[b]; digits[b] = tmp
                }
            }
            val r = row(best)
            val c = col(best)
            val b = box(best)
            for (d in digits) {
                val bit = 1 shl (d - 1)
                g[best] = d
                rows[r] = rows[r] or bit
                cols[c] = cols[c] or bit
                boxes[b] = boxes[b] or bit
                search()
                g[best] = 0
                rows[r] = rows[r] and bit.inv()
                cols[c] = cols[c] and bit.inv()
                boxes[b] = boxes[b] and bit.inv()
                if (count >= limit) return
            }
        }
    }

    /** Number of solutions of [grid] (stops counting at [limit]). 0 for an inconsistent grid. The grid is not modified. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int = Solver(grid.copyOf(), null).run(limit)

    /** Some solution of [grid], or null when it has none. */
    fun solve(grid: IntArray): IntArray? {
        val s = Solver(grid.copyOf(), null)
        return if (s.run(1) > 0) s.first else null
    }

    /** A complete, valid grid. */
    fun randomSolved(rnd: Random): IntArray {
        val s = Solver(IntArray(81), rnd)
        s.run(1)
        return s.first!!
    }

    /** True when [g] is a completely filled grid obeying all the rules. */
    fun isValidSolution(g: IntArray): Boolean {
        if (g.size != 81) return false
        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)
        for (i in 0 until 81) {
            val d = g[i]
            if (d !in 1..9) return false
            val bit = 1 shl (d - 1)
            if ((rows[row(i)] or cols[col(i)] or boxes[box(i)]) and bit != 0) return false
            rows[row(i)] = rows[row(i)] or bit
            cols[col(i)] = cols[col(i)] or bit
            boxes[box(i)] = boxes[box(i)] or bit
        }
        return true
    }

    // ------------------------------- grading --------------------------------

    /**
     * Logical difficulty of [grid]: 0 = solvable with naked singles only, 1 = solvable with naked and hidden
     * singles, 2 = needs something stronger (or guessing).
     */
    fun grade(grid: IntArray): Int {
        if (singlesSolve(grid, false)) return 0
        return if (singlesSolve(grid, true)) 1 else 2
    }

    private fun singlesSolve(grid: IntArray, hidden: Boolean): Boolean {
        val g = grid.copyOf()
        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)
        for (i in 0 until 81) {
            val d = g[i]
            if (d == 0) continue
            val bit = 1 shl (d - 1)
            rows[row(i)] = rows[row(i)] or bit
            cols[col(i)] = cols[col(i)] or bit
            boxes[box(i)] = boxes[box(i)] or bit
        }
        fun put(i: Int, d: Int) {
            val bit = 1 shl (d - 1)
            g[i] = d
            rows[row(i)] = rows[row(i)] or bit
            cols[col(i)] = cols[col(i)] or bit
            boxes[box(i)] = boxes[box(i)] or bit
        }
        var progress = true
        while (progress) {
            progress = false
            for (i in 0 until 81) {
                if (g[i] != 0) continue
                val m = ALL and (rows[row(i)] or cols[col(i)] or boxes[box(i)]).inv()
                if (m != 0 && m and (m - 1) == 0) {
                    put(i, m.countTrailingZeroBits() + 1)
                    progress = true
                }
            }
            if (progress || !hidden) continue
            // hidden singles in rows, columns and boxes
            for (unit in 0 until 27) {
                val kind = unit / 9
                val u = unit % 9
                for (d in 1..9) {
                    val bit = 1 shl (d - 1)
                    val used = when (kind) {
                        0 -> rows[u]
                        1 -> cols[u]
                        else -> boxes[u]
                    }
                    if (used and bit != 0) continue
                    var where = -1
                    var n = 0
                    for (k in 0 until 9) {
                        val i = when (kind) {
                            0 -> u * 9 + k
                            1 -> k * 9 + u
                            else -> (u / 3) * 27 + (u % 3) * 3 + (k / 3) * 9 + (k % 3)
                        }
                        if (g[i] == 0 && (rows[row(i)] or cols[col(i)] or boxes[box(i)]) and bit == 0) {
                            n++
                            where = i
                        }
                    }
                    if (n == 1) {
                        put(where, d)
                        progress = true
                    }
                }
            }
        }
        for (i in 0 until 81) if (g[i] == 0) return false
        return true
    }

    // ------------------------------ generator -------------------------------

    /**
     * Random puzzle with a UNIQUE solution: cells are removed in 180-degree symmetric pairs from a random
     * solved grid while the solution stays unique, until the clue target is reached. The result is also graded
     * logically (easy = naked singles, medium = singles, hard = harder than singles); generation retries until the
     * grade fits or [budgetMs] is used up, then returns the closest puzzle found.
     */
    fun generate(diff: Int, rnd: Random = Random.Default, budgetMs: Long = 1500L): SudokuPuzzle {
        val d = diff.coerceIn(0, 2)
        val target = CLUE_TARGET[d]
        var bestGrid: IntArray? = null
        var bestSol: IntArray? = null
        var bestScore = Int.MAX_VALUE
        val t0 = System.nanoTime()
        var attempt = 0
        while (true) {
            attempt++
            val sol = randomSolved(rnd)
            val g = sol.copyOf()
            val order = (0..40).toMutableList()
            order.shuffle(rnd)
            var clues = 81
            for (i in order) {
                if (clues <= target) break
                val j = 80 - i
                val a = g[i]
                val b = g[j]
                g[i] = 0
                g[j] = 0
                if (countSolutions(g, 2) == 1) {
                    clues -= if (i == j) 1 else 2
                } else {
                    g[i] = a
                    g[j] = b
                }
            }
            // score: grade mismatch weighs more than a few extra clues
            val score = Math.abs(grade(g) - d) * 100 + (clues - target).coerceAtLeast(0) * 5 + (if (d == 2 && grade(g) == 2) 0 else 0)
            if (score < bestScore) {
                bestScore = score
                bestGrid = g
                bestSol = sol
            }
            if (bestScore == 0) break
            if (attempt >= 60 || (System.nanoTime() - t0) / 1_000_000L > budgetMs) break
        }
        return SudokuPuzzle(d, bestGrid!!, bestSol!!)
    }
}

/**
 * State of one game: entered digits, pencil marks, undo, mistakes and hints, plus (de)serialisation.
 * A "mistake" is a digit that differs from the solution; it is only counted.
 */
class SudokuGame(val puzzle: SudokuPuzzle) {
    val given = BooleanArray(81) { puzzle.givens[it] != 0 }
    val v = IntArray(81) { puzzle.givens[it] }
    /** Pencil marks as bitmasks (bit d-1 = digit d). */
    val notes = IntArray(81)
    var mistakes = 0
    var hints = 0
    /** Playing time in 1/60 s steps (advanced by the view). */
    var frames = 0

    private val undoStack = ArrayList<IntArray>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    private fun snapshot() {
        val s = IntArray(162)
        System.arraycopy(v, 0, s, 0, 81)
        System.arraycopy(notes, 0, s, 81, 81)
        undoStack.add(s)
        if (undoStack.size > 400) undoStack.removeAt(0)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val s = undoStack.removeAt(undoStack.size - 1)
        System.arraycopy(s, 0, v, 0, 81)
        System.arraycopy(s, 81, notes, 0, 81)
        return true
    }

    /**
     * Enters [d] into [cell]. Returns 0 when nothing changed, 1 for a digit that matches the solution,
     * 2 for a wrong digit (counted as a mistake).
     */
    fun place(cell: Int, d: Int): Int {
        if (cell !in 0..80 || d !in 1..9 || given[cell] || v[cell] == d) return 0
        snapshot()
        v[cell] = d
        notes[cell] = 0
        val bit = 1 shl (d - 1)
        for (p in SudokuEngine.PEERS[cell]) notes[p] = notes[p] and bit.inv()
        return if (d == puzzle.solution[cell]) 1 else {
            mistakes++
            2
        }
    }

    /** Toggles a pencil mark (only in an empty, non-given cell). Returns true when something changed. */
    fun toggleNote(cell: Int, d: Int): Boolean {
        if (cell !in 0..80 || d !in 1..9 || given[cell] || v[cell] != 0) return false
        snapshot()
        notes[cell] = notes[cell] xor (1 shl (d - 1))
        return true
    }

    fun erase(cell: Int): Boolean {
        if (cell !in 0..80 || given[cell] || (v[cell] == 0 && notes[cell] == 0)) return false
        snapshot()
        v[cell] = 0
        notes[cell] = 0
        return true
    }

    /** Cells that are empty or wrong and could receive a hint. */
    private fun hintable(): List<Int> = (0 until 81).filter { !given[it] && v[it] != puzzle.solution[it] }

    /** Reveals the correct digit in [selected] (when it is empty/wrong) or in a random open cell. Returns the cell or -1. */
    fun hint(selected: Int, rnd: Random = Random.Default): Int {
        val open = hintable()
        if (open.isEmpty()) return -1
        val cell = if (selected in open) selected else open[rnd.nextInt(open.size)]
        snapshot()
        val d = puzzle.solution[cell]
        v[cell] = d
        notes[cell] = 0
        val bit = 1 shl (d - 1)
        for (p in SudokuEngine.PEERS[cell]) notes[p] = notes[p] and bit.inv()
        hints++
        return cell
    }

    fun isSolved(): Boolean {
        for (i in 0 until 81) if (v[i] != puzzle.solution[i]) return false
        return true
    }

    /** True when the digit in [cell] also stands in one of its peers (a rule violation). */
    fun conflict(cell: Int): Boolean {
        val d = v[cell]
        if (d == 0) return false
        for (p in SudokuEngine.PEERS[cell]) if (v[p] == d) return true
        return false
    }

    /** How many cells of digit [d] are correctly placed (0..9). */
    fun correctCount(d: Int): Int {
        var n = 0
        for (i in 0 until 81) if (v[i] == d && puzzle.solution[i] == d) n++
        return n
    }

    fun filledCount(): Int = v.count { it != 0 }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("v1|").append(puzzle.diff).append('|').append(frames).append('|')
            .append(mistakes).append('|').append(hints).append('|')
        for (x in puzzle.givens) sb.append(x)
        sb.append('|')
        for (x in puzzle.solution) sb.append(x)
        sb.append('|')
        for (x in v) sb.append(x)
        sb.append('|')
        sb.append(notes.joinToString(","))
        return sb.toString()
    }

    companion object {
        /** Rebuilds a game from [serialize]; null when the data is damaged. */
        fun deserialize(s: String): SudokuGame? {
            try {
                val p = s.split('|')
                if (p.size != 9 || p[0] != "v1") return null
                val diff = p[1].toInt()
                if (diff !in 0..2) return null
                fun digits(x: String): IntArray? {
                    if (x.length != 81) return null
                    return IntArray(81) { val d = x[it] - '0'; if (d !in 0..9) return null; d }
                }
                val givens = digits(p[5]) ?: return null
                val sol = digits(p[6]) ?: return null
                val vals = digits(p[7]) ?: return null
                if (!SudokuEngine.isValidSolution(sol)) return null
                for (i in 0 until 81) if (givens[i] != 0 && givens[i] != sol[i]) return null
                for (i in 0 until 81) if (givens[i] != 0 && vals[i] != givens[i]) return null
                val nt = p[8].split(',').map { it.toInt() }
                if (nt.size != 81 || nt.any { it !in 0..ALL_NOTES }) return null
                val g = SudokuGame(SudokuPuzzle(diff, givens, sol))
                System.arraycopy(vals, 0, g.v, 0, 81)
                for (i in 0 until 81) g.notes[i] = nt[i]
                g.frames = p[2].toInt().coerceAtLeast(0)
                g.mistakes = p[3].toInt().coerceAtLeast(0)
                g.hints = p[4].toInt().coerceAtLeast(0)
                return g
            } catch (e: Exception) {
                return null
            }
        }

        private const val ALL_NOTES = 0x1FF
    }
}
