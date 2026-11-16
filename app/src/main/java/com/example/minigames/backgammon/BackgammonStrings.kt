package com.example.minigames.backgammon

/** Texts of short backgammon: key to arrayOf(EN, RU, UZ). */
val BG_STRINGS: Map<String, Array<String>> = mapOf(
    "bg_title" to arrayOf("BACKGAMMON", "НАРДЫ", "NARD"),
    "bg_roll" to arrayOf("ROLL DICE", "БРОСИТЬ", "ZAR TASHLASH"),
    "bg_you" to arrayOf("YOU", "ВЫ", "SIZ"),
    "bg_cpu" to arrayOf("COMPUTER", "КОМПЬЮТЕР", "KOMPYUTER"),
    "bg_pips" to arrayOf("PIPS {0}", "ПИПСЫ {0}", "PIPS {0}"),
    "bg_your_roll" to arrayOf("YOUR TURN: ROLL THE DICE", "ВАШ ХОД: БРОСЬТЕ КОСТИ", "NAVBATINGIZ: ZAR TASHLANG"),
    "bg_your_move" to arrayOf("YOUR MOVE", "ВАШ ХОД", "SIZNING YURISHINGIZ"),
    "bg_cpu_turn" to arrayOf("COMPUTER'S TURN", "ХОД КОМПЬЮТЕРА", "KOMPYUTER YURISHI"),
    "bg_no_moves" to arrayOf("NO LEGAL MOVES", "НЕТ ВОЗМОЖНЫХ ХОДОВ", "YURISH IMKONI YO‘Q"),
    "bg_no_more" to arrayOf("NO MORE MOVES POSSIBLE", "БОЛЬШЕ ХОДОВ НЕТ", "BOSHQA YURISH YO‘Q"),
    "bg_first_you" to arrayOf("YOU MOVE FIRST", "ВЫ ХОДИТЕ ПЕРВЫМ", "BIRINCHI SIZ YURASIZ"),
    "bg_first_cpu" to arrayOf("COMPUTER MOVES FIRST", "ПЕРВЫМ ХОДИТ КОМПЬЮТЕР", "BIRINCHI KOMPYUTER YURADI"),
    "bg_first_x" to arrayOf("{0} MOVES FIRST", "ПЕРВЫМИ ХОДЯТ: {0}", "BIRINCHI YURADI: {0}"),
    "bg_r_borne" to arrayOf("ALL CHECKERS BORNE OFF", "ВСЕ ШАШКИ СНЯТЫ", "BARCHA DONALAR CHIQARILDI")
)
