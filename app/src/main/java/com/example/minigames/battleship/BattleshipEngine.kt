package com.example.minigames.battleship

import kotlin.random.Random

/** Pure Kotlin Battleship (no android.*): the fleet, placement, shooting, the computer player and save/load. */
object BsEngine {
    const val N = 10
    const val CELLS = N * N

    /** Ship lengths: 5-4-3-3-2. A ship index is its position in this array. */
    val FLEET: IntArray = intArrayOf(5, 4, 3, 3, 2)

    // results of a shot
    const val NOOP = 0
    const val MISS = 1
    const val HIT = 2
    const val SUNK = 3

    // game status
    const val PLAYING = 0
    const val WON = 1
    const val LOST = 2

    const val LEVELS = 3
}

/** One ship. [r]/[c] is its top-left cell, [horiz] = lies along a row. Not placed while [r] < 0. */
class BsShip(val len: Int) {
    var r = -1
    var c = -1
    var horiz = true

    val placed: Boolean get() = r >= 0

    /** Board index of the k-th cell of the ship. */
    fun cell(k: Int): Int = if (horiz) r * BsEngine.N + c + k else (r + k) * BsEngine.N + c
}

/** A 10x10 sea with one fleet: where the ships are, and which cells have been shot at. */
class BsBoard {
    val ships: Array<BsShip> = Array(BsEngine.FLEET.size) { BsShip(BsEngine.FLEET[it]) }

    /** Ship index on every cell (-1 = water). */
    val owner = IntArray(BsEngine.CELLS) { -1 }
    val shot = BooleanArray(BsEngine.CELLS)

    var shots = 0
        private set

    /** Ship that was sunk by the last [shoot] (valid when it returned SUNK). */
    var lastSunk = -1
        private set

    val allPlaced: Boolean get() = ships.all { it.placed }

    val sunkCount: Int get() = ships.indices.count { isSunk(it) }
    val allSunk: Boolean get() = allPlaced && sunkCount == ships.size

    /** Number of shots that hit a ship. */
    val hitCount: Int get() = (0 until BsEngine.CELLS).count { shot[it] && owner[it] >= 0 }

    /** Can ship [idx] (its own current cells count as free) lie with its top-left cell at r,c? */
    fun fits(idx: Int, r: Int, c: Int, horiz: Boolean): Boolean {
        val len = ships[idx].len
        if (r < 0 || c < 0) return false
        if (horiz) {
            if (r >= BsEngine.N || c + len > BsEngine.N) return false
        } else {
            if (c >= BsEngine.N || r + len > BsEngine.N) return false
        }
        for (k in 0 until len) {
            val i = if (horiz) r * BsEngine.N + c + k else (r + k) * BsEngine.N + c
            val o = owner[i]
            if (o >= 0 && o != idx) return false
        }
        return true
    }

    /** Like [fits], and additionally no other ship may touch the new cells, not even diagonally. */
    private fun fitsApart(idx: Int, r: Int, c: Int, horiz: Boolean): Boolean {
        if (!fits(idx, r, c, horiz)) return false
        val len = ships[idx].len
        val r1 = if (horiz) r else r + len - 1
        val c1 = if (horiz) c + len - 1 else c
        for (rr in maxOf(0, r - 1)..minOf(BsEngine.N - 1, r1 + 1)) {
            for (cc in maxOf(0, c - 1)..minOf(BsEngine.N - 1, c1 + 1)) {
                val o = owner[rr * BsEngine.N + cc]
                if (o >= 0 && o != idx) return false
            }
        }
        return true
    }

    fun lift(idx: Int) {
        val s = ships[idx]
        if (!s.placed) return
        for (k in 0 until s.len) owner[s.cell(k)] = -1
        s.r = -1
        s.c = -1
    }

    /** Puts ship [idx] on the board (moving it if it is already there). False (nothing changed) when it does not fit. */
    fun place(idx: Int, r: Int, c: Int, horiz: Boolean): Boolean {
        if (!fits(idx, r, c, horiz)) return false
        lift(idx)
        val s = ships[idx]
        s.r = r
        s.c = c
        s.horiz = horiz
        for (k in 0 until s.len) owner[s.cell(k)] = idx
        return true
    }

    /** Removes all ships (the shots stay). */
    fun clearShips() {
        for (i in ships.indices) lift(i)
    }

    /** Random fleet: ships do not touch each other when that is possible (it always is on 10x10 with this fleet). */
    fun randomize(rnd: Random) {
        while (true) {
            clearShips()
            var ok = true
            for (idx in ships.indices) {
                var done = false
                for (attempt in 0 until 400) {
                    val horiz = rnd.nextBoolean()
                    val r = rnd.nextInt(BsEngine.N)
                    val c = rnd.nextInt(BsEngine.N)
                    if (fitsApart(idx, r, c, horiz)) {
                        place(idx, r, c, horiz)
                        done = true
                        break
                    }
                }
                if (!done) {
                    ok = false
                    break
                }
            }
            if (ok) return
        }
    }

    fun hitsOn(idx: Int): Int {
        val s = ships[idx]
        if (!s.placed) return 0
        var n = 0
        for (k in 0 until s.len) if (shot[s.cell(k)]) n++
        return n
    }

    fun isSunk(idx: Int): Boolean = ships[idx].placed && hitsOn(idx) == ships[idx].len

    /** Fires at cell [i]: NOOP (already shot / outside), MISS, HIT or SUNK. */
    fun shoot(i: Int): Int {
        if (i < 0 || i >= BsEngine.CELLS || shot[i]) return BsEngine.NOOP
        shot[i] = true
        shots++
        val o = owner[i]
        if (o < 0) return BsEngine.MISS
        if (isSunk(o)) {
            lastSunk = o
            return BsEngine.SUNK
        }
        return BsEngine.HIT
    }

    /** Marks cell [i] as shot without any result (used when loading a saved game). */
    internal fun restoreShot(i: Int) {
        if (!shot[i]) {
            shot[i] = true
            shots++
        }
    }
}

/**
 * The computer's shooting. It looks only at what a real player would know about the board it shoots at: which cells
 * were shot, which of them hit a ship, and which ships are sunk (their lengths, and — like the usual "sunk" call — the cells).
 * Easy: random shots, finishes a wounded ship only now and then. Medium: checkerboard hunting, then finishes the ship
 * along its line. Hard: probability map over all positions the remaining ships could still take.
 */
object BsAi {
    private val NB = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))

    fun pick(level: Int, b: BsBoard, rnd: Random): Int {
        val free = ArrayList<Int>()
        for (i in 0 until BsEngine.CELLS) if (!b.shot[i]) free.add(i)
        if (free.isEmpty()) return -1
        // wounded: a hit on a ship that is not sunk yet
        val wounded = ArrayList<Int>()
        for (i in 0 until BsEngine.CELLS) {
            val o = b.owner[i]
            if (b.shot[i] && o >= 0 && !b.isSunk(o)) wounded.add(i)
        }
        val r = when (level) {
            0 -> easy(b, free, wounded, rnd)
            1 -> medium(b, free, wounded, rnd)
            else -> hard(b, free, wounded, rnd)
        }
        return if (r >= 0 && !b.shot[r]) r else free[rnd.nextInt(free.size)]
    }

    private fun neighbours(b: BsBoard, i: Int): ArrayList<Int> {
        val res = ArrayList<Int>(4)
        val r = i / BsEngine.N
        val c = i % BsEngine.N
        for (d in NB) {
            val rr = r + d[0]
            val cc = c + d[1]
            if (rr < 0 || cc < 0 || rr >= BsEngine.N || cc >= BsEngine.N) continue
            val j = rr * BsEngine.N + cc
            if (!b.shot[j]) res.add(j)
        }
        return res
    }

    private fun easy(b: BsBoard, free: List<Int>, wounded: List<Int>, rnd: Random): Int {
        if (wounded.isNotEmpty() && rnd.nextInt(100) < 55) {
            val cand = neighbours(b, wounded[rnd.nextInt(wounded.size)])
            if (cand.isNotEmpty()) return cand[rnd.nextInt(cand.size)]
        }
        return free[rnd.nextInt(free.size)]
    }

    private fun medium(b: BsBoard, free: List<Int>, wounded: List<Int>, rnd: Random): Int {
        if (wounded.isNotEmpty()) {
            // two wounded cells next to each other: continue along their line
            val line = ArrayList<Int>()
            for (w in wounded) {
                val r = w / BsEngine.N
                val c = w % BsEngine.N
                for (d in NB) {
                    val r2 = r + d[0]
                    val c2 = c + d[1]
                    if (r2 < 0 || c2 < 0 || r2 >= BsEngine.N || c2 >= BsEngine.N) continue
                    val j2 = r2 * BsEngine.N + c2
                    if (!(b.shot[j2] && b.owner[j2] >= 0 && !b.isSunk(b.owner[j2]))) continue
                    // w and j2 are wounded neighbours: look at the cell beyond each of them
                    val beyond = intArrayOf(r - d[0], c - d[1], r2 + d[0], c2 + d[1])
                    for (k in 0 until 2) {
                        val rr = beyond[k * 2]
                        val cc = beyond[k * 2 + 1]
                        if (rr < 0 || cc < 0 || rr >= BsEngine.N || cc >= BsEngine.N) continue
                        val j = rr * BsEngine.N + cc
                        if (!b.shot[j]) line.add(j)
                    }
                }
            }
            if (line.isNotEmpty()) return line[rnd.nextInt(line.size)]
            val cand = neighbours(b, wounded[rnd.nextInt(wounded.size)])
            if (cand.isNotEmpty()) return cand[rnd.nextInt(cand.size)]
        }
        val parity = free.filter { ((it / BsEngine.N) + (it % BsEngine.N)) % 2 == 0 }
        val pool = if (parity.isNotEmpty()) parity else free
        return pool[rnd.nextInt(pool.size)]
    }

    private fun hard(b: BsBoard, free: List<Int>, wounded: List<Int>, rnd: Random): Int {
        val score = LongArray(BsEngine.CELLS)
        val n = BsEngine.N
        val hasWounded = wounded.isNotEmpty()
        for (idx in b.ships.indices) {
            if (b.isSunk(idx)) continue
            val len = b.ships[idx].len
            for (horiz in booleanArrayOf(true, false)) {
                val maxR = if (horiz) n else n - len + 1
                val maxC = if (horiz) n - len + 1 else n
                for (r in 0 until maxR) for (c in 0 until maxC) {
                    var okPos = true
                    var h = 0
                    for (k in 0 until len) {
                        val i = if (horiz) r * n + c + k else (r + k) * n + c
                        if (b.shot[i]) {
                            val o = b.owner[i]
                            if (o >= 0 && !b.isSunk(o)) h++ else {
                                okPos = false
                                break
                            }
                        }
                    }
                    if (!okPos) continue
                    if (hasWounded && h == 0) continue
                    val w = if (hasWounded) (h * h * h * 12L) else 1L
                    for (k in 0 until len) {
                        val i = if (horiz) r * n + c + k else (r + k) * n + c
                        if (!b.shot[i]) score[i] += w
                    }
                }
            }
        }
        var best = -1L
        val ties = ArrayList<Int>()
        for (i in free) {
            val s = score[i]
            if (s > best) {
                best = s
                ties.clear()
                ties.add(i)
            } else if (s == best) ties.add(i)
        }
        if (best <= 0L || ties.isEmpty()) return free[rnd.nextInt(free.size)]
        return ties[rnd.nextInt(ties.size)]
    }
}

/**
 * A game against the computer: [me] is the player's own sea (the computer shoots at it), [foe] is the computer's sea
 * (the player shoots at it). A hit or a sunk ship lets the same side shoot again; a miss passes the turn.
 */
class BsGame(val level: Int) {
    val me = BsBoard()
    val foe = BsBoard()

    /** 0 = the player shoots, 1 = the computer shoots. */
    var turn = 0
        private set
    var status = BsEngine.PLAYING
        private set

    /** The last cell each side shot at (-1 none) and the result of the last shot of the player / the computer. */
    var lastMine = -1
        private set
    var lastFoe = -1
        private set
    var lastMineRes = BsEngine.NOOP
        private set
    var lastFoeRes = BsEngine.NOOP
        private set

    /** Starts the battle: [me] must be fully placed; the computer's fleet is random. */
    fun begin(rnd: Random): Boolean {
        if (!me.allPlaced) return false
        foe.randomize(rnd)
        turn = 0
        status = BsEngine.PLAYING
        return true
    }

    /** The player fires at cell [i] of the computer's sea. Returns the result (NOOP = not allowed). */
    fun playerShoot(i: Int): Int {
        if (status != BsEngine.PLAYING || turn != 0) return BsEngine.NOOP
        val res = foe.shoot(i)
        if (res == BsEngine.NOOP) return res
        lastMine = i
        lastMineRes = res
        if (foe.allSunk) status = BsEngine.WON
        else if (res == BsEngine.MISS) turn = 1
        return res
    }

    /** Cell the computer would shoot at now (does not change anything). */
    fun aiChoice(rnd: Random): Int = BsAi.pick(level, me, rnd)

    /** The computer fires at cell [i] of the player's sea. */
    fun aiShoot(i: Int): Int {
        if (status != BsEngine.PLAYING || turn != 1) return BsEngine.NOOP
        val res = me.shoot(i)
        if (res == BsEngine.NOOP) return res
        lastFoe = i
        lastFoeRes = res
        if (me.allSunk) status = BsEngine.LOST
        else if (res == BsEngine.MISS) turn = 0
        return res
    }

    // ------------------------------- save / load ----------------------------

    /** `BS1;level;turn;lastMine;lastFoe;myShips;foeShips;shotsOnFoe;shotsOnMe` (ships: row, col, 0/1 per ship). */
    fun serialize(): String {
        val sb = StringBuilder("BS1;")
        sb.append(level).append(';').append(turn).append(';').append(lastMine).append(';').append(lastFoe).append(';')
        appendShips(sb, me)
        sb.append(';')
        appendShips(sb, foe)
        sb.append(';')
        for (i in 0 until BsEngine.CELLS) sb.append(if (foe.shot[i]) '1' else '0')
        sb.append(';')
        for (i in 0 until BsEngine.CELLS) sb.append(if (me.shot[i]) '1' else '0')
        return sb.toString()
    }

    private fun appendShips(sb: StringBuilder, b: BsBoard) {
        for (s in b.ships) sb.append(s.r).append(s.c).append(if (s.horiz) '1' else '0')
    }

    companion object {
        private fun loadShips(b: BsBoard, s: String): Boolean {
            if (s.length != BsEngine.FLEET.size * 3) return false
            for (idx in b.ships.indices) {
                val r = s[idx * 3] - '0'
                val c = s[idx * 3 + 1] - '0'
                val h = s[idx * 3 + 2]
                if (r !in 0..9 || c !in 0..9 || (h != '0' && h != '1')) return false
                if (!b.place(idx, r, c, h == '1')) return false
            }
            return true
        }

        private fun loadShots(b: BsBoard, s: String): Boolean {
            if (s.length != BsEngine.CELLS) return false
            for (i in 0 until BsEngine.CELLS) {
                when (s[i]) {
                    '1' -> b.restoreShot(i)
                    '0' -> {}
                    else -> return false
                }
            }
            return true
        }

        /** Restores a running game, or null for anything that is not a consistent, unfinished game. */
        fun deserialize(s: String): BsGame? {
            try {
                val p = s.split(';')
                if (p.size != 9 || p[0] != "BS1") return null
                val level = p[1].toInt()
                val turn = p[2].toInt()
                val lastMine = p[3].toInt()
                val lastFoe = p[4].toInt()
                if (level !in 0 until BsEngine.LEVELS || turn !in 0..1) return null
                if (lastMine !in -1 until BsEngine.CELLS || lastFoe !in -1 until BsEngine.CELLS) return null
                val g = BsGame(level)
                if (!loadShips(g.me, p[5]) || !loadShips(g.foe, p[6])) return null
                if (!loadShots(g.foe, p[7]) || !loadShots(g.me, p[8])) return null
                if (g.foe.allSunk || g.me.allSunk) return null
                if (lastMine >= 0 && !g.foe.shot[lastMine]) return null
                if (lastFoe >= 0 && !g.me.shot[lastFoe]) return null
                g.turn = turn
                g.lastMine = lastMine
                g.lastFoe = lastFoe
                g.lastMineRes = if (lastMine < 0) BsEngine.NOOP else resultAt(g.foe, lastMine)
                g.lastFoeRes = if (lastFoe < 0) BsEngine.NOOP else resultAt(g.me, lastFoe)
                return g
            } catch (e: Exception) {
                return null
            }
        }

        /** What a shot at an already shot cell was: MISS, HIT or (ship fully shot) SUNK. */
        private fun resultAt(b: BsBoard, i: Int): Int {
            val o = b.owner[i]
            if (o < 0) return BsEngine.MISS
            return if (b.isSunk(o)) BsEngine.SUNK else BsEngine.HIT
        }
    }
}
