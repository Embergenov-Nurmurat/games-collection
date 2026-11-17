package com.example.minigames.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.example.minigames.chess.KNIGHT
import com.example.minigames.chess.PieceArt
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
import kotlin.math.max
import kotlin.math.min

private class Card(
    val id: GameId,
    val titleKey: String,
    val accent: Int,
    val button: Button
)

/** Home screen: one card per game, plus the global settings (language, sound, vibration). */
class HubView(context: Context, host: Host) : BaseView(context, host) {

    private enum class Page { HOME, SETTINGS }

    private var page = Page.HOME

    private fun card(id: GameId, titleKey: String, cy: Float, accent: Int): Card {
        val b = btn({ "" }, W / 2f, cy, 400f, 92f) { leaveTo(id) }
        return Card(id, titleKey, accent, b)
    }

    private val cards = listOf(
        card(GameId.FLAPPY, "flappy_title", 232f, Color.rgb(79, 195, 247)),
        card(GameId.TTT, "ttt_title", 337f, Color.rgb(155, 140, 255)),
        card(GameId.CHESS, "chess_title", 442f, Color.rgb(98, 214, 164)),
        card(GameId.CHECKERS, "checkers_title", 547f, Color.rgb(255, 159, 90))
    )

    private val settingsBtn = btn({ t("settings") }, W / 2f, 650f, 240f, 50f, style = { 1 }) { page = Page.SETTINGS }
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
        { t("sound_x", if (sfx.enabled) t("on") else t("off")) }, W / 2f, 352f, 320f, 54f,
        style = { if (sfx.enabled) 2 else 3 }
    ) {
        sfx.enabled = !sfx.enabled
        store.soundOn = sfx.enabled
    }
    private val vibroBtn = btn(
        { t("vibration_x", if (store.haptics) t("on") else t("off")) }, W / 2f, 422f, 320f, 54f,
        style = { if (store.haptics) 2 else 3 }
    ) {
        store.haptics = !store.haptics
        haptic()
    }
    private val backBtn = btn({ t("back") }, W / 2f, 520f, 220f, 50f) { page = Page.HOME }

    private val homeButtons: List<Button> = cards.map { it.button } + listOf(settingsBtn, langQuick)
    private val settingsButtons: List<Button> = langBtns + listOf(soundBtn, vibroBtn, backBtn)

    override fun buttons(): List<Button> = if (page == Page.HOME) homeButtons else settingsButtons

    private fun setLanguage(i: Int) {
        I18n.lang = i
        store.lang = i
    }

    override fun onBack(): Boolean {
        if (fading) return true
        if (page == Page.SETTINGS) {
            page = Page.HOME
            return true
        }
        return false
    }

    // ------------------------------- update ---------------------------------

    override fun update() {
        updateClouds(0.12f)
    }

    // ------------------------------- drawing --------------------------------

    override fun drawScreen(c: Canvas) {
        drawBackdrop(c)
        if (page == Page.HOME) drawHome(c) else drawSettings(c)
    }

    private fun drawHome(c: Canvas) {
        drawTitle(c, t("hub_title"), W / 2f, 122f, 44f, Ui.TEXT, 400f)
        for (card in cards) drawCard(c, card)
        drawButton(c, settingsBtn)
        drawButton(c, langQuick)
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
        val ix = l + 54f
        fillOval(c, ix - 32f, -32f, ix + 32f, 32f, withA(card.accent, 0.14f))
        drawIcon(c, card.id, card.accent, ix, 0f)

        // texts (left aligned)
        val left = l + 100f
        val title = t(card.titleKey)
        val tw = min(textW(title, 26f), 250f)
        text(c, title, left + tw / 2f, -2f, 26f, Ui.TEXT, maxW = 250f, shadow = false)
        plainText(c, stats(card.id), left, 26f, 15f, Ui.MUTED, -1, 250f)

        // chevron
        line.color = Ui.MUTED
        line.strokeWidth = 2.5f
        c.drawLine(r - 32f, -8f, r - 24f, 0f, line)
        c.drawLine(r - 24f, 0f, r - 32f, 8f, line)
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
