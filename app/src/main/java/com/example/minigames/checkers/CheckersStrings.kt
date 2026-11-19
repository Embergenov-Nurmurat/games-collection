package com.example.minigames.checkers

/** Texts of the draughts screen (rule sets, messages). Keys start with "ck_". */
val CHECKERS_STRINGS: Map<String, Array<String>> = mapOf(
    "ck_rules_x" to arrayOf("RULES: {0}", "ПРАВИЛА: {0}", "QOIDALAR: {0}"),
    "ck_v_ru" to arrayOf("RUSSIAN 8×8", "РУССКИЕ 8×8", "RUS 8×8"),
    "ck_v_int" to arrayOf("INTERNATIONAL 10×10", "МЕЖДУНАРОДНЫЕ 10×10", "XALQARO 10×10"),
    "ck_v_give" to arrayOf("GIVEAWAY 8×8", "ПОДДАВКИ 8×8", "TESKARI 8×8"),
    "ck_cap_ru" to arrayOf(
        "Russian 8×8 · capture in any way you like",
        "Русские 8×8 · любое взятие на выбор",
        "Rus 8×8 · olishni o‘zingiz tanlaysiz"
    ),
    "ck_cap_int" to arrayOf(
        "International 10×10 · take the most pieces",
        "Международные 10×10 · берите больше всех",
        "Xalqaro 10×10 · eng ko‘p dona oling"
    ),
    "ck_cap_give" to arrayOf(
        "Giveaway 8×8 · lose all your pieces to win",
        "Поддавки 8×8 · отдайте все шашки, чтобы выиграть",
        "Teskari 8×8 · yutish uchun barcha donalarni bering"
    ),
    "ck_majority" to arrayOf(
        "TAKE THE MOST PIECES!",
        "БЕРИТЕ МАКСИМУМ ШАШЕК!",
        "ENG KO‘P DONA OLING!"
    ),
    "ck_quiet25" to arrayOf(
        "NO PROGRESS FOR 25 MOVES",
        "25 ХОДОВ БЕЗ ПРОГРЕССА",
        "25 YURISH NATIJASIZ"
    )
)
