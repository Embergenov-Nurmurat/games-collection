package com.example.minigames.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sound effects without any audio assets: every effect is synthesised at start-up,
 * written as a tiny WAV file into the cache dir and played through SoundPool.
 */
class Sfx(context: Context) {

    enum class Kind {
        FLAP, SCORE, COIN, SHIELD, HIT, POP, CLICK,
        MOVE, CAPTURE, PLACE, CHECK, WIN, LOSE, DRAW
    }

    var enabled = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = IntArray(Kind.values().size)

    init {
        try {
            for (k in Kind.values()) {
                val file = File(context.cacheDir, "sfx2_" + k.name.lowercase() + ".wav")
                file.writeBytes(toWav(synth(k)))
                ids[k.ordinal] = pool.load(file.absolutePath, 1)
            }
        } catch (e: Exception) {
            // Sound is optional - the game still works without it.
        }
    }

    fun play(kind: Kind, volume: Float = 1f, rate: Float = 1f) {
        if (!enabled) return
        val id = ids[kind.ordinal]
        if (id != 0) pool.play(id, volume, volume, 1, 0, rate)
    }

    fun release() {
        pool.release()
    }

    // ----------------------------- synthesis -----------------------------

    private fun synth(kind: Kind): ShortArray = when (kind) {
        Kind.FLAP -> render(110, 0.35f, 2) { t -> 380f + 340f * t }
        Kind.SCORE -> render(70, 0.40f, 1) { 988f } + render(130, 0.40f, 1) { 1319f }
        Kind.COIN -> render(60, 0.32f, 1) { 1175f } + render(150, 0.32f, 1) { 1568f }
        Kind.SHIELD -> render(180, 0.50f, 0) { t -> 440f + 880f * t }
        Kind.HIT -> mix(
            render(300, 0.55f, 0) { t -> 240f - 190f * t },
            render(220, 0.60f, 3) { 0f }
        )
        Kind.POP -> render(90, 0.55f, 3) { 0f }
        Kind.CLICK -> render(35, 0.40f, 0) { 720f }
        Kind.MOVE -> mix(render(70, 0.50f, 0) { 170f }, render(28, 0.35f, 3) { 0f })
        Kind.CAPTURE -> mix(render(120, 0.55f, 0) { t -> 150f - 50f * t }, render(80, 0.50f, 3) { 0f })
        Kind.PLACE -> render(90, 0.45f, 0) { t -> 560f - 200f * t }
        Kind.CHECK -> render(70, 0.38f, 1) { 880f } + render(110, 0.38f, 1) { 660f }
        Kind.WIN -> render(90, 0.40f, 2) { 523f } + render(90, 0.40f, 2) { 659f } +
                render(90, 0.40f, 2) { 784f } + render(260, 0.42f, 2) { 1047f }
        Kind.LOSE -> render(150, 0.40f, 2) { 392f } + render(150, 0.40f, 2) { 330f } +
                render(300, 0.40f, 2) { 262f }
        Kind.DRAW -> render(130, 0.35f, 0) { 523f } + render(220, 0.35f, 0) { 440f }
    }

    /**
     * @param wave 0 sine, 1 square, 2 triangle, 3 white noise
     * @param freq frequency in Hz as a function of normalised time 0..1
     */
    private fun render(ms: Int, volume: Float, wave: Int, freq: (Float) -> Float): ShortArray {
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        var phase = 0f
        val twoPi = 2f * PI.toFloat()
        for (i in 0 until n) {
            val t = i.toFloat() / n
            phase += twoPi * freq(t) / RATE
            val s = when (wave) {
                0 -> sin(phase)
                1 -> if (sin(phase) >= 0f) 1f else -1f
                2 -> {
                    val p = (phase / twoPi) % 1f
                    4f * abs(p - 0.5f) - 1f
                }
                else -> Random.nextFloat() * 2f - 1f
            }
            val attack = minOf(1f, i / (RATE * 0.004f))
            val env = attack * (1f - t).pow(1.4f)
            out[i] = (s * env * volume * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun mix(a: ShortArray, b: ShortArray): ShortArray {
        val n = maxOf(a.size, b.size)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = (if (i < a.size) a[i].toInt() else 0) + (if (i < b.size) b[i].toInt() else 0)
            out[i] = v.coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun toWav(samples: ShortArray): ByteArray {
        val dataLen = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)               // PCM
        bb.putShort(1)               // mono
        bb.putInt(RATE)
        bb.putInt(RATE * 2)          // byte rate
        bb.putShort(2)               // block align
        bb.putShort(16)              // bits per sample
        bb.put("data".toByteArray())
        bb.putInt(dataLen)
        for (s in samples) bb.putShort(s)
        return bb.array()
    }

    private companion object {
        const val RATE = 22050
    }
}
