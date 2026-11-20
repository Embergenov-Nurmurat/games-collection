package com.example.minigames

import com.example.minigames.backgammon.BG_STRINGS
import com.example.minigames.battleship.BS_STRINGS
import com.example.minigames.c4.C4_STRINGS
import com.example.minigames.checkers.CHECKERS_STRINGS
import com.example.minigames.chess.CHESS_STRINGS
import com.example.minigames.core.I18n
import com.example.minigames.flappy.FLAPPY_STRINGS
import com.example.minigames.g2048.G2048_STRINGS
import com.example.minigames.mines.MINES_STRINGS
import com.example.minigames.sudoku.SUDOKU_STRINGS

/**
 * Registers the text tables of all games in one place. Call once at start-up (MainActivity.onCreate) and from any
 * test main that creates views. A new game adds its `<NAME>_STRINGS` table here.
 */
object GameStrings {
    val tables: List<Map<String, Array<String>>> = listOf(
        C4_STRINGS, BG_STRINGS, G2048_STRINGS, SUDOKU_STRINGS, MINES_STRINGS,
        BS_STRINGS, CHESS_STRINGS, CHECKERS_STRINGS, FLAPPY_STRINGS
    )

    fun registerAll() {
        for (m in tables) I18n.register(m)
    }
}
