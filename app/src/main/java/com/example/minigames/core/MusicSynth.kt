package com.example.minigames.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Background music without any audio asset (pure Kotlin, no android classes, testable on the JVM).
 *
 * A calm 16-bar loop in C major / A minor (C - Am - F - G, 92 BPM): a soft pad, a bass line, a plucked arpeggio
 * from the second round on, a pentatonic melody and a quiet shaker from the third round on. Every note is added
 * into a ring buffer (the tail of a note that ends after the last sample wraps to the beginning), so the loop is
 * seamless by construction. The result is 16-bit mono PCM at [RATE] Hz.
 */
object MusicSynth {
    const val RATE = 22050
    const val BPM = 92
    const val BARS = 16
    private const val BEATS_PER_BAR = 4
    private const val PEAK = 0.55f

    private val beatLen: Double get() = RATE * 60.0 / BPM
    val loopSamples: Int get() = (beatLen * BEATS_PER_BAR * BARS).roundToInt()

    /** Seconds of one loop. */
    val loopSeconds: Double get() = loopSamples.toDouble() / RATE

    // chord of each bar of a round: C, Am, F, G
    private val TRIADS = arrayOf(
        intArrayOf(60, 64, 67),
        intArrayOf(57, 60, 64),
        intArrayOf(57, 60, 65),
        intArrayOf(55, 59, 62)
    )
    private val ROOTS = intArrayOf(48, 45, 41, 43)

    /** (beat in the bar, length in beats, midi note) of the melody, per bar of a round; two variants. */
    private val MELODY = arrayOf(
        arrayOf(
            arrayOf(doubleArrayOf(0.0, 1.5, 76.0), doubleArrayOf(1.5, 0.5, 74.0), doubleArrayOf(2.0, 1.0, 72.0), doubleArrayOf(3.0, 1.0, 74.0)),
            arrayOf(doubleArrayOf(0.0, 1.0, 72.0), doubleArrayOf(1.0, 1.0, 76.0), doubleArrayOf(2.0, 2.0, 81.0)),
            arrayOf(doubleArrayOf(0.0, 1.5, 81.0), doubleArrayOf(1.5, 0.5, 79.0), doubleArrayOf(2.0, 1.0, 76.0), doubleArrayOf(3.0, 1.0, 72.0)),
            arrayOf(doubleArrayOf(0.0, 1.0, 74.0), doubleArrayOf(1.0, 1.0, 79.0), doubleArrayOf(2.0, 2.0, 74.0))
        ),
        arrayOf(
            arrayOf(doubleArrayOf(0.0, 0.5, 79.0), doubleArrayOf(0.5, 0.5, 76.0), doubleArrayOf(1.0, 1.0, 79.0), doubleArrayOf(2.0, 1.0, 84.0), doubleArrayOf(3.0, 1.0, 81.0)),
            arrayOf(doubleArrayOf(0.0, 1.0, 81.0), doubleArrayOf(1.0, 1.0, 79.0), doubleArrayOf(2.0, 1.0, 76.0), doubleArrayOf(3.0, 1.0, 72.0)),
            arrayOf(doubleArrayOf(0.0, 1.0, 77.0), doubleArrayOf(1.0, 0.5, 81.0), doubleArrayOf(1.5, 0.5, 84.0), doubleArrayOf(2.0, 2.0, 81.0)),
            arrayOf(doubleArrayOf(0.0, 1.0, 79.0), doubleArrayOf(1.0, 1.0, 74.0), doubleArrayOf(2.0, 1.0, 83.0), doubleArrayOf(3.0, 1.0, 79.0))
        )
    )

    private fun freq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** The whole loop as 16-bit mono PCM. */
    fun render(): ShortArray {
        val n = loopSamples
        val buf = FloatArray(n)
        val barLen = beatLen * BEATS_PER_BAR
        val rnd = Random(7)

        for (bar in 0 until BARS) {
            val round = bar / 4
            val k = bar % 4
            val t0 = bar * barLen
            val tri = TRIADS[k]

            // pad: a triad of two slightly detuned sines, long attack and release (chords overlap smoothly)
            for (m in tri) {
                note(buf, t0, barLen + 0.15 * RATE, freq(m), 0.075f, KIND_PAD)
                note(buf, t0, barLen + 0.15 * RATE, freq(m) * 1.004, 0.055f, KIND_PAD)
            }

            // bass
            note(buf, t0, beatLen * 1.4, freq(ROOTS[k]), 0.26f, KIND_BASS)
            note(buf, t0 + beatLen * 2, beatLen * 0.9, freq(ROOTS[k] + 7), 0.20f, KIND_BASS)
            note(buf, t0 + beatLen * 3, beatLen * 0.9, freq(ROOTS[k] + 12), 0.18f, KIND_BASS)

            // plucked arpeggio of the chord (from the second round)
            if (round >= 1) {
                val tones = intArrayOf(tri[0] + 12, tri[1] + 12, tri[2] + 12, tri[0] + 24)
                val pattern = intArrayOf(0, 1, 2, 3, 2, 1, 2, 1)
                for (i in 0 until 8) {
                    note(buf, t0 + beatLen * 0.5 * i, beatLen * 0.5, freq(tones[pattern[i]]), 0.085f, KIND_PLUCK)
                }
            }

            // melody (third and fourth round)
            if (round >= 2) {
                for (e in MELODY[round - 2][k]) {
                    note(buf, t0 + beatLen * e[0], beatLen * e[1], freq(e[2].toInt()), 0.14f, KIND_LEAD)
                }
            }

            // quiet shaker on the off-beats (third and fourth round)
            if (round >= 2) {
                for (i in 0 until 4) shaker(buf, t0 + beatLen * (i + 0.5), 0.022f, rnd)
            }
        }

        // normalise to a safe, quiet peak
        var peak = 0f
        for (v in buf) peak = max(peak, abs(v))
        val g = if (peak > 0f) PEAK / peak else 1f
        val out = ShortArray(n)
        for (i in 0 until n) out[i] = (buf[i] * g * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        return out
    }

    private const val KIND_PAD = 0
    private const val KIND_BASS = 1
    private const val KIND_PLUCK = 2
    private const val KIND_LEAD = 3

    /** Adds one note into the ring buffer; [start] and [dur] are in samples (the tail wraps around). */
    private fun note(buf: FloatArray, start: Double, dur: Double, f: Double, amp: Float, kind: Int) {
        val n = buf.size
        val release = when (kind) {
            KIND_PAD -> 0.7
            KIND_BASS -> 0.12
            KIND_PLUCK -> 0.25
            else -> 0.18
        }
        val len = (dur + release * RATE).toInt()
        val s0 = start.roundToInt()
        val w = 2.0 * PI * f / RATE
        for (i in 0 until len) {
            val t = i.toDouble() / RATE
            val td = dur / RATE
            val rel = if (t > td) max(0.0, 1.0 - (t - td) / release) else 1.0
            val ph = w * i
            val s: Double
            val env: Double
            when (kind) {
                KIND_PAD -> {
                    s = sin(ph)
                    env = min(1.0, t / 0.45) * rel
                }
                KIND_BASS -> {
                    val p = (ph / (2.0 * PI)) % 1.0
                    s = 0.8 * (4.0 * abs(p - 0.5) - 1.0) + 0.2 * sin(2.0 * ph)
                    env = min(1.0, t / 0.008) * exp(-t / 0.55) * rel
                }
                KIND_PLUCK -> {
                    s = sin(ph) + 0.3 * sin(2.0 * ph)
                    env = min(1.0, t / 0.004) * exp(-t / 0.22) * rel
                }
                else -> {
                    val vib = 1.0 + 0.004 * sin(2.0 * PI * 5.5 * t) * min(1.0, t / 0.25)
                    val p = (ph * vib / (2.0 * PI)) % 1.0
                    s = 0.6 * (4.0 * abs(p - 0.5) - 1.0) + 0.4 * sin(ph * vib)
                    env = min(1.0, t / 0.012) * exp(-t / 0.7) * rel
                }
            }
            val idx = ((s0 + i) % n + n) % n
            buf[idx] += (s * env * amp).toFloat()
        }
    }

    private fun shaker(buf: FloatArray, start: Double, amp: Float, rnd: Random) {
        val n = buf.size
        val len = (0.06 * RATE).toInt()
        val s0 = start.roundToInt()
        var prev = 0f
        for (i in 0 until len) {
            val x = rnd.nextFloat() * 2f - 1f
            val hp = x - prev * 0.6f          // crude high-pass: bright, no low rumble
            prev = x
            val env = exp(-i.toDouble() / (0.018 * RATE)).toFloat()
            buf[(s0 + i) % n] += hp * env * amp
        }
    }

    /** 16-bit mono PCM WAV file. */
    fun toWav(samples: ShortArray): ByteArray {
        val dataLen = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(RATE)
        bb.putInt(RATE * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(dataLen)
        for (s in samples) bb.putShort(s)
        return bb.array()
    }
}
