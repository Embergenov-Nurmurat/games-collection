package com.example.minigames.core

/** One achievement: id, and title / description in EN, RU, UZ. */
class AchDef(
    val id: String,
    private val title: Array<String>,
    private val desc: Array<String>
) {
    fun title(): String = title[I18n.lang]
    fun desc(): String = desc[I18n.lang]
}

/**
 * Achievements. Games call [unlock] with one of the ids below, [recordGame] when a game ends,
 * and the table-game base class does the generic ones (hard-level wins, first win, ...) by itself.
 */
object Achievements {
    private fun a(id: String, te: String, tr: String, tu: String, de: String, dr: String, du: String) =
        AchDef(id, arrayOf(te, tr, tu), arrayOf(de, dr, du))

    val all: List<AchDef> = listOf(
        a("first_win", "FIRST VICTORY", "ПЕРВАЯ ПОБЕДА", "BIRINCHI G‘ALABA",
            "Win any game against the computer", "Победите компьютер в любой игре", "Istalgan o‘yinda kompyuterni yenging"),
        a("wins_10", "CHAMPION", "ЧЕМПИОН", "CHEMPION",
            "Win 10 games", "Одержите 10 побед", "10 ta g‘alaba qozoning"),
        a("games_25", "REGULAR", "ПОСТОЯННЫЙ ИГРОК", "DOIMIY O‘YINCHI",
            "Finish 25 games", "Завершите 25 игр", "25 ta o‘yinni tugating"),
        a("explorer", "EXPLORER", "ИССЛЕДОВАТЕЛЬ", "SAYYOH",
            "Open every game in the app", "Откройте все игры приложения", "Ilovadagi barcha o‘yinlarni oching"),

        a("flappy_10", "FIRST FLIGHT", "ПЕРВЫЙ ПОЛЁТ", "BIRINCHI UCHISH",
            "Score 10 in Flappy Bird", "Наберите 10 очков во Flappy Bird", "Flappy Bird’da 10 ball to‘plang"),
        a("flappy_25", "SKY RUNNER", "ПОКОРИТЕЛЬ НЕБА", "OSMON QAHRAMONI",
            "Score 25 in Flappy Bird", "Наберите 25 очков во Flappy Bird", "Flappy Bird’da 25 ball to‘plang"),
        a("flappy_50", "LEGEND OF THE SKY", "ЛЕГЕНДА НЕБА", "OSMON AFSONASI",
            "Score 50 in Flappy Bird", "Наберите 50 очков во Flappy Bird", "Flappy Bird’da 50 ball to‘plang"),
        a("flappy_coins", "TREASURE HUNTER", "ОХОТНИК ЗА МОНЕТАМИ", "TANGA OVCHISI",
            "Collect 100 coins in total", "Соберите 100 монет суммарно", "Jami 100 ta tanga yig‘ing"),
        a("flappy_daily", "DAILY FLIGHT", "ЕЖЕДНЕВНЫЙ ВЫЗОВ", "KUNLIK SINOV",
            "Score 5 in the daily challenge", "Наберите 5 очков в ежедневном вызове", "Kunlik sinovda 5 ball to‘plang"),

        a("chess_hard", "GRANDMASTER", "ГРОССМЕЙСТЕР", "GROSSMEYSTER",
            "Beat the hard chess computer", "Победите сложного компьютера в шахматах", "Shaxmatda qiyin kompyuterni yenging"),
        a("chess_fast", "BLITZ ATTACK", "БЛИЦ-АТАКА", "TEZKOR HUJUM",
            "Checkmate the computer in 20 moves or fewer", "Поставьте мат компьютеру не более чем за 20 ходов", "Kompyuterga 20 yurishda yoki tezroq mat qo‘ying"),
        a("chess_puzzles", "PUZZLE MASTER", "МАСТЕР ЗАДАЧ", "MASALA USTASI",
            "Solve 10 chess puzzles", "Решите 10 шахматных задач", "10 ta shaxmat masalasini yeching"),

        a("checkers_hard", "DRAUGHTS MASTER", "МАСТЕР ШАШЕК", "SHASHKA USTASI",
            "Beat the hard checkers computer", "Победите сложного компьютера в шашках", "Shashkada qiyin kompyuterni yenging"),
        a("checkers_multi", "TRIPLE JUMP", "ТРОЙНОЙ УДАР", "UCHTA URISH",
            "Capture 3 or more pieces in one move", "Возьмите 3 и более шашки за один ход", "Bir yurishda 3 ta yoki ko‘proq dona oling"),

        a("ttt_hard", "TIC-TAC-GENIUS", "ГЕНИЙ КРЕСТИКОВ", "XO DAHOSI",
            "Beat the hard tic-tac-toe computer", "Победите сложного компьютера в крестиках-ноликах", "XO o‘yinida qiyin kompyuterni yenging"),
        a("c4_hard", "CONNECTED", "ЧЕТЫРЕ В РЯД", "TO‘RTTA QATORDA",
            "Beat the hard Connect Four computer", "Победите сложного компьютера в «Четыре в ряд»", "“To‘rt qatorda”da qiyin kompyuterni yenging"),
        a("bs_hard", "ADMIRAL", "АДМИРАЛ", "ADMIRAL",
            "Beat the hard Battleship computer", "Победите сложного компьютера в «Морском бое»", "“Dengiz jangi”da qiyin kompyuterni yenging"),
        a("bs_perfect", "UNSINKABLE", "НЕПОТОПЛЯЕМЫЙ", "CHO‘KMAS",
            "Win Battleship without losing a ship", "Выиграйте «Морской бой», не потеряв ни одного корабля", "“Dengiz jangi”da birorta kemani yo‘qotmay g‘alaba qozoning"),
        a("bg_hard", "BACKGAMMON PRO", "ПРОФИ НАРД", "NARD USTASI",
            "Beat the hard backgammon computer", "Победите сложного компьютера в нардах", "Nardda qiyin kompyuterni yenging"),
        a("bg_gammon", "GAMMON", "МАРС", "MARS",
            "Win backgammon before the opponent bears off a checker", "Выиграйте нарды «марсом»", "Nardni “mars” bilan yuting"),

        a("sudoku_easy", "WARM-UP", "РАЗМИНКА", "ISINISH",
            "Solve an easy sudoku", "Решите лёгкое судоку", "Oson sudokuni yeching"),
        a("sudoku_med", "THINKER", "МЫСЛИТЕЛЬ", "MUSHOHADACHI",
            "Solve a medium sudoku", "Решите среднее судоку", "O‘rtacha sudokuni yeching"),
        a("sudoku_hard", "SUDOKU EXPERT", "ЭКСПЕРТ СУДОКУ", "SUDOKU MUTAXASSISI",
            "Solve a hard sudoku", "Решите сложное судоку", "Qiyin sudokuni yeching"),
        a("sudoku_clean", "FLAWLESS", "БЕЗ ОШИБОК", "XATOSIZ",
            "Solve a sudoku without mistakes and hints", "Решите судоку без ошибок и подсказок", "Sudokuni xatosiz va maslahatsiz yeching"),

        a("g2048_1024", "HALFWAY THERE", "НА ПОЛПУТИ", "YARIM YO‘LDA",
            "Reach the 1024 tile in 2048", "Соберите плитку 1024 в игре 2048", "2048 o‘yinida 1024 plitkasiga yeting"),
        a("g2048_2048", "TWENTY FORTY-EIGHT", "ДВЕ ТЫСЯЧИ СОРОК ВОСЕМЬ", "IKKI MING QIRQ SAKKIZ",
            "Reach the 2048 tile", "Соберите плитку 2048", "2048 plitkasiga yeting"),

        a("mines_easy", "SAPPER", "САПЁР", "SAPYOR",
            "Clear an easy minefield", "Разминируйте лёгкое поле", "Oson maydonni minalardan tozalang"),
        a("mines_med", "BOMB DISPOSAL", "РАЗМИНИРОВАНИЕ", "MINA TOZALASH",
            "Clear a medium minefield", "Разминируйте среднее поле", "O‘rtacha maydonni minalardan tozalang"),
        a("mines_hard", "DEMINING EXPERT", "МАСТЕР-САПЁР", "USTA SAPYOR",
            "Clear a hard minefield", "Разминируйте сложное поле", "Qiyin maydonni minalardan tozalang")
    )

    fun isUnlocked(store: Store, id: String): Boolean = store.getB("ach_$id", false)

    fun unlockedCount(store: Store): Int = all.count { isUnlocked(store, it.id) }

    /** Unlocks [id] (ignored when unknown or already unlocked) and shows a banner. */
    fun unlock(store: Store, id: String) {
        val def = all.firstOrNull { it.id == id } ?: return
        if (isUnlocked(store, id)) return
        store.putB("ach_$id", true)
        Toasts.show(t("ach_unlocked"), def.title())
    }

    /** Call when any game ends (won = the player beat the computer / solved the puzzle). */
    fun recordGame(store: Store, won: Boolean) {
        val games = store.getI("cnt_games", 0) + 1
        store.putI("cnt_games", games)
        if (games >= 25) unlock(store, "games_25")
        if (won) {
            val wins = store.getI("cnt_wins", 0) + 1
            store.putI("cnt_wins", wins)
            unlock(store, "first_win")
            if (wins >= 10) unlock(store, "wins_10")
        }
    }

    /** Adds [n] to a named counter and returns the new value. */
    fun addCounter(store: Store, name: String, n: Int = 1): Int {
        val v = store.getI("cnt_$name", 0) + n
        store.putI("cnt_$name", v)
        return v
    }

    /** Remembers that the player has opened [game]; unlocks "explorer" when all [total] were opened. */
    fun markOpened(store: Store, game: GameId, total: Int) {
        if (game == GameId.HUB) return
        store.putB("opened_${game.name}", true)
        val n = GameId.values().count { it != GameId.HUB && store.getB("opened_${it.name}", false) }
        if (n >= total) unlock(store, "explorer")
    }
}

/** Short banners ("achievement unlocked") drawn on top of every screen by [BaseView]. */
object Toasts {
    class Item(val title: String, val sub: String) {
        var age = 0
    }

    private val queue = ArrayList<Item>()

    const val LIFE = 190
    const val SLIDE = 16

    fun show(title: String, sub: String) {
        queue.add(Item(title, sub))
    }

    fun current(): Item? = if (queue.isEmpty()) null else queue[0]

    fun tick() {
        if (queue.isEmpty()) return
        val it = queue[0]
        it.age++
        if (it.age >= LIFE) queue.removeAt(0)
    }
}
