package com.example.minigames.mines

/** Texts of Minesweeper: key to arrayOf(EN, RU, UZ). */
val MINES_STRINGS: Map<String, Array<String>> = mapOf(
    "mn_title" to arrayOf("MINESWEEPER", "САПЁР", "SAPYOR"),
    "mn_info" to arrayOf("{0}×{1} · {2} MINES", "{0}×{1} · МИН: {2}", "{0}×{1} · MINALAR: {2}"),
    "mn_dig" to arrayOf("DIG", "ОТКРЫТЬ", "OCHISH"),
    "mn_flag" to arrayOf("FLAG", "ФЛАЖОК", "BAYROQ"),
    "mn_howto" to arrayOf(
        "TAP TO DIG · HOLD TO FLAG",
        "КАСАНИЕ — ОТКРЫТЬ, УДЕРЖАНИЕ — ФЛАЖОК",
        "BOSING — OCHISH, USHLAB TURING — BAYROQ"
    ),
    "mn_howto2" to arrayOf(
        "TAP A NUMBER WITH ENOUGH FLAGS TO OPEN AROUND IT",
        "КАСАНИЕ ЦИФРЫ С НУЖНЫМ ЧИСЛОМ ФЛАЖКОВ ОТКРОЕТ СОСЕДЕЙ",
        "YETARLI BAYROQLI RAQAMGA BOSSANGIZ, ATROFI OCHILADI"
    ),
    "mn_win" to arrayOf("CLEARED!", "РАЗМИНИРОВАНО!", "TOZALANDI!"),
    "mn_boom" to arrayOf("BOOM!", "БАХ!", "PORTLADI!"),
    "mn_progress" to arrayOf("{0}% CLEARED", "ОТКРЫТО: {0}%", "OCHILGAN: {0}%"),
    "mn_record" to arrayOf("NEW RECORD", "НОВЫЙ РЕКОРД", "YANGI REKORD"),
    "mn_best_time" to arrayOf("BEST TIME: {0}", "ЛУЧШЕЕ ВРЕМЯ: {0}", "ENG YAXSHI VAQT: {0}"),
    "mn_cleared_n" to arrayOf("CLEARED: {0}", "РАЗМИНИРОВАНО: {0}", "TOZALANGAN: {0}"),
    "mn_saved" to arrayOf("SAVED GAME: {0}, {1}", "СОХРАНЕНО: {0}, {1}", "SAQLANGAN: {0}, {1}")
)
