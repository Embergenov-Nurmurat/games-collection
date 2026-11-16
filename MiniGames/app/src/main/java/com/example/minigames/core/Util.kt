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

/** Colour palette of the whole app: dark neutral surfaces, one accent colour per screen. */
object Ui {
    val BG_TOP: Int = Color.rgb(17, 21, 31)
    val BG_BOTTOM: Int = Color.rgb(9, 11, 17)
    val SURFACE: Int = Color.rgb(27, 33, 46)
    val SURFACE_HI: Int = Color.rgb(37, 45, 62)
    val BORDER: Int = Color.rgb(54, 64, 86)
    val TEXT: Int = Color.rgb(236, 240, 247)
    val MUTED: Int = Color.rgb(140, 149, 168)
    val INK: Int = Color.rgb(16, 19, 27)
    val ACCENT: Int = Color.rgb(245, 181, 68)
    val OK: Int = Color.rgb(98, 214, 164)
    val DANGER: Int = Color.rgb(240, 110, 110)
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
