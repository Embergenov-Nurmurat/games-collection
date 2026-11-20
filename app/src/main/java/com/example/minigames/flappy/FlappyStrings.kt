package com.example.minigames.flappy

/** Texts of Flappy Bird added in Part 12 (daily challenge, scenes, new skins). Keys start with "fl_". */
val FLAPPY_STRINGS: Map<String, Array<String>> = mapOf(
    "fl_daily" to arrayOf("DAILY CHALLENGE", "ВЫЗОВ ДНЯ", "KUNLIK SINOV"),
    "fl_daily_best" to arrayOf("DAILY CHALLENGE: {0}", "ВЫЗОВ ДНЯ: {0}", "KUNLIK SINOV: {0}"),
    "fl_daily_line" to arrayOf(
        "DAILY · {0} · STREAK {1}",
        "ВЫЗОВ ДНЯ · {0} · СЕРИЯ {1}",
        "KUNLIK · {0} · SERIYA {1}"
    ),
    "fl_scene_x" to arrayOf("SCENE: {0}", "ПЕЙЗАЖ: {0}", "MANZARA: {0}"),
    "fl_scene_meadow" to arrayOf("MEADOW", "ЛУГ", "DALA"),
    "fl_scene_desert" to arrayOf("DESERT", "ПУСТЫНЯ", "CHO‘L"),
    "fl_scene_winter" to arrayOf("WINTER", "ЗИМА", "QISH"),
    "fl_scene_sunset" to arrayOf("SUNSET", "ЗАКАТ", "SHAFAQ"),
    "fl_skin_gold" to arrayOf("GOLD", "ЗОЛОТО", "OLTIN"),
    "fl_skin_sunset" to arrayOf("SUNSET", "ЗАКАТ", "SHAFAQ"),
    "fl_skin_shadow" to arrayOf("SHADOW", "ТЕНЬ", "SOYA"),
    "fl_next_coins" to arrayOf(
        "NEXT SKIN: COLLECT {0} COINS",
        "СЛЕДУЮЩИЙ ОБРАЗ: СОБЕРИТЕ {0} МОНЕТ",
        "KEYINGI OBRAZ: {0} TANGA YIG‘ING"
    ),
    "fl_next_daily" to arrayOf(
        "NEXT SKIN: {0} DAYS OF THE DAILY IN A ROW",
        "СЛЕДУЮЩИЙ ОБРАЗ: ВЫЗОВ ДНЯ {0} ДНЯ ПОДРЯД",
        "KEYINGI OBRAZ: KUNLIK SINOV {0} KUN KETMA-KET"
    )
)
