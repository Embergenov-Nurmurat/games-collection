package com.example.minigames.core

import android.content.Context

/** Small SharedPreferences wrapper: settings, records and statistics for all games. */
class Store(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("minigames", Context.MODE_PRIVATE)

    fun getI(key: String, def: Int): Int = prefs.getInt(key, def)

    fun putI(key: String, v: Int) {
        prefs.edit().putInt(key, v).apply()
    }

    fun getB(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)

    fun putB(key: String, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply()
    }

    // ------------------------------ global settings --------------------------

    /** -1 = never chosen (follow the system language) */
    var lang: Int
        get() = getI("lang", -1)
        set(v) = putI("lang", v)

    var soundOn: Boolean
        get() = getB("sound", true)
        set(v) = putB("sound", v)

    var haptics: Boolean
        get() = getB("haptics", true)
        set(v) = putB("haptics", v)

    // ------------------------------ statistics -------------------------------

    /** kind: 0 = wins, 1 = losses, 2 = draws (games against the computer) */
    fun stat(game: String, kind: Int): Int = getI("stat_${game}_$kind", 0)

    fun addStat(game: String, kind: Int) {
        putI("stat_${game}_$kind", stat(game, kind) + 1)
    }

    var totalCoins: Int
        get() = getI("coins", 0)
        set(v) = putI("coins", v)
}
