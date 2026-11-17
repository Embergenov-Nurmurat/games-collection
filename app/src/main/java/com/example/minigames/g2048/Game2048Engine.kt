package com.example.minigames.g2048

import java.util.Random

/** One tile that slides during a move: from cell [from] to cell [to]; [value] is its value BEFORE merging. */
class G48Move(val from: Int, val to: Int, val value: Int)

/**
 * What one successful move did (for animation and sounds).
 * [merged] - cells where two tiles joined; [gained] - score added by this move;
 * [spawnIdx]/[spawnVal] - the new tile (-1 when the board had no free cell, which cannot happen after a move);
 * [biggest] - the biggest tile created by merging in this move (0 = no merge).
 */
class G48Step(
    val moves: List<G48Move>,
    val merged: IntArray,
    val gained: Int,
    val spawnIdx: Int,
    val spawnVal: Int,
    val biggest: Int
)

/**
 * 2048 on a 4x4 board (pure Kotlin, no Android classes).
 * Cell index = row * 4 + col; value 0 = empty, otherwise the tile value (2, 4, 8, ...).
 * Directions: [LEFT], [UP], [RIGHT], [DOWN].
 */
class Game2048(private val rng: Random = Random()) {

    companion object {
        const val N = 4
        const val CELLS = 16
        const val LEFT = 0
        const val UP = 1
        const val RIGHT = 2
        const val DOWN = 3
        const val MAX_UNDO = 30
        const val MAX_TILE = 131072
        const val SAVE_VERSION = "1"

        /** Cell of the [pos]-th square of line [line] when sliding towards [dir] (pos 0 = the edge tiles slide to). */
        fun lineIndex(dir: Int, line: Int, pos: Int): Int = when (dir) {
            LEFT -> line * N + pos
            RIGHT -> line * N + (N - 1 - pos)
            UP -> pos * N + line
            else -> (N - 1 - pos) * N + line
        }

        private fun validTile(v: Int): Boolean = v == 0 || (v in 2..MAX_TILE && (v and (v - 1)) == 0)

        /** Restores a game written by [serialize]; null when the text is damaged. The undo history is empty. */
        fun deserialize(s: String?, rng: Random = Random()): Game2048? {
            if (s == null) return null
            try {
                val p = s.split(";")
                if (p.size != 6 || p[0] != SAVE_VERSION) return null
                val score = p[1].toInt()
                val moves = p[2].toInt()
                val keep = p[3]
                val rec = p[4]
                if (score < 0 || moves < 0) return null
                if ((keep != "0" && keep != "1") || (rec != "0" && rec != "1")) return null
                val vals = p[5].split(",")
                if (vals.size != CELLS) return null
                val g = Game2048(rng)
                var any = false
                for (i in 0 until CELLS) {
                    val v = vals[i].toInt()
                    if (!validTile(v)) return null
                    g.cells[i] = v
                    if (v != 0) any = true
                }
                if (!any) return null
                g.score = score
                g.moves = moves
                g.keepGoing = keep == "1"
                g.recorded = rec == "1"
                return g
            } catch (e: NumberFormatException) {
                return null
            }
        }
    }

    private class Snapshot(val cells: IntArray, val score: Int, val moves: Int)

    val cells = IntArray(CELLS)

    var score = 0
        private set

    var moves = 0
        private set

    /** The player chose "keep playing" after reaching 2048 (not rolled back by undo). */
    var keepGoing = false

    /** The result of this game (win / loss) has already been counted in the statistics (not rolled back by undo). */
    var recorded = false

    private val history = ArrayList<Snapshot>()

    /** Starts a new game: empty board with two tiles. */
    fun reset() {
        for (i in 0 until CELLS) cells[i] = 0
        score = 0
        moves = 0
        keepGoing = false
        recorded = false
        history.clear()
        spawn()
        spawn()
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun maxTile(): Int {
        var m = 0
        for (v in cells) if (v > m) m = v
        return m
    }

    fun emptyCount(): Int {
        var n = 0
        for (v in cells) if (v == 0) n++
        return n
    }

    /** True when at least one move changes the board. */
    fun canMove(): Boolean {
        for (r in 0 until N) {
            for (c in 0 until N) {
                val v = cells[r * N + c]
                if (v == 0) return true
                if (c + 1 < N && cells[r * N + c + 1] == v) return true
                if (r + 1 < N && cells[(r + 1) * N + c] == v) return true
            }
        }
        return false
    }

    fun isOver(): Boolean = !canMove()

    /** Puts a new tile (2 with 90 %, 4 with 10 %) into a random free cell; returns the cell or -1. */
    private fun spawn(): Int {
        val free = emptyCount()
        if (free == 0) return -1
        var k = rng.nextInt(free)
        for (i in 0 until CELLS) {
            if (cells[i] == 0) {
                if (k == 0) {
                    cells[i] = if (rng.nextInt(10) == 0) 4 else 2
                    return i
                }
                k--
            }
        }
        return -1
    }

    /** Slides every tile towards [dir]. Returns null (and changes nothing) when no tile can move. */
    fun move(dir: Int): G48Step? {
        val next = IntArray(CELLS)
        val list = ArrayList<G48Move>()
        val mergedCells = ArrayList<Int>()
        var gained = 0
        var biggest = 0
        var changed = false
        for (line in 0 until N) {
            var target = 0              // next free position of this line
            var lastPos = -1            // position of the last placed tile
            var lastVal = 0
            var lastOpen = false        // the last placed tile may still merge
            for (pos in 0 until N) {
                val from = lineIndex(dir, line, pos)
                val v = cells[from]
                if (v == 0) continue
                if (lastOpen && lastVal == v) {
                    val to = lineIndex(dir, line, lastPos)
                    next[to] = v * 2
                    gained += v * 2
                    if (v * 2 > biggest) biggest = v * 2
                    mergedCells.add(to)
                    list.add(G48Move(from, to, v))
                    lastOpen = false
                    changed = true
                } else {
                    val to = lineIndex(dir, line, target)
                    next[to] = v
                    list.add(G48Move(from, to, v))
                    if (to != from) changed = true
                    lastPos = target
                    lastVal = v
                    lastOpen = true
                    target++
                }
            }
        }
        if (!changed) return null

        history.add(Snapshot(cells.copyOf(), score, moves))
        if (history.size > MAX_UNDO) history.removeAt(0)
        for (i in 0 until CELLS) cells[i] = next[i]
        score += gained
        moves++
        val idx = spawn()
        val sv = if (idx >= 0) cells[idx] else 0
        return G48Step(list, mergedCells.toIntArray(), gained, idx, sv, biggest)
    }

    /** Takes back the last move (score and move counter too). */
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val s = history.removeAt(history.size - 1)
        for (i in 0 until CELLS) cells[i] = s.cells[i]
        score = s.score
        moves = s.moves
        return true
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append(SAVE_VERSION).append(';').append(score).append(';').append(moves).append(';')
        sb.append(if (keepGoing) '1' else '0').append(';')
        sb.append(if (recorded) '1' else '0').append(';')
        for (i in 0 until CELLS) {
            if (i > 0) sb.append(',')
            sb.append(cells[i])
        }
        return sb.toString()
    }
}
