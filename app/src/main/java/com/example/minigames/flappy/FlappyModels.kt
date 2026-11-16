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
    val beakLower: Int
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
    )
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
