package com.example.minigames.core

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Logical playfield size. Every screen is drawn in this 480x720 space and scaled to fit the display. */
const val W = 480f
const val H = 720f
const val STEP = 1f / 60f
const val RAD2DEG = 57.29578f

val OUTLINE: Int = Color.rgb(14, 18, 28)

/** Colour palette of the whole app (dark or light), plus one accent colour per screen. */
object Ui {
    /** Switched by the host when the user changes the appearance (or the system theme changes). */
    @JvmField var dark: Boolean = true

    private fun pick(d: Int, l: Int): Int = if (dark) d else l

    val BG_TOP: Int get() = pick(Color.rgb(17, 21, 31), Color.rgb(246, 248, 252))
    val BG_BOTTOM: Int get() = pick(Color.rgb(9, 11, 17), Color.rgb(228, 232, 241))
    val SURFACE: Int get() = pick(Color.rgb(27, 33, 46), Color.rgb(255, 255, 255))
    val SURFACE_HI: Int get() = pick(Color.rgb(37, 45, 62), Color.rgb(236, 240, 247))
    val BORDER: Int get() = pick(Color.rgb(54, 64, 86), Color.rgb(205, 211, 224))
    val GRID: Int get() = pick(Color.rgb(62, 73, 98), Color.rgb(196, 203, 218))
    val TEXT: Int get() = pick(Color.rgb(236, 240, 247), Color.rgb(24, 30, 44))
    val MUTED: Int get() = pick(Color.rgb(140, 149, 168), Color.rgb(104, 114, 134))
    val DISABLED_BG: Int get() = pick(Color.rgb(21, 26, 36), Color.rgb(233, 236, 242))
    val DISABLED_FG: Int get() = pick(Color.rgb(92, 101, 120), Color.rgb(160, 168, 184))
    val INK: Int get() = Color.rgb(16, 19, 27)
    val ACCENT: Int get() = Color.rgb(245, 181, 68)
    val OK: Int get() = pick(Color.rgb(98, 214, 164), Color.rgb(24, 160, 110))
    val DANGER: Int get() = pick(Color.rgb(240, 110, 110), Color.rgb(212, 64, 64))
    val GLOW_ALPHA: Float get() = if (dark) 0.2f else 0.14f
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clampF(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

fun mixC(a: Int, b: Int, t: Float): Int {
    val k = clampF(t, 0f, 1f)
    return Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).roundToInt()
    )
}

fun withA(c: Int, a: Float): Int =
    Color.argb((clampF(a, 0f, 1f) * 255f).roundToInt(), Color.red(c), Color.green(c), Color.blue(c))

fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

fun easeOutCubic(t: Float): Float {
    val x = 1f - clampF(t, 0f, 1f)
    return 1f - x * x * x
}
