package com.example.minigames.core

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Int, val maxLife: Int, val color: Int, val size: Float, val gravity: Float
)

class Cloud(var x: Float, var y: Float, val scale: Float, val speed: Float)

class Button(
    val label: () -> String,
    var cx: Float,
    var cy: Float,
    val w: Float,
    val h: Float,
    /** 0 orange, 1 blue, 2 green, 3 red, 4 grey (used automatically when disabled) */
    val style: () -> Int = { 0 },
    val enabled: () -> Boolean = { true },
    val visible: () -> Boolean = { true },
    val action: () -> Unit
) {
    var held = false     // a finger is currently on the button
    var down = 0f        // animated 0..1 (pressed look)
    var press = 0f       // short "click" animation

    fun contains(x: Float, y: Float): Boolean =
        x >= cx - w / 2f && x <= cx + w / 2f && y >= cy - h / 2f && y <= cy + h / 2f
}
