package com.example.minigames.core

enum class GameId { HUB, FLAPPY, TTT, CHESS, CHECKERS }

/** What a screen can ask from the activity that hosts it. */
interface Host {
    val store: Store
    val sfx: Sfx

    /** Switch to another screen (safe to call from inside a frame callback). */
    fun open(id: GameId)

    /** Close the app. */
    fun finishApp()
}
