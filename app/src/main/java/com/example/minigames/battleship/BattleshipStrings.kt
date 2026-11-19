package com.example.minigames.battleship

/** Texts of Battleship: key to arrayOf(EN, RU, UZ). */
val BS_STRINGS: Map<String, Array<String>> = mapOf(
    "bs_title" to arrayOf("BATTLESHIP", "МОРСКОЙ БОЙ", "DENGIZ JANGI"),
    "bs_rules" to arrayOf(
        "HIT = SHOOT AGAIN · 5 SHIPS: 5-4-3-3-2",
        "ПОПАЛ — СТРЕЛЯЕШЬ СНОВА · 5 КОРАБЛЕЙ: 5-4-3-3-2",
        "TEGSA — YANA O‘Q UZASIZ · 5 TA KEMA: 5-4-3-3-2"
    ),
    "bs_place_title" to arrayOf("PLACE YOUR FLEET", "РАССТАВЬТЕ ФЛОТ", "FLOTNI JOYLANG"),
    "bs_place_hint" to arrayOf(
        "DRAG A SHIP · TAP A SHIP TO TURN IT",
        "ПЕРЕТАЩИТЕ КОРАБЛЬ · КАСАНИЕ — ПОВОРОТ",
        "KEMANI SURING · BOSING — BURISH"
    ),
    "bs_start" to arrayOf("START BATTLE", "В БОЙ", "JANGGA"),
    "bs_your_shot" to arrayOf("YOUR SHOT", "ВАШ ВЫСТРЕЛ", "SIZNING O‘QINGIZ"),
    "bs_enemy_shot" to arrayOf("ENEMY FIRES…", "СОПЕРНИК СТРЕЛЯЕТ…", "RAQIB O‘Q UZMOQDA…"),
    "bs_you_miss" to arrayOf("MISS", "МИМО", "XATO"),
    "bs_you_hit" to arrayOf("HIT! SHOOT AGAIN", "ПОПАЛ! СТРЕЛЯЙТЕ СНОВА", "TEGDI! YANA O‘Q UZING"),
    "bs_you_sunk" to arrayOf("SUNK! SHOOT AGAIN", "ПОТОПЛЕН! СТРЕЛЯЙТЕ СНОВА", "CHO‘KDI! YANA O‘Q UZING"),
    "bs_en_miss" to arrayOf("ENEMY MISSED", "СОПЕРНИК ПРОМАХНУЛСЯ", "RAQIB XATO QILDI"),
    "bs_en_hit" to arrayOf("ENEMY HIT YOUR SHIP", "СОПЕРНИК ПОПАЛ В ВАС", "RAQIB KEMANGIZGA TEGDI"),
    "bs_en_sunk" to arrayOf("YOUR SHIP IS SUNK", "ВАШ КОРАБЛЬ ПОТОПЛЕН", "KEMANGIZ CHO‘KDI"),
    "bs_enemy_fleet" to arrayOf("ENEMY FLEET", "ФЛОТ СОПЕРНИКА", "RAQIB FLOTI"),
    "bs_your_fleet" to arrayOf("YOUR FLEET", "ВАШ ФЛОТ", "SIZNING FLOT"),
    "bs_shots" to arrayOf("SHOTS {0}   HITS {1}", "ВЫСТРЕЛОВ {0}   ПОПАДАНИЙ {1}", "O‘QLAR {0}   TEGDI {1}"),
    "bs_record" to arrayOf("NEW RECORD", "НОВЫЙ РЕКОРД", "YANGI REKORD"),
    "bs_best" to arrayOf("BEST: {0} SHOTS", "ЛУЧШЕЕ: {0} ВЫСТРЕЛОВ", "ENG YAXSHI: {0} O‘Q"),
    "bs_won_n" to arrayOf("WON: {0}", "ПОБЕД: {0}", "G‘ALABA: {0}"),
    "bs_saved" to arrayOf("SAVED GAME: {0}, {1} SHOTS", "СОХРАНЕНО: {0}, ВЫСТРЕЛОВ: {1}", "SAQLANGAN: {0}, O‘Q: {1}"),
    "bs_perfect" to arrayOf("NO SHIPS LOST", "НИ ОДНОГО КОРАБЛЯ НЕ ПОТЕРЯНО", "BIRORTA KEMA YO‘QOTILMADI")
)
