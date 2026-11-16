package com.example.minigames.core

import java.util.Locale

/**
 * All texts of the app in English, Russian and Uzbek (Latin script).
 * Use [t] everywhere; "{0}", "{1}" are replaced by the arguments.
 * Uzbek uses the apostrophe U+2018 in o‘ / g‘.
 */
object I18n {
    const val EN = 0
    const val RU = 1
    const val UZ = 2

    var lang = EN

    val codes = arrayOf("EN", "RU", "UZ")
    val names = arrayOf("ENGLISH", "РУССКИЙ", "O‘ZBEKCHA")

    /** Language that follows the phone's setting (English when it is not supported). */
    fun detect(): Int = when (Locale.getDefault().language) {
        "ru" -> RU
        "uz" -> UZ
        else -> EN
    }

    fun get(key: String): String {
        val a = TABLE[key] ?: return key
        return a[lang]
    }

    private fun e(en: String, ru: String, uz: String) = arrayOf(en, ru, uz)

    private val TABLE: HashMap<String, Array<String>> = hashMapOf(
        // --- general ---
        "play" to e("PLAY", "ИГРАТЬ", "O‘YNASH"),
        "back" to e("BACK", "НАЗАД", "ORQAGA"),
        "menu" to e("MENU", "МЕНЮ", "MENYU"),
        "again" to e("AGAIN", "ЕЩЁ РАЗ", "YANA"),
        "new_game" to e("NEW", "ЗАНОВО", "YANGI"),
        "undo" to e("UNDO", "ОТМЕНА", "BEKOR"),
        "hint" to e("HINT", "ПОДСКАЗКА", "MASLAHAT"),
        "settings" to e("SETTINGS", "НАСТРОЙКИ", "SOZLAMALAR"),
        "language" to e("LANGUAGE", "ЯЗЫК", "TIL"),
        "on" to e("ON", "ВКЛ", "YOQIQ"),
        "off" to e("OFF", "ВЫКЛ", "O‘CHIQ"),
        "random" to e("RANDOM", "СЛУЧАЙНО", "TASODIFIY"),
        "thinking" to e("THINKING", "ДУМАЕТ", "O‘YLAMOQDA"),
        "all_games" to e("ALL GAMES", "ВСЕ ИГРЫ", "BARCHA O‘YINLAR"),
        "hub_title" to e("MINI GAMES", "МИНИ-ИГРЫ", "MINI O‘YINLAR"),
        "sound_x" to e("SOUND: {0}", "ЗВУК: {0}", "TOVUSH: {0}"),
        "vibration_x" to e("VIBRATION: {0}", "ВИБРАЦИЯ: {0}", "TEBRANISH: {0}"),

        // --- game titles ---
        "flappy_title" to e("FLAPPY BIRD", "FLAPPY BIRD", "FLAPPY BIRD"),
        "ttt_title" to e("TIC-TAC-TOE", "КРЕСТИКИ-НОЛИКИ", "XO O‘YINI"),
        "chess_title" to e("CHESS", "ШАХМАТЫ", "SHAXMAT"),
        "checkers_title" to e("CHECKERS", "ШАШКИ", "SHASHKA"),

        // --- levels, modes, sides ---
        "lvl_easy" to e("EASY", "ЛЁГКИЙ", "OSON"),
        "lvl_med" to e("MEDIUM", "СРЕДНИЙ", "O‘RTACHA"),
        "lvl_hard" to e("HARD", "СЛОЖНЫЙ", "QIYIN"),
        "level_x" to e("LEVEL: {0}", "УРОВЕНЬ: {0}", "DARAJA: {0}"),
        "mode_x" to e("MODE: {0}", "РЕЖИМ: {0}", "REJIM: {0}"),
        "vs_ai" to e("VS COMPUTER", "С КОМПЬЮТЕРОМ", "KOMPYUTERGA QARSHI"),
        "two_players" to e("2 PLAYERS", "2 ИГРОКА", "2 O‘YINCHI"),
        "side_x" to e("SIDE: {0}", "СТОРОНА: {0}", "TOMON: {0}"),
        "white" to e("WHITE", "БЕЛЫЕ", "OQ"),
        "black" to e("BLACK", "ЧЁРНЫЕ", "QORA"),
        "board_x" to e("BOARD: {0}", "ПОЛЕ: {0}", "TAXTA: {0}"),

        // --- board games: status and results ---
        "your_turn_as" to e("YOUR TURN ({0})", "ВАШ ХОД ({0})", "SIZNING NAVBATINGIZ ({0})"),
        "turn_of" to e("TURN: {0}", "ХОД: {0}", "NAVBAT: {0}"),
        "you_win" to e("YOU WIN!", "ВЫ ПОБЕДИЛИ!", "SIZ YUTDINGIZ!"),
        "you_lose" to e("YOU LOSE", "ВЫ ПРОИГРАЛИ", "SIZ YUTQAZDINGIZ"),
        "side_wins" to e("{0} WINS", "{0}: ПОБЕДА", "{0}: G‘ALABA"),
        "draw_title" to e("DRAW", "НИЧЬЯ", "DURANG"),
        "draws_short" to e("DRAW", "НИЧЬЯ", "DURANG"),
        "stats_line" to e(
            "WINS {0}   LOSSES {1}   DRAWS {2}",
            "ПОБЕДЫ {0}   ПОРАЖЕНИЯ {1}   НИЧЬИ {2}",
            "G‘ALABA {0}   MAG‘LUBIYAT {1}   DURANG {2}"
        ),
        "stats_short" to e("W {0}  L {1}  D {2}", "П {0}  Пр {1}  Н {2}", "G‘ {0}  M {1}  D {2}"),
        "check_excl" to e("CHECK!", "ШАХ!", "SHOH!"),
        "promote" to e("CHOOSE A PIECE", "ВЫБЕРИТЕ ФИГУРУ", "DONANI TANLANG"),
        "must_capture" to e("CAPTURE IS MANDATORY!", "ВЗЯТИЕ ОБЯЗАТЕЛЬНО!", "OLISH MAJBURIY!"),
        "r_checkmate" to e("CHECKMATE!", "МАТ!", "MAT!"),
        "r_stalemate" to e("STALEMATE", "ПАТ", "PAT"),
        "r_insufficient" to e("NOT ENOUGH PIECES TO MATE", "МАТЕРИАЛА ДЛЯ МАТА НЕДОСТАТОЧНО", "MAT UCHUN DONALAR YETARLI EMAS"),
        "r_fifty" to e("FIFTY-MOVE RULE", "ПРАВИЛО 50 ХОДОВ", "50 YURISH QOIDASI"),
        "r_repetition" to e("THREEFOLD REPETITION", "ТРОЕКРАТНОЕ ПОВТОРЕНИЕ", "UCH MARTA TAKRORLANDI"),
        "r_no_moves" to e("NO MOVES LEFT", "НЕТ ХОДОВ", "YURISH QOLMADI"),
        "r_quiet" to e("NO PROGRESS FOR 15 MOVES", "15 ХОДОВ БЕЗ ПРОГРЕССА", "15 YURISH NATIJASIZ"),
        "r_board_full" to e("BOARD IS FULL", "ПОЛЕ ЗАПОЛНЕНО", "TAXTA TO‘LDI"),

        // --- Flappy Bird ---
        "skin_x" to e("SKIN: {0}", "ОБРАЗ: {0}", "OBRAZ: {0}"),
        "skin_classic" to e("CLASSIC", "КЛАССИКА", "KLASSIK"),
        "skin_ruby" to e("RUBY", "РУБИН", "YOQUT"),
        "skin_ocean" to e("OCEAN", "ОКЕАН", "OKEAN"),
        "skin_mint" to e("MINT", "МЯТА", "YALPIZ"),
        "skin_galaxy" to e("GALAXY", "ГАЛАКТИКА", "GALAKTIKA"),
        "theme_x" to e("THEME: {0}", "ТЕМА: {0}", "MAVZU: {0}"),
        "theme_auto" to e("AUTO", "АВТО", "AVTO"),
        "theme_day" to e("DAY", "ДЕНЬ", "KUN"),
        "theme_night" to e("NIGHT", "НОЧЬ", "TUN"),
        "shields_x" to e("SHIELDS: {0}", "ЩИТЫ: {0}", "QALQONLAR: {0}"),
        "shields_hint" to e(
            "A SHIELD BUBBLE PROTECTS FROM ONE HIT",
            "ЩИТ ЗАЩИЩАЕТ ОТ ОДНОГО УДАРА",
            "QALQON BIR URILISHDAN HIMOYA QILADI"
        ),
        "get_ready" to e("GET READY", "ПРИГОТОВЬТЕСЬ", "TAYYOR BO‘LING"),
        "tap_to_flap" to e("TAP TO FLAP", "КАСАЙТЕСЬ, ЧТОБЫ ЛЕТЕТЬ", "UCHISH UCHUN BOSING"),
        "paused" to e("PAUSED", "ПАУЗА", "PAUZA"),
        "resume" to e("RESUME", "ПРОДОЛЖИТЬ", "DAVOM ETISH"),
        "retry" to e("RETRY", "ЗАНОВО", "QAYTA"),
        "game_over" to e("GAME OVER", "ИГРА ОКОНЧЕНА", "O‘YIN TUGADI"),
        "medal" to e("MEDAL", "МЕДАЛЬ", "MEDAL"),
        "score" to e("SCORE", "СЧЁТ", "HISOB"),
        "best" to e("BEST", "РЕКОРД", "REKORD"),
        "best_x" to e("BEST: {0}", "РЕКОРД: {0}", "REKORD: {0}"),
        "best_coins" to e("BEST: {0}   COINS: {1}", "РЕКОРД: {0}   МОНЕТЫ: {1}", "REKORD: {0}   TANGALAR: {1}"),
        "new_badge" to e("NEW", "НОВЫЙ", "YANGI"),
        "new_skin_x" to e("NEW SKIN: {0}!", "НОВЫЙ ОБРАЗ: {0}!", "YANGI OBRAZ: {0}!"),
        "next_skin" to e("NEXT SKIN AT SCORE {0}", "СЛЕДУЮЩИЙ ОБРАЗ: РЕКОРД {0}", "KEYINGI OBRAZ: REKORD {0}"),
        "all_skins" to e("ALL SKINS UNLOCKED!", "ВСЕ ОБРАЗЫ ОТКРЫТЫ!", "BARCHA OBRAZLAR OCHILDI!")
    )
}

/** Translated text for [key]. */
fun t(key: String): String = I18n.get(key)

/** Translated text with {0}, {1}, ... replaced by [args]. */
fun t(key: String, vararg args: Any): String {
    var s = I18n.get(key)
    for (i in args.indices) s = s.replace("{" + i + "}", args[i].toString())
    return s
}
