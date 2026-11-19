package com.example.minigames.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.backgammon.BackgammonIcon
import com.example.minigames.battleship.BattleshipIcon
import com.example.minigames.c4.C4Icon
import com.example.minigames.chess.KNIGHT
import com.example.minigames.g2048.G2048Icon
import com.example.minigames.sudoku.SudokuIcon
import com.example.minigames.mines.MinesIcon
import com.example.minigames.chess.PieceArt
import com.example.minigames.core.Achievements
import com.example.minigames.core.BaseView
import com.example.minigames.core.Button
import com.example.minigames.core.GameId
import com.example.minigames.core.Host
import com.example.minigames.core.I18n
import com.example.minigames.core.Ui
import com.example.minigames.core.W
import com.example.minigames.core.t
import com.example.minigames.core.mixC
import com.example.minigames.core.withA
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val CARD_W = 210f
private const val CARD_H = 84f
private const val CARD_GAP = 10f
private const val CARD_X0 = 129f
private const val CARD_X1 = 351f
private const val CARD_Y0 = 182f

// achievements list
private const val ROW_H = 64f
private const val LIST_T = 178f
private const val LIST_B = 636f

private class Card(
    val id: GameId,
    val titleKey: String,
    val accent: Int,
    val button: Button
)

/**
 * Home screen: a grid of game cards (2 columns, room for 10 games), the achievements list and the global settings
 * (language, sound, vibration, appearance, music).
 */
class HubView(context: Context, host: Host) : BaseView(context, host) {

    private enum class Page { HOME, SETTINGS, ACHIEVEMENTS }

    private var page = Page.HOME

    /** Cards are laid out in a 2-column grid, left to right, top to bottom (index = position in the list). */
    private fun card(index: Int, id: GameId, titleKey: String, accent: Int): Card {
        val cx = if (index % 2 == 0) CARD_X0 else CARD_X1
        val cy = CARD_Y0 + (index / 2) * (CARD_H + CARD_GAP)
        val b = btn({ "" }, cx, cy, CARD_W, CARD_H) {
            Achievements.markOpened(store, id, GameId.values().count { it != GameId.HUB })
            leaveTo(id)
        }
        return Card(id, titleKey, accent, b)
    }

    private val cards = listOf(
        card(0, GameId.FLAPPY, "flappy_title", Color.rgb(79, 195, 247)),
        card(1, GameId.TTT, "ttt_title", Color.rgb(155, 140, 255)),
        card(2, GameId.CHESS, "chess_title", Color.rgb(98, 214, 164)),
        card(3, GameId.CHECKERS, "checkers_title", Color.rgb(255, 159, 90)),
        card(4, GameId.C4, "c4_title", C4Icon.ACCENT),
        card(5, GameId.BACKGAMMON, "bg_title", BackgammonIcon.ACCENT),
        card(6, GameId.G2048, "g48_title", G2048Icon.ACCENT),
        card(7, GameId.SUDOKU, "sd_title", SudokuIcon.ACCENT),
        card(8, GameId.MINES, "mn_title", MinesIcon.ACCENT),
        card(9, GameId.BATTLESHIP, "bs_title", BattleshipIcon.ACCENT)
    )

    private val achBtn = btn(
        { t("achievements") + " " + Achievements.unlockedCount(store) + "/" + Achievements.all.size },
        125f, 676f, 220f, 44f, style = { 1 }
    ) {
        page = Page.ACHIEVEMENTS
        listScroll = 0f
        vel = 0f
    }
    private val settingsBtn = btn({ t("settings") }, 355f, 676f, 220f, 44f, style = { 1 }) { page = Page.SETTINGS }
    private val langQuick = btn({ I18n.codes[I18n.lang] }, W - 50f, 52f, 76f, 44f, style = { 1 }) {
        setLanguage((I18n.lang + 1) % I18n.codes.size)
    }

    // settings page
    private val langBtns: List<Button> = List(I18n.codes.size) { i ->
        btn(
            { I18n.names[i] }, 80f + i * 160f, 262f, 148f, 50f,
            style = { if (I18n.lang == i) 2 else 1 }
        ) { setLanguage(i) }
    }
    private val soundBtn = btn(
        { t("sound_x", if (sfx.enabled) t("on") else t("off")) }, W / 2f, 338f, 320f, 52f,
        style = { if (sfx.enabled) 2 else 3 }
    ) {
        sfx.enabled = !sfx.enabled
        store.soundOn = sfx.enabled
    }
    private val vibroBtn = btn(
        { t("vibration_x", if (store.haptics) t("on") else t("off")) }, W / 2f, 402f, 320f, 52f,
        style = { if (store.haptics) 2 else 3 }
    ) {
        store.haptics = !store.haptics
        haptic()
    }
    private val themeBtn = btn(
        { t("ui_x", when (store.themeMode) { 0 -> t("theme_auto"); 2 -> t("ui_light"); else -> t("ui_dark") }) },
        W / 2f, 466f, 320f, 52f, style = { 1 }
    ) {
        store.themeMode = (store.themeMode + 1) % 3
        host.applyTheme()
    }
    private val musicBtn = btn(
        { t("music_x", if (store.musicOn) t("on") else t("off")) }, W / 2f, 530f, 320f, 52f,
        style = { if (store.musicOn) 2 else 3 }
    ) {
        store.musicOn = !store.musicOn
        host.updateMusic()
    }
    private val backBtn = btn({ t("back") }, W / 2f, 672f, 220f, 44f) { page = Page.HOME }

    private val homeButtons: List<Button> = cards.map { it.button } + listOf(achBtn, settingsBtn, langQuick)
    private val settingsButtons: List<Button> = langBtns + listOf(soundBtn, vibroBtn, themeBtn, musicBtn, backBtn)
    private val achButtons: List<Button> = listOf(backBtn)

    override fun buttons(): List<Button> = when (page) {
        Page.HOME -> homeButtons
        Page.SETTINGS -> settingsButtons
        Page.ACHIEVEMENTS -> achButtons
    }

    // achievements list: drag to scroll, with a little inertia
    private var listScroll = 0f
    private var vel = 0f
    private var dragging = false
    private var lastY = 0f

    private fun maxScroll(): Float = max(0f, Achievements.all.size * ROW_H - (LIST_B - LIST_T))

    override fun onPointerDown(x: Float, y: Float) {
        if (page != Page.ACHIEVEMENTS || y < LIST_T || y > LIST_B) return
        dragging = true
        lastY = y
        vel = 0f
    }

    override fun onPointerMove(x: Float, y: Float) {
        if (!dragging) return
        val d = lastY - y
        lastY = y
        listScroll = (listScroll + d).coerceIn(0f, maxScroll())
        vel = d
    }

    override fun onPointerUp(x: Float, y: Float) {
        dragging = false
    }

    private fun setLanguage(i: Int) {
        I18n.lang = i
        store.lang = i
    }

    override fun onBack(): Boolean {
        if (fading) return true
        if (page != Page.HOME) {
            page = Page.HOME
            return true
        }
        return false
    }

    // ------------------------------- update ---------------------------------

    override fun update() {
        updateClouds(0.12f)
        if (page == Page.ACHIEVEMENTS && !dragging && abs(vel) > 0.05f) {
            listScroll = (listScroll + vel).coerceIn(0f, maxScroll())
            vel *= 0.92f
        }
    }

    // ------------------------------- drawing --------------------------------

    override fun drawScreen(c: Canvas) {
        drawBackdrop(c)
        when (page) {
            Page.HOME -> drawHome(c)
            Page.SETTINGS -> drawSettings(c)
            Page.ACHIEVEMENTS -> drawAchievements(c)
        }
    }

    private fun drawHome(c: Canvas) {
        drawTitle(c, t("hub_title"), W / 2f, 100f, 40f, Ui.TEXT, 330f)
        for (card in cards) drawCard(c, card)
        drawButton(c, achBtn)
        drawButton(c, settingsBtn)
        drawButton(c, langQuick)
    }

    private fun drawAchievements(c: Canvas) {
        val list = Achievements.all
        val got = Achievements.unlockedCount(store)
        drawTitle(c, t("achievements"), W / 2f, 100f, 36f, Ui.TEXT, 400f)
        plainText(c, t("ach_count", got, list.size), W / 2f, 148f, 14f, Ui.MUTED, 0, 400f)
        fillRRect(c, 90f, 158f, 390f, 164f, 3f, Ui.BORDER)
        if (got > 0) fillRRect(c, 90f, 158f, 90f + 300f * got / list.size, 164f, 3f, Ui.OK)

        c.save()
        c.clipRect(0f, LIST_T, W, LIST_B)
        for (i in list.indices) {
            val y = LIST_T - listScroll + i * ROW_H
            if (y > LIST_B || y + ROW_H < LIST_T) continue
            drawAchRow(c, list[i], y, Achievements.isUnlocked(store, list[i].id))
        }
        c.restore()

        val mx = maxScroll()
        if (mx > 0f) {
            val h = (LIST_B - LIST_T)
            val bar = h * h / (list.size * ROW_H)
            val by = LIST_T + (h - bar) * listScroll / mx
            fillRRect(c, 462f, by, 466f, by + bar, 2f, withA(Ui.MUTED, 0.5f))
        }
        drawButton(c, backBtn)
    }

    private fun drawAchRow(c: Canvas, a: com.example.minigames.core.AchDef, y: Float, got: Boolean) {
        val l = 30f
        val r = 452f
        val h = ROW_H - 8f
        fillRRect(c, l, y, r, y + h, 14f, Ui.SURFACE)
        strokeRRect(c, l, y, r, y + h, 14f, if (got) withA(Ui.OK, 0.7f) else Ui.BORDER, 1.5f)
        val mx = l + 30f
        val my = y + h / 2f
        if (got) {
            fillOval(c, mx - 17f, my - 17f, mx + 17f, my + 17f, withA(Ui.OK, 0.18f))
            strokeOval(c, mx - 17f, my - 17f, mx + 17f, my + 17f, Ui.OK, 2f)
            line.color = Ui.OK
            line.strokeWidth = 3f
            c.drawLine(mx - 7f, my + 1f, mx - 2f, my + 7f, line)
            c.drawLine(mx - 2f, my + 7f, mx + 8f, my - 6f, line)
        } else {
            strokeOval(c, mx - 17f, my - 17f, mx + 17f, my + 17f, Ui.BORDER, 2f)
            strokeRRect(c, mx - 5f, my - 10f, mx + 5f, my - 1f, 4f, Ui.MUTED, 2f)      // shackle
            fillRRect(c, mx - 8f, my - 3f, mx + 8f, my + 9f, 3f, Ui.MUTED)             // body
        }
        val left = l + 62f
        val maxText = if (got) r - 12f - left else r - 12f - left - 60f
        val title = a.title()
        val tw = min(textW(title, 15f), maxText)
        text(c, title, left + tw / 2f, y + 22f, 15f, if (got) Ui.TEXT else Ui.MUTED, maxW = maxText, shadow = false)
        plainText(c, a.desc(), left, y + 42f, 12f, Ui.MUTED, -1, r - 12f - left)
        if (!got) plainText(c, t("locked"), r - 12f, y + 22f, 10f, Ui.MUTED, 1, 56f)
    }

    private fun drawSettings(c: Canvas) {
        drawTitle(c, t("settings"), W / 2f, 122f, 40f, Ui.TEXT, 400f)
        plainText(c, t("language"), W / 2f, 222f, 15f, Ui.MUTED, 0, 400f)
        for (b in settingsButtons) drawButton(c, b)
    }

    private fun stats(id: GameId): String = when (id) {
        GameId.FLAPPY -> {
            val best = listOf("EASY", "NORMAL", "HARD").maxOf { store.getI("flappy_best_$it", 0) }
            t("best_x", best)
        }
        GameId.TTT -> short("ttt")
        GameId.CHESS -> short("chess")
        GameId.C4 -> short("c4")
        GameId.BACKGAMMON -> short("bg")
        GameId.G2048 -> t("best_x", store.getI("g48_best", 0))
        GameId.SUDOKU -> t("sd_solved_n", store.stat("sudoku", 0))
        GameId.MINES -> t("mn_cleared_n", store.stat("mines", 0))
        GameId.BATTLESHIP -> t("bs_won_n", store.stat("bs", 0))
        else -> short("checkers")
    }

    private fun short(key: String): String =
        t("stats_short", store.stat(key, 0), store.stat(key, 1), store.stat(key, 2))

    private fun drawCard(c: Canvas, card: Card) {
        val b = card.button
        val press = max(b.down, b.press)
        val sc = 1f - 0.03f * b.down
        val l = -b.w / 2f
        val tp = -b.h / 2f
        val r = b.w / 2f
        val bt = b.h / 2f

        c.save()
        c.translate(b.cx, b.cy)
        c.scale(sc, sc)

        fillRRect(c, l, tp, r, bt, 18f, mixC(Ui.SURFACE, Color.WHITE, 0.05f * press))
        strokeRRect(c, l, tp, r, bt, 18f, mixC(Ui.BORDER, card.accent, 0.35f + 0.65f * press), 1.5f)

        // icon medallion
        val ix = l + 38f
        fillOval(c, ix - 24f, -24f, ix + 24f, 24f, withA(card.accent, 0.14f))
        c.save()
        c.translate(ix, 0f)
        c.scale(0.64f, 0.64f)
        drawIcon(c, card.id, card.accent, 0f, 0f)
        c.restore()

        // texts (left aligned)
        val left = l + 70f
        val maxText = r - 10f - left
        val title = t(card.titleKey)
        val tw = min(textW(title, 17f), maxText)
        text(c, title, left + tw / 2f, -3f, 17f, Ui.TEXT, maxW = maxText, shadow = false)
        plainText(c, stats(card.id), left, 22f, 12f, Ui.MUTED, -1, maxText)
        c.restore()
    }

    private fun drawIcon(c: Canvas, id: GameId, col: Int, x: Float, y: Float) {
        when (id) {
            GameId.FLAPPY -> {
                // minimal bird: round body, wing, beak, eye
                fillOval(c, x - 17f, y - 15f, x + 15f, y + 15f, col)
                fillOval(c, x - 14f, y - 2f, x + 2f, y + 9f, withA(Color.WHITE, 0.55f))
                path.reset()
                path.moveTo(x + 13f, y - 5f)
                path.lineTo(x + 26f, y + 1f)
                path.lineTo(x + 13f, y + 6f)
                path.close()
                fill.shader = null
                fill.color = Ui.TEXT
                c.drawPath(path, fill)
                fillOval(c, x + 4f, y - 9f, x + 10f, y - 3f, Ui.INK)
            }
            GameId.TTT -> {
                line.color = col
                line.strokeWidth = 5f
                c.drawLine(x - 26f, y - 13f, x - 8f, y + 5f, line)
                c.drawLine(x - 8f, y - 13f, x - 26f, y + 5f, line)
                line.color = Ui.TEXT
                c.drawCircle(x + 13f, y + 0f, 11f, line)
            }
            GameId.CHESS -> PieceArt.draw(c, KNIGHT, true, x, y - 1f, 62f)
            GameId.C4 -> C4Icon.draw(c, x, y, col)
            GameId.BACKGAMMON -> BackgammonIcon.draw(c, x, y, col)
            GameId.G2048 -> G2048Icon.draw(c, x, y, col)
            GameId.SUDOKU -> SudokuIcon.draw(c, x, y, col)
            GameId.MINES -> MinesIcon.draw(c, x, y, col)
            GameId.BATTLESHIP -> BattleshipIcon.draw(c, x, y, col)
            else -> {
                disc(c, x - 10f, y + 8f, Ui.TEXT)
                disc(c, x + 10f, y - 8f, col)
            }
        }
    }

    private fun disc(c: Canvas, x: Float, y: Float, color: Int) {
        fillOval(c, x - 16f, y - 16f, x + 16f, y + 16f, color)
        strokeOval(c, x - 10f, y - 10f, x + 10f, y + 10f, withA(Ui.INK, 0.35f), 2f)
    }
}
