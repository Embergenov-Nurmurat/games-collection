package com.example.minigames.core

enum class GameId { HUB, FLAPPY, TTT, CHESS, CHECKERS, C4, BACKGAMMON, G2048, SUDOKU, MINES, BATTLESHIP }

/** What a screen can ask from the activity that hosts it. */
interface Host {
    val store: Store
    val sfx: Sfx

    /** Switch to another screen (safe to call from inside a frame callback). */
    fun open(id: GameId)

    /** Close the app. */
    fun finishApp()

    /** Re-reads [Store.themeMode] and updates [Ui.dark]. */
    fun applyTheme()

    /** Opens the system share sheet with a plain text (e.g. a chess game in PGN). */
    fun shareText(text: String)

    /** Starts / stops the background music according to [Store.musicOn]. */
    fun updateMusic()
}
