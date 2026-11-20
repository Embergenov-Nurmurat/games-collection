package com.example.minigames.flappy

import android.graphics.Color
import com.example.minigames.core.H

const val GROUND_H = 100f
const val GROUND_Y = H - GROUND_H

// Physics / geometry
const val GRAVITY = 0.42f
const val FLAP_V = -7.6f
const val MAX_FALL = 11f
const val PIPE_W = 70f
const val CAP_W = 86f
const val CAP_H = 30f
const val BIRD_X = 130f
const val BIRD_R = 13f

enum class Difficulty(
    val key: String,         // translation key of the name
    val gap: Float,          // vertical opening between pipes
    val spacing: Float,      // horizontal distance between pipes
    val baseSpeed: Float,    // starting scroll speed
    val speedStep: Float,    // speed gained per passed pipe
    val maxBonus: Float,     // cap for the speed gain
    val maxShift: Float,     // max vertical jump between neighbouring gaps
    val movingFrom: Int      // from this many passed pipes some pipes start moving
) {
    EASY("lvl_easy", 200f, 250f, 2.6f, 0.03f, 0.8f, 200f, Int.MAX_VALUE),
    NORMAL("lvl_med", 170f, 230f, 3.0f, 0.04f, 1.2f, 190f, 12),
    HARD("lvl_hard", 145f, 220f, 3.4f, 0.05f, 1.6f, 150f, 6)
}

/** Bird colours. A skin unlocks once your best score (in any mode) reaches [unlockAt]. */
enum class Skin(
    val key: String,
    val unlockAt: Int,
    val bodyTop: Int,
    val bodyBottom: Int,
    val belly: Int,
    val tail: Int,
    val beakUpper: Int,
    val beakLower: Int,
    val coinsAt: Int = 0,        // ... and the total number of coins collected reaches this
    val dailyAt: Int = 0         // ... and the best daily-challenge streak (days in a row) reaches this
) {
    CLASSIC(
        "skin_classic", 0,
        Color.rgb(255, 232, 100), Color.rgb(245, 170, 30), Color.rgb(255, 243, 200),
        Color.rgb(238, 120, 40), Color.rgb(255, 120, 45), Color.rgb(232, 70, 40)
    ),
    RUBY(
        "skin_ruby", 10,
        Color.rgb(255, 150, 150), Color.rgb(214, 48, 62), Color.rgb(255, 226, 220),
        Color.rgb(170, 30, 50), Color.rgb(255, 190, 50), Color.rgb(230, 140, 20)
    ),
    OCEAN(
        "skin_ocean", 20,
        Color.rgb(150, 225, 255), Color.rgb(38, 120, 220), Color.rgb(220, 244, 255),
        Color.rgb(30, 80, 170), Color.rgb(255, 150, 60), Color.rgb(235, 100, 30)
    ),
    MINT(
        "skin_mint", 30,
        Color.rgb(190, 255, 190), Color.rgb(40, 180, 110), Color.rgb(236, 255, 230),
        Color.rgb(30, 130, 80), Color.rgb(255, 200, 60), Color.rgb(235, 150, 30)
    ),
    GALAXY(
        "skin_galaxy", 50,
        Color.rgb(210, 160, 255), Color.rgb(100, 50, 190), Color.rgb(240, 225, 255),
        Color.rgb(70, 30, 140), Color.rgb(255, 120, 200), Color.rgb(220, 70, 150)
    ),
    // new skins are appended: the chosen skin is stored as an index
    GOLD(
        "fl_skin_gold", 0,
        Color.rgb(255, 244, 160), Color.rgb(224, 170, 24), Color.rgb(255, 249, 214),
        Color.rgb(190, 130, 20), Color.rgb(255, 140, 60), Color.rgb(230, 100, 40),
        coinsAt = 150
    ),
    SUNSET(
        "fl_skin_sunset", 0,
        Color.rgb(255, 196, 136), Color.rgb(240, 90, 96), Color.rgb(255, 232, 218),
        Color.rgb(200, 60, 112), Color.rgb(255, 214, 84), Color.rgb(240, 160, 40),
        dailyAt = 3
    ),
    SHADOW(
        "fl_skin_shadow", 75,
        Color.rgb(126, 136, 158), Color.rgb(38, 44, 64), Color.rgb(156, 166, 190),
        Color.rgb(26, 30, 46), Color.rgb(255, 122, 92), Color.rgb(230, 72, 62)
    )
}

/** Scenery of the game: colours of the day (the night colours are shared). */
enum class Scene(
    val key: String,
    val skyTop: Int,
    val skyBottom: Int,
    val hillFar: Int,
    val hillNear: Int,
    val groundTop: Int,
    val groundBottom: Int,
    val groundEdge: Int,
    val pipeLight: Int,
    val pipeDark: Int,
    val snow: Boolean
) {
    MEADOW(
        "fl_scene_meadow",
        Color.rgb(100, 166, 212), Color.rgb(228, 237, 245), Color.rgb(172, 204, 204), Color.rgb(138, 180, 178),
        Color.rgb(206, 198, 178), Color.rgb(188, 180, 160), Color.rgb(134, 170, 150),
        Color.rgb(120, 200, 164), Color.rgb(72, 152, 122), false
    ),
    DESERT(
        "fl_scene_desert",
        Color.rgb(238, 178, 112), Color.rgb(253, 233, 200), Color.rgb(224, 178, 128), Color.rgb(204, 150, 104),
        Color.rgb(232, 196, 138), Color.rgb(212, 174, 116), Color.rgb(190, 150, 96),
        Color.rgb(128, 184, 108), Color.rgb(84, 138, 82), false
    ),
    WINTER(
        "fl_scene_winter",
        Color.rgb(142, 184, 224), Color.rgb(238, 245, 252), Color.rgb(216, 229, 241), Color.rgb(192, 210, 228),
        Color.rgb(238, 242, 248), Color.rgb(214, 224, 238), Color.rgb(176, 196, 218),
        Color.rgb(150, 208, 226), Color.rgb(98, 160, 192), true
    ),
    SUNSET(
        "fl_scene_sunset",
        Color.rgb(252, 140, 118), Color.rgb(255, 226, 172), Color.rgb(222, 128, 150), Color.rgb(184, 96, 128),
        Color.rgb(234, 172, 152), Color.rgb(212, 146, 130), Color.rgb(176, 100, 110),
        Color.rgb(160, 120, 200), Color.rgb(112, 80, 156), false
    )
}

/** Source of the current time (a test can replace it, e.g. to play on a particular day). */
object FlappyClock {
    var now: () -> Long = { System.currentTimeMillis() }

    /** Number of the current day (local time) counted from 1970-01-01. */
    fun dayNumber(): Int {
        val t = now()
        val off = java.util.TimeZone.getDefault().getOffset(t)
        return Math.floorDiv(t + off, 86_400_000L).toInt()
    }
}

class Pipe(
    var x: Float,
    val baseTop: Float,      // centre line of the gap's top edge
    val amp: Float,          // vertical swing (0 = static pipe)
    val phase: Float,
    val item: Int            // 0 none, 1 coin, 2 shield
) {
    var gapTop = baseTop
    var age = 0f
    var scored = false
    var itemTaken = false
}

class Star(val x: Float, val y: Float, val phase: Float, val size: Float)
