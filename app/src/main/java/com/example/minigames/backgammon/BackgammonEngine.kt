package com.example.minigames.backgammon

import kotlin.math.max
import kotlin.random.Random

/*
 * Pure-Kotlin engine of short backgammon (24 points, bar, bearing off, doubles = 4 moves,
 * "play as many dice as possible" rule, no gammon / doubling cube).
 *
 * Board: pt[i] (i = 0..23) holds checkers of side 0 as a positive number and of side 1 as a negative one.
 * Side 0 (white) moves towards lower indices, its home board is 0..5 and it bears off below index 0;
 * it enters from the bar on index 24 - die. Side 1 (black) moves towards higher indices, its home is 18..23,
 * it bears off above index 23 and enters on index die - 1.
 *
 * A move is an Int: from (0..23 or BAR), to (0..23 or OFF) and the die value, see [Bg].
 */
object Bg {
    const val BAR = 24
    const val OFF = -1

    fun mv(from: Int, to: Int, die: Int): Int = from or ((to + 1) shl 8) or (die shl 16)
    fun from(m: Int): Int = m and 255
    fun to(m: Int): Int = ((m shr 8) and 255) - 1
    fun die(m: Int): Int = (m shr 16) and 255

    /** Pips a checker of [side] on index [i] still has to travel. */
    fun dist(side: Int, i: Int): Int = if (side == 0) i + 1 else 24 - i

    fun entryIndex(side: Int, die: Int): Int = if (side == 0) 24 - die else die - 1
}

class BgBoard {
    val pt = IntArray(24)
    val bar = IntArray(2)
    val off = IntArray(2)

    fun copy(): BgBoard {
        val b = BgBoard()
        b.copyFrom(this)
        return b
    }

    fun copyFrom(o: BgBoard) {
        System.arraycopy(o.pt, 0, pt, 0, 24)
        bar[0] = o.bar[0]; bar[1] = o.bar[1]
        off[0] = o.off[0]; off[1] = o.off[1]
    }

    /** Checkers of [side] on [i]; a negative number means checkers of the opponent. */
    fun own(side: Int, i: Int): Int = if (side == 0) pt[i] else -pt[i]

    fun allHome(side: Int): Boolean {
        if (bar[side] > 0) return false
        if (side == 0) {
            for (i in 6..23) if (pt[i] > 0) return false
        } else {
            for (i in 0..17) if (pt[i] < 0) return false
        }
        return true
    }

    fun pips(side: Int): Int {
        var p = bar[side] * 25
        for (i in 0 until 24) {
            val c = own(side, i)
            if (c > 0) p += c * Bg.dist(side, i)
        }
        return p
    }

    fun checkersOf(side: Int): Int {
        var n = bar[side] + off[side]
        for (i in 0 until 24) {
            val c = own(side, i)
            if (c > 0) n += c
        }
        return n
    }

    fun hash(): Long {
        var h = 1125899906842597L
        for (i in 0 until 24) h = h * 1000003L + (pt[i] + 16)
        h = h * 1000003L + bar[0]
        h = h * 1000003L + bar[1] + 7
        h = h * 1000003L + off[0] + 11
        h = h * 1000003L + off[1] + 13
        return h
    }

    private fun noneFarther(side: Int, i: Int): Boolean {
        if (side == 0) {
            for (j in i + 1..5) if (pt[j] > 0) return false
        } else {
            for (j in 18 until i) if (pt[j] < 0) return false
        }
        return true
    }

    /** Appends the legal single moves with die [d] to [out] (starting at [n0]); returns the new length. */
    fun movesForDie(side: Int, d: Int, out: IntArray, n0: Int): Int {
        var n = n0
        if (bar[side] > 0) {
            val to = Bg.entryIndex(side, d)
            if (own(side, to) > -2) out[n++] = Bg.mv(Bg.BAR, to, d)
            return n
        }
        val dir = if (side == 0) -1 else 1
        val home = allHome(side)
        for (i in 0 until 24) {
            if (own(side, i) <= 0) continue
            val to = i + dir * d
            if (to in 0..23) {
                if (own(side, to) > -2) out[n++] = Bg.mv(i, to, d)
            } else if (home) {
                val dd = Bg.dist(side, i)
                if (dd == d || (dd < d && noneFarther(side, i))) out[n++] = Bg.mv(i, Bg.OFF, d)
            }
        }
        return n
    }

    /** Plays [m] for [side]; returns true when a blot of the opponent was hit. */
    fun apply(side: Int, m: Int): Boolean {
        val from = Bg.from(m)
        val to = Bg.to(m)
        val sgn = if (side == 0) 1 else -1
        if (from == Bg.BAR) bar[side]-- else pt[from] -= sgn
        var hit = false
        if (to == Bg.OFF) {
            off[side]++
        } else {
            if (own(side, to) == -1) {
                pt[to] = 0
                bar[1 - side]++
                hit = true
            }
            pt[to] += sgn
        }
        return hit
    }

    fun undo(side: Int, m: Int, hit: Boolean) {
        val from = Bg.from(m)
        val to = Bg.to(m)
        val sgn = if (side == 0) 1 else -1
        if (to == Bg.OFF) {
            off[side]--
        } else {
            pt[to] -= sgn
            if (hit) {
                pt[to] = -sgn
                bar[1 - side]--
            }
        }
        if (from == Bg.BAR) bar[side]++ else pt[from] += sgn
    }

    /** Standard start position. */
    companion object {
        fun initial(): BgBoard {
            val b = BgBoard()
            b.pt[23] = 2; b.pt[12] = 5; b.pt[7] = 3; b.pt[5] = 5
            b.pt[0] = -2; b.pt[11] = -5; b.pt[16] = -3; b.pt[18] = -5
            return b
        }
    }
}

/**
 * Searches the dice of one turn on a (temporarily modified) board: the maximal number of playable dice,
 * the legal first moves and every complete turn. The board is always restored.
 */
class BgTurnSearch(private val b: BgBoard, private val side: Int) {
    private val cnt = IntArray(7)
    private val bufs = Array(6) { IntArray(64) }
    private val memo = HashMap<Long, Int>()
    private var visited = HashSet<Long>()

    fun setDice(d1: Int, d2: Int) {
        java.util.Arrays.fill(cnt, 0)
        if (d1 == d2) cnt[d1] = 4 else {
            cnt[d1] = 1
            cnt[d2] = 1
        }
        memo.clear()
    }

    /** Remaining dice as a list of values (unused dice). */
    fun setRemaining(dice: IntArray) {
        java.util.Arrays.fill(cnt, 0)
        for (d in dice) cnt[d]++
        memo.clear()
    }

    private fun code(): Long {
        var c = 0L
        for (v in 1..6) c = c * 5 + cnt[v]
        return c
    }

    private fun total(): Int {
        var s = 0
        for (v in 1..6) s += cnt[v]
        return s
    }

    private fun distinct(): Int {
        var s = 0
        for (v in 1..6) if (cnt[v] > 0) s++
        return s
    }

    private fun maxPlay(depth: Int): Int {
        val tot = total()
        if (tot == 0) return 0
        val key = b.hash() * 1000003L + code() * 2 + side
        val got = memo[key]
        if (got != null) return got
        var best = 0
        val buf = bufs[depth]
        outer@ for (v in 1..6) {
            if (cnt[v] == 0) continue
            val n = b.movesForDie(side, v, buf, 0)
            for (k in 0 until n) {
                val m = buf[k]
                val hit = b.apply(side, m)
                cnt[v]--
                val r = 1 + maxPlay(depth + 1)
                cnt[v]++
                b.undo(side, m, hit)
                if (r > best) best = r
                if (best == tot) break@outer
            }
        }
        memo[key] = best
        return best
    }

    /** Most dice that can be played from here. */
    fun maxDice(): Int = maxPlay(0)

    /** Legal single moves right now, honouring the "use as many dice as possible / the larger die" rules. */
    fun legalMoves(): IntArray {
        val total = maxPlay(0)
        if (total == 0) return IntArray(0)
        val res = ArrayList<Int>()
        val buf = IntArray(64)
        for (v in 1..6) {
            if (cnt[v] == 0) continue
            val n = b.movesForDie(side, v, buf, 0)
            for (k in 0 until n) {
                val m = buf[k]
                val hit = b.apply(side, m)
                cnt[v]--
                val r = 1 + maxPlay(1)
                cnt[v]++
                b.undo(side, m, hit)
                if (r == total) res.add(m)
            }
        }
        if (total == 1 && distinct() == 2) {
            var big = 0
            for (v in 1..6) if (cnt[v] > 0) big = v
            if (res.any { Bg.die(it) == big }) res.removeAll { Bg.die(it) != big }
        }
        return res.toIntArray()
    }

    private class Leaf(val seq: IntArray, val h: Long)

    private var leaves = ArrayList<Leaf>()
    private val seq = IntArray(4)

    private fun dfs(depth: Int) {
        val key = b.hash() * 1000003L + code() * 2 + side
        if (!visited.add(key)) return
        var any = false
        val buf = bufs[depth]
        for (v in 1..6) {
            if (cnt[v] == 0) continue
            val n = b.movesForDie(side, v, buf, 0)
            for (k in 0 until n) {
                any = true
                val m = buf[k]
                val hit = b.apply(side, m)
                cnt[v]--
                seq[depth] = m
                dfs(depth + 1)
                cnt[v]++
                b.undo(side, m, hit)
            }
        }
        if (!any) leaves.add(Leaf(seq.copyOf(depth), b.hash()))
    }

    /**
     * Every distinct complete turn (a sequence of moves) with the maximal number of dice; one empty
     * sequence when nothing can be played. Different move orders reaching the same position are merged.
     */
    fun turns(): List<IntArray> {
        visited = HashSet()
        leaves = ArrayList()
        var big = 0
        for (v in 1..6) if (cnt[v] > 0) big = v
        val dist = distinct()
        dfs(0)
        var maxD = 0
        for (l in leaves) if (l.seq.size > maxD) maxD = l.seq.size
        if (maxD == 0) return listOf(IntArray(0))
        var cand = leaves.filter { it.seq.size == maxD }
        if (maxD == 1 && dist == 2 && cand.any { Bg.die(it.seq[0]) == big }) {
            cand = cand.filter { Bg.die(it.seq[0]) == big }
        }
        val seen = HashSet<Long>()
        val res = ArrayList<IntArray>()
        for (l in cand) if (seen.add(l.h)) res.add(l.seq)
        return res
    }
}

/** One game: board, side to move, dice of the turn and the steps already played in this turn (for undo). */
class BgGame {
    val b = BgBoard.initial()
    var turn = 0
    val dice = IntArray(4)
    var nDice = 0
    var used = 0

    private val hist = ArrayList<Int>()

    val rolled: Boolean get() = nDice > 0
    val stepsPlayed: Int get() = hist.size

    fun copy(): BgGame {
        val g = BgGame()
        g.b.copyFrom(b)
        g.turn = turn
        System.arraycopy(dice, 0, g.dice, 0, 4)
        g.nDice = nDice
        g.used = used
        g.hist.addAll(hist)
        return g
    }

    fun roll(d1: Int, d2: Int) {
        require(d1 in 1..6 && d2 in 1..6)
        dice[0] = d1
        dice[1] = d2
        if (d1 == d2) {
            dice[2] = d1
            dice[3] = d1
            nDice = 4
        } else {
            nDice = 2
        }
        used = 0
        hist.clear()
    }

    fun roll(rnd: Random) = roll(rnd.nextInt(6) + 1, rnd.nextInt(6) + 1)

    fun remainingDice(): IntArray {
        val r = ArrayList<Int>()
        for (i in 0 until nDice) if ((used shr i) and 1 == 0) r.add(dice[i])
        return r.toIntArray()
    }

    fun allUsed(): Boolean = nDice > 0 && used == (1 shl nDice) - 1

    private fun search(): BgTurnSearch {
        val s = BgTurnSearch(b, turn)
        s.setRemaining(remainingDice())
        return s
    }

    /** Legal single moves now (empty when not rolled / nothing playable). */
    fun legalMoves(): IntArray = if (nDice == 0 || allUsed()) IntArray(0) else search().legalMoves()

    /** All complete turns with the remaining dice (one empty sequence = must pass). */
    fun allTurns(): List<IntArray> = if (nDice == 0 || allUsed()) listOf(IntArray(0)) else search().turns()

    fun turnFinished(): Boolean = nDice > 0 && (allUsed() || legalMoves().isEmpty())

    /** Plays a legal move [m] (see [legalMoves]); returns true when it hit a blot. */
    fun play(m: Int): Boolean {
        var idx = -1
        for (i in 0 until nDice) if ((used shr i) and 1 == 0 && dice[i] == Bg.die(m)) {
            idx = i
            break
        }
        require(idx >= 0) { "die not available" }
        val hit = b.apply(turn, m)
        used = used or (1 shl idx)
        hist.add(m or ((if (hit) 1 else 0) shl 24) or (idx shl 25))
        return hit
    }

    fun canUndoStep(): Boolean = hist.isNotEmpty()

    fun undoStep(): Boolean {
        if (hist.isEmpty()) return false
        val h = hist.removeAt(hist.size - 1)
        val m = h and 0xFFFFFF
        val hit = (h shr 24) and 1 == 1
        val idx = (h shr 25) and 7
        b.undo(turn, m, hit)
        used = used and (1 shl idx).inv()
        return true
    }

    /** Moves played in this turn so far. */
    fun turnMoves(): IntArray = IntArray(hist.size) { hist[it] and 0xFFFFFF }

    fun endTurn() {
        turn = 1 - turn
        nDice = 0
        used = 0
        hist.clear()
    }

    /** 0 / 1 = that side has borne off all its checkers, -1 = running. */
    fun winner(): Int = if (b.off[0] == 15) 0 else if (b.off[1] == 15) 1 else -1

    // ------------------------------ persistence ------------------------------

    fun serialize(): String {
        val sb = StringBuilder()
        for (i in 0 until 24) sb.append(b.pt[i]).append(',')
        sb.append('|').append(b.bar[0]).append(',').append(b.bar[1])
        sb.append('|').append(b.off[0]).append(',').append(b.off[1])
        sb.append('|').append(turn).append('|').append(nDice).append('|').append(used)
        sb.append('|')
        for (i in 0 until 4) sb.append(dice[i]).append(',')
        return sb.toString()
    }

    /** Loads [s]; false when it is malformed or inconsistent (the game is then unusable). */
    fun deserialize(s: String): Boolean {
        try {
            val p = s.split('|')
            if (p.size < 7) return false
            val pts = p[0].split(',').filter { it.isNotEmpty() }.map { it.toInt() }
            if (pts.size != 24) return false
            val bar = p[1].split(',').map { it.toInt() }
            val off = p[2].split(',').map { it.toInt() }
            val tn = p[3].toInt()
            val nd = p[4].toInt()
            val us = p[5].toInt()
            val ds = p[6].split(',').filter { it.isNotEmpty() }.map { it.toInt() }
            if (bar.size != 2 || off.size != 2 || ds.size != 4 || tn !in 0..1) return false
            if (nd != 0 && nd != 2 && nd != 4) return false
            if (us < 0 || us >= (1 shl 4)) return false
            if (nd > 0 && ds.take(nd).any { it !in 1..6 }) return false
            if (nd == 4 && !(ds[0] == ds[1] && ds[1] == ds[2] && ds[2] == ds[3])) return false
            if (nd == 2 && ds[0] == ds[1]) return false
            val nb = BgBoard()
            for (i in 0 until 24) nb.pt[i] = pts[i]
            nb.bar[0] = bar[0]; nb.bar[1] = bar[1]
            nb.off[0] = off[0]; nb.off[1] = off[1]
            if (nb.bar[0] < 0 || nb.bar[1] < 0 || nb.off[0] !in 0..15 || nb.off[1] !in 0..15) return false
            if (nb.checkersOf(0) != 15 || nb.checkersOf(1) != 15) return false
            for (i in 0 until 24) {
                if (pts[i] !in -15..15) return false
            }
            b.copyFrom(nb)
            turn = tn
            nDice = nd
            used = us and ((1 shl nd) - 1)
            for (i in 0 until 4) dice[i] = ds[i]
            hist.clear()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

/** Position evaluation (in pips, from the point of view of one side). */
object BgEval {
    private val hitTab = FloatArray(4096)

    init {
        for (mask in 0 until 4096) {
            var hits = 0
            for (a in 1..6) for (c in 1..6) {
                var ok = false
                if (a == c) {
                    for (k in 1..4) {
                        val d = a * k
                        if (d <= 12 && (mask shr (d - 1)) and 1 == 1) ok = true
                    }
                } else {
                    if ((mask shr (a - 1)) and 1 == 1) ok = true
                    if ((mask shr (c - 1)) and 1 == 1) ok = true
                    if (a + c <= 12 && (mask shr (a + c - 1)) and 1 == 1) ok = true
                }
                if (ok) hits++
            }
            hitTab[mask] = hits / 36f
        }
    }

    const val WIN = 1_000_000.0

    private fun contact(b: BgBoard): Boolean {
        if (b.bar[0] > 0 || b.bar[1] > 0) return true
        var maxW = -1
        var minB = 99
        for (i in 0 until 24) {
            if (b.pt[i] > 0 && i > maxW) maxW = i
            if (b.pt[i] < 0 && i < minB) minB = i
        }
        return maxW > minB
    }

    /** Chance that the opponent hits a blot of [s] on [i] with one roll (blocked points ignored). */
    private fun shotProb(b: BgBoard, s: Int, i: Int): Float {
        val o = 1 - s
        var mask = 0
        if (b.bar[o] > 0) {
            val dd = if (o == 0) 24 - i else i + 1
            if (dd in 1..12) mask = mask or (1 shl (dd - 1))
            return hitTab[mask]
        }
        for (q in 0 until 24) {
            if (b.own(o, q) <= 0) continue
            val dd = if (o == 0) q - i else i - q
            if (dd in 1..12) mask = mask or (1 shl (dd - 1))
        }
        return hitTab[mask]
    }

    private fun structure(b: BgBoard, s: Int, toMove: Int): Double {
        val blotK = if (s == toMove) 0.35 else 1.0
        val o = 1 - s
        var sc = 0.0
        if (b.bar[s] > 0) {
            var blocked = 0
            val lo = if (s == 0) 18 else 0
            for (i in lo until lo + 6) if (b.own(o, i) >= 2) blocked++
            val f = blocked / 6.0
            sc -= b.bar[s] * (10.0 + 2.0 * blocked) + f * f * 25.0
        }
        var run = 0
        var bestRun = 0
        for (d in 1..24) {
            val i = if (s == 0) d - 1 else 24 - d
            val c = b.own(s, i)
            if (c >= 2) {
                sc += when {
                    d <= 6 -> 3.0
                    d <= 12 -> 3.5
                    d <= 18 -> 2.0
                    else -> 2.5
                }
                if (c > 3) sc -= (c - 3) * 1.2
                run++
                if (run > bestRun) bestRun = run
            } else {
                run = 0
                if (c == 1) {
                    val p = shotProb(b, s, i)
                    if (p > 0f) sc -= blotK * p * ((25 - d) + 6)
                }
            }
        }
        if (bestRun >= 3) sc += 2.0 * (bestRun - 2) * (bestRun - 2)
        return sc
    }

    /** Value for [me] of position [b] where [toMove] is about to roll. */
    fun eval(b: BgBoard, me: Int, toMove: Int): Double {
        val o = 1 - me
        if (b.off[me] == 15) return WIN
        if (b.off[o] == 15) return -WIN
        var e = (b.pips(o) - b.pips(me)).toDouble()
        e += (b.off[me] - b.off[o]) * 0.5
        if (contact(b)) e += structure(b, me, toMove) - structure(b, o, toMove)
        return e
    }
}

/** Computer player. Levels: 0 easy (noisy 1-ply), 1 medium (1-ply), 2 hard (2-ply on the best candidates, time limited). */
object BgAi {

    /** Chooses the whole rest of the turn of the side to move in [g] (not modified). */
    fun choose(g: BgGame, level: Int, timeMs: Long, rnd: Random = Random.Default): IntArray {
        val side = g.turn
        val search = BgTurnSearch(g.b.copy(), side)
        search.setRemaining(g.remainingDice())
        val turns = search.turns()
        if (turns.size <= 1) return if (turns.isEmpty()) IntArray(0) else turns[0]
        val deadline = System.currentTimeMillis() + timeMs

        val n = turns.size
        val score = DoubleArray(n)
        val tmp = BgBoard()
        for (i in 0 until n) {
            tmp.copyFrom(g.b)
            for (m in turns[i]) tmp.apply(side, m)
            score[i] = BgEval.eval(tmp, side, 1 - side)
        }
        when (level) {
            0 -> {
                var best = 0
                var bv = Double.NEGATIVE_INFINITY
                for (i in 0 until n) {
                    val v = score[i] + rnd.nextDouble(-45.0, 45.0)
                    if (v > bv) { bv = v; best = i }
                }
                return turns[best]
            }
            1 -> {
                var best = 0
                var bv = Double.NEGATIVE_INFINITY
                for (i in 0 until n) {
                    val v = score[i] + rnd.nextDouble(-7.0, 7.0)
                    if (v > bv) { bv = v; best = i }
                }
                return turns[best]
            }
        }
        // hard: 2-ply over the best candidates
        val order = (0 until n).sortedByDescending { score[it] }
        var bestIdx = order[0]
        var bestVal = Double.NEGATIVE_INFINITY
        val k = minOf(n, 24)
        for (r in 0 until k) {
            val i = order[r]
            if (r > 0 && System.currentTimeMillis() > deadline) break
            tmp.copyFrom(g.b)
            for (m in turns[i]) tmp.apply(side, m)
            val v = if (score[i] >= BgEval.WIN) BgEval.WIN else twoPly(tmp, side, deadline, r == 0)
            if (v > bestVal) { bestVal = v; bestIdx = i }
        }
        return turns[bestIdx]
    }

    /** Average over the opponent's 21 rolls of (minus) the opponent's best reply by 1-ply evaluation. */
    private fun twoPly(after: BgBoard, me: Int, deadline: Long, mustFinish: Boolean): Double {
        val o = 1 - me
        var sum = 0.0
        var wsum = 0.0
        val tmp = BgBoard()
        for (a in 1..6) for (c in a..6) {
            val w = if (a == c) 1.0 else 2.0
            if (!mustFinish && System.currentTimeMillis() > deadline) {
                // out of time: finish the average with what we have so far
                return if (wsum > 0) sum / wsum else BgEval.eval(after, me, o)
            }
            val ts = BgTurnSearch(after.copy(), o)
            ts.setDice(a, c)
            var best = Double.NEGATIVE_INFINITY
            for (t in ts.turns()) {
                tmp.copyFrom(after)
                for (m in t) tmp.apply(o, m)
                val v = BgEval.eval(tmp, o, me)
                if (v > best) best = v
            }
            sum += -best * w
            wsum += w
        }
        return sum / max(wsum, 1.0)
    }
}
