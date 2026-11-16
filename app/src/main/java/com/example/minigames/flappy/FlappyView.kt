package com.example.minigames.flappy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.KeyEvent
import com.example.minigames.core.BaseView
import com.example.minigames.core.Button
import com.example.minigames.core.GameId
import com.example.minigames.core.Host
import com.example.minigames.core.OUTLINE
import com.example.minigames.core.Particle
import com.example.minigames.core.RAD2DEG
import com.example.minigames.core.Sfx
import com.example.minigames.core.Ui
import com.example.minigames.core.W
import com.example.minigames.core.H
import com.example.minigames.core.clampF
import com.example.minigames.core.easeOutBack
import com.example.minigames.core.lerp
import com.example.minigames.core.mixC
import com.example.minigames.core.rnd
import com.example.minigames.core.t
import com.example.minigames.core.withA
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val SUN_X = W - 105f
private const val SUN_Y = 120f
private const val PAUSE_X = W - 44f
private const val PAUSE_Y = 48f

private val GOLD = Color.rgb(255, 205, 50)
private val SHIELD_BLUE = Color.rgb(120, 200, 255)

private fun circleHitsRect(
    cx: Float, cy: Float, r: Float,
    rx: Float, ry: Float, rw: Float, rh: Float
): Boolean {
    val nx = clampF(cx, rx, rx + rw)
    val ny = clampF(cy, ry, ry + rh)
    val dx = cx - nx
    val dy = cy - ny
    return dx * dx + dy * dy < r * r
}

/** Flappy Bird: its own menu, settings (theme, shields), skins and records. */
class FlappyView(context: Context, host: Host) : BaseView(context, host) {

    private enum class State { MENU, SETTINGS, READY, PLAYING, PAUSED, COUNTDOWN, DYING, GAME_OVER }

    private var state = State.MENU

    // bird
    private var birdX = BIRD_X
    private var birdY = H * 0.45f
    private var vy = 0f
    private var rot = 0f
    private var wing = 0f
    private var flapBoost = 0

    // world
    private val pipes = ArrayList<Pipe>()
    private val stars = ArrayList<Star>()
    private var groundOff = 0f
    private var hill1 = 0f
    private var hill2 = 0f
    private var scrollV = 2f

    // run state
    private val diffs = Difficulty.values()
    private var difficulty = diffs[store.getI("flappy_diff", 1).coerceIn(0, diffs.size - 1)]
    private var passed = 0            // pipes passed (drives difficulty)
    private var coins = 0             // coins collected this run
    private var score = 0             // passed + coins
    private var displayScore = 0
    private var best = bestOf(difficulty)
    private var newBest = false
    private var scorePop = 0f
    private var shield = false
    private var invuln = 0
    private var unlockedSkin: Skin? = null

    // persistent stats (cached so we do not hit SharedPreferences every frame)
    private val skins = Skin.values()
    private var maxBest = diffs.maxOf { bestOf(it) }
    private var totalCoins = store.totalCoins
    private var skinIdx = 0

    // fx
    private var goTimer = 0
    private var cdTimer = 0
    private var nightMix = 0f

    // cached shaders
    private var shaderKey = -1
    private var skyShader: Shader? = null
    private var groundShader: Shader? = null
    private var sunGlow: Shader? = null
    private var moonGlow: Shader? = null
    private var birdShader: Shader? = null
    private var birdShaderSkin = -1

    private fun bestOf(d: Difficulty) = store.getI("flappy_best_" + d.name, 0)

    // settings
    private val shieldsOn: Boolean get() = store.getB("flappy_shields", false)
    private var themeOpt = store.getI("flappy_theme", 0).coerceIn(0, 2)

    private fun themeLabel(): String = when (themeOpt) {
        1 -> t("theme_day")
        2 -> t("theme_night")
        else -> t("theme_auto")
    }

    // ------------------------------- buttons --------------------------------

    private val menuButtons: List<Button> = listOf(
        btn({ t("play") }, W / 2f, 330f, 260f, 50f) { transition { startReady() } },
        btn({ t("mode_x", t(difficulty.key)) }, W / 2f, 390f, 260f, 50f, style = { 1 }) { cycleDifficulty() },
        btn({ t("skin_x", t(skins[skinIdx].key)) }, W / 2f, 450f, 260f, 50f, style = { 2 }) { cycleSkin() },
        btn({ t("settings") }, W / 2f, 510f, 260f, 50f, style = { 1 }) { state = State.SETTINGS },
        btn({ t("all_games") }, W / 2f, 570f, 260f, 46f, style = { 1 }) { leaveTo(GameId.HUB) }
    )
    private val settingsButtons: List<Button> = listOf(
        btn({ t("theme_x", themeLabel()) }, W / 2f, 290f, 320f, 54f, style = { 1 }) { cycleTheme() },
        btn(
            { t("shields_x", if (shieldsOn) t("on") else t("off")) }, W / 2f, 360f, 320f, 54f,
            style = { if (shieldsOn) 2 else 3 }
        ) { store.putB("flappy_shields", !shieldsOn) },
        btn({ t("back") }, W / 2f, 450f, 220f, 50f) { state = State.MENU }
    )
    private val overButtons: List<Button> = listOf(
        btn({ t("retry") }, 150f, 545f, 170f, 56f) { transition { startReady() } },
        btn({ t("menu") }, 330f, 545f, 170f, 56f, style = { 1 }) { transition { startMenu() } }
    )
    private val pauseButtons: List<Button> = listOf(
        btn({ t("resume") }, W / 2f, 340f, 260f, 54f) { resumeGame() },
        btn({ t("menu") }, W / 2f, 415f, 260f, 54f, style = { 1 }) { bankProgress(); transition { startMenu() } }
    )

    init {
        skinIdx = store.getI("flappy_skin", 0).coerceIn(0, skins.size - 1)
        if (!skinUnlocked(skinIdx)) skinIdx = 0
        startMenu()
    }

    override fun buttons(): List<Button> = when (state) {
        State.MENU -> menuButtons
        State.SETTINGS -> settingsButtons
        State.PAUSED -> pauseButtons
        State.GAME_OVER -> if (goTimer > 50) overButtons else emptyList()
        else -> emptyList()
    }

    override fun onViewportChanged() {
        stars.clear()
        repeat(70) {
            stars.add(Star(rnd(vl, vr), rnd(vt, GROUND_Y - 120f), rnd(0f, 6.28f), rnd(2f, 4f)))
        }
    }

    // ------------------------------ lifecycle -------------------------------

    override fun onHostPause() {
        super.onHostPause()
        if (state == State.PLAYING || state == State.COUNTDOWN) state = State.PAUSED
    }

    override fun onBack(): Boolean {
        if (fading) return true
        when (state) {
            State.PLAYING, State.COUNTDOWN -> state = State.PAUSED
            State.PAUSED -> resumeGame()
            State.READY -> transition { startMenu() }
            State.GAME_OVER -> if (goTimer > 50) transition { startMenu() }
            State.DYING -> {}
            State.SETTINGS -> state = State.MENU
            State.MENU -> leaveTo(GameId.HUB)
        }
        return true
    }

    // ------------------------------ game flow -------------------------------

    private fun resetWorld() {
        birdX = BIRD_X
        birdY = H * 0.45f
        vy = 0f
        rot = 0f
        flapBoost = 0
        pipes.clear()
        particles.clear()
        passed = 0
        coins = 0
        score = 0
        displayScore = 0
        newBest = false
        unlockedSkin = null
        shield = false
        invuln = 0
        shake = 0f
        flash = 0f
        goTimer = 0
        cdTimer = 0
        scorePop = 0f
        best = bestOf(difficulty)
    }

    private fun startMenu() {
        resetWorld()
        state = State.MENU
    }

    private fun startReady() {
        resetWorld()
        state = State.READY
    }

    private fun resumeGame() {
        cdTimer = 0
        state = State.COUNTDOWN
    }

    private fun cycleDifficulty() {
        difficulty = diffs[(difficulty.ordinal + 1) % diffs.size]
        store.putI("flappy_diff", difficulty.ordinal)
        best = bestOf(difficulty)
    }

    private fun cycleTheme() {
        themeOpt = (themeOpt + 1) % 3
        store.putI("flappy_theme", themeOpt)
    }

    private fun skinUnlocked(i: Int) = skins[i].unlockAt <= maxBest

    private fun cycleSkin() {
        for (i in 1..skins.size) {
            val idx = (skinIdx + i) % skins.size
            if (skinUnlocked(idx)) {
                skinIdx = idx
                store.putI("flappy_skin", idx)
                break
            }
        }
    }

    /** 0 = auto (day/night switches every 20 points), 1 = day, 2 = night */
    private fun nightTarget(): Float = when (themeOpt) {
        1 -> 0f
        2 -> 1f
        else -> if (state == State.MENU || state == State.SETTINGS) 0f else if ((passed / 20) % 2 == 1) 1f else 0f
    }

    private fun pipeSpeed() =
        difficulty.baseSpeed + min(passed * difficulty.speedStep, difficulty.maxBonus)

    // ------------------------------ effects ---------------------------------

    private fun flap() {
        vy = FLAP_V
        flapBoost = 12
        sfx.play(Sfx.Kind.FLAP)
        repeat(4) {
            particles.add(
                Particle(
                    birdX - 14f, birdY + 6f, -rnd(0.6f, 2.2f), rnd(0.2f, 1.6f),
                    26, 26, if (it % 2 == 0) Color.WHITE else Color.rgb(255, 235, 140),
                    rnd(3f, 6f), 0.05f
                )
            )
        }
    }

    private fun sparkle() {
        emit(birdX + 18f, birdY, 8, 0.8f, 2.6f, 24, 3f, 5f, 0f, Color.rgb(255, 230, 90), Color.WHITE)
    }

    private fun burst() {
        emit(birdX, birdY, 26, 2f, 7f, 42, 4f, 8f, 0.25f, Color.rgb(255, 225, 80), Color.rgb(255, 140, 40), Color.WHITE)
    }

    private fun collect(kind: Int, x: Float, y: Float) {
        if (kind == 1) {
            coins++
            score = passed + coins
            scorePop = 1f
            sfx.play(Sfx.Kind.COIN)
            emit(x, y, 10, 1f, 3f, 24, 3f, 5f, 0f, GOLD, Color.WHITE)
        } else {
            shield = true
            sfx.play(Sfx.Kind.SHIELD)
            emit(x, y, 14, 1f, 3.5f, 28, 3f, 6f, 0f, SHIELD_BLUE, Color.WHITE)
        }
    }

    private fun breakShield() {
        shield = false
        invuln = 75
        shake = 6f
        sfx.play(Sfx.Kind.POP)
        haptic()
        emit(birdX, birdY, 22, 2f, 5f, 30, 3f, 6f, 0.05f, SHIELD_BLUE, Color.WHITE)
    }

    private fun hit(ground: Boolean) {
        state = State.DYING
        flash = 1f
        shake = 14f
        burst()
        sfx.play(Sfx.Kind.HIT)
        haptic(true)
        if (ground) {
            birdY = GROUND_Y - BIRD_R
            endGame()
        } else {
            vy = -3f
        }
    }

    /** Saves the best score and coins of an unfinished run (used when leaving from the pause menu). */
    private fun bankProgress() {
        if (score > best) {
            best = score
            store.putI("flappy_best_" + difficulty.name, best)
        }
        if (coins > 0) store.totalCoins = store.totalCoins + coins
        totalCoins = store.totalCoins
        maxBest = diffs.maxOf { bestOf(it) }
        coins = 0
    }

    private fun endGame() {
        state = State.GAME_OVER
        goTimer = 0
        val before = maxBest
        if (score > best) {
            best = score
            newBest = true
            store.putI("flappy_best_" + difficulty.name, best)
        }
        if (coins > 0) store.totalCoins = store.totalCoins + coins
        totalCoins = store.totalCoins
        maxBest = diffs.maxOf { bestOf(it) }
        unlockedSkin = skins.lastOrNull { it.unlockAt > before && it.unlockAt <= maxBest }
    }

    private fun addPipe() {
        val gap = difficulty.gap
        val lo = 80f
        val hi = GROUND_Y - gap - 80f
        val moving = passed >= difficulty.movingFrom && Random.nextFloat() < 0.6f
        val amp = if (moving) 28f + min(passed, 40) * 0.5f else 0f
        val prev = if (pipes.isEmpty()) (lo + hi) / 2f else pipes[pipes.size - 1].baseTop
        val minTop = max(lo + amp, prev - difficulty.maxShift)
        val maxTop = min(hi - amp, prev + difficulty.maxShift)
        val top = if (maxTop > minTop) rnd(minTop, maxTop) else clampF(prev, lo + amp, hi - amp)
        val roll = Random.nextFloat()
        val item = when {
            roll < 0.08f && passed >= 3 && !shield && shieldsOn -> 2
            roll < 0.40f -> 1
            else -> 0
        }
        pipes.add(Pipe(vr + 20f, top, amp, rnd(0f, 6.28f), item))
    }

    private fun hitsPipe(p: Pipe): Boolean {
        val capX = p.x - (CAP_W - PIPE_W) / 2f
        val gt = p.gapTop
        val gb = gt + difficulty.gap
        return circleHitsRect(birdX, birdY, BIRD_R, p.x, -1000f, PIPE_W, gt + 1000f) ||
                circleHitsRect(birdX, birdY, BIRD_R, p.x, gb, PIPE_W, 1000f) ||
                circleHitsRect(birdX, birdY, BIRD_R, capX, gt - CAP_H, CAP_W, CAP_H) ||
                circleHitsRect(birdX, birdY, BIRD_R, capX, gb, CAP_W, CAP_H)
    }

    // ------------------------------- input ----------------------------------

    override fun onKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_W) {
            tap()
            return true
        }
        return false
    }

    override fun onPointerDown(x: Float, y: Float) {
        if (state == State.PLAYING) {
            val dx = x - PAUSE_X
            val dy = y - PAUSE_Y
            if (dx * dx + dy * dy < 30f * 30f) {
                state = State.PAUSED
                sfx.play(Sfx.Kind.CLICK)
                return
            }
        }
        tap()
    }

    /** A tap on the playfield (not on a button). */
    private fun tap() {
        when (state) {
            State.READY -> {
                state = State.PLAYING
                flap()
            }
            State.PLAYING -> flap()
            else -> {}
        }
    }

    // ------------------------------- update ---------------------------------

    override fun update() {
        val tm = tick.toFloat()
        nightMix += (nightTarget() - nightMix) * 0.06f
        scorePop *= 0.88f

        if (state == State.PAUSED) return

        if (state == State.COUNTDOWN) {
            cdTimer++
            if (cdTimer % 40 == 1) sfx.play(Sfx.Kind.CLICK)
            if (cdTimer >= 120) state = State.PLAYING
            return
        }

        // scrolling speed (eased)
        val scrollTarget = when (state) {
            State.MENU, State.SETTINGS, State.READY -> 2f
            State.PLAYING -> pipeSpeed()
            else -> 0f
        }
        scrollV += (scrollTarget - scrollV) * 0.12f
        groundOff += scrollV
        hill1 += scrollV * 0.2f
        hill2 += scrollV * 0.45f
        updateClouds(scrollV * 0.12f)

        when (state) {
            State.MENU -> {
                birdX = W / 2f
                birdY = 262f + sin(tm * 0.07f) * 10f
                rot = sin(tm * 0.07f + 1f) * 0.12f
                wing += 0.3f
            }
            State.SETTINGS -> {
                birdX = W / 2f
                birdY = 205f + sin(tm * 0.07f) * 8f
                rot = sin(tm * 0.07f + 1f) * 0.12f
                wing += 0.3f
            }
            State.READY -> {
                birdX = BIRD_X
                birdY = H * 0.45f + sin(tm * 0.12f) * 8f
                rot = 0f
                wing += 0.28f
            }
            State.PLAYING -> updatePlaying()
            State.DYING -> {
                vy = min(vy + GRAVITY, MAX_FALL)
                birdY += vy
                rot = lerp(rot, 1.5f, 0.12f)
                if (birdY + BIRD_R >= GROUND_Y) {
                    birdY = GROUND_Y - BIRD_R
                    shake = 7f
                    endGame()
                }
            }
            State.GAME_OVER -> {
                goTimer++
                rot = lerp(rot, 1.5f, 0.2f)
                if (goTimer > 40 && goTimer % 2 == 0 && displayScore < score) displayScore++
            }
            else -> {}
        }
    }

    private fun updatePlaying() {
        vy = min(vy + GRAVITY, MAX_FALL)
        birdY += vy
        rot = lerp(rot, clampF(vy * 0.09f, -0.45f, 1.4f), 0.25f)
        wing += if (flapBoost > 0) 0.9f else if (vy < 2f) 0.35f else 0.1f
        if (flapBoost > 0) flapBoost--
        if (invuln > 0) invuln--

        if (birdY - BIRD_R < 0f) {
            birdY = BIRD_R
            if (vy < 0f) vy = 0f
        }

        val sp = pipeSpeed()
        if (pipes.isEmpty() || pipes[pipes.size - 1].x < vr - difficulty.spacing) addPipe()

        for (p in pipes) {
            p.x -= sp
            p.age += 1f
            if (p.amp > 0f) p.gapTop = p.baseTop + sin(p.age * 0.045f + p.phase) * p.amp

            if (!p.scored && p.x + PIPE_W / 2f < birdX) {
                p.scored = true
                passed++
                score = passed + coins
                scorePop = 1f
                sfx.play(Sfx.Kind.SCORE)
                sparkle()
            }

            if (p.item != 0 && !p.itemTaken) {
                val ix = p.x + PIPE_W / 2f
                val iy = p.gapTop + difficulty.gap / 2f
                val dx = birdX - ix
                val dy = birdY - iy
                val rr = BIRD_R + 13f
                if (dx * dx + dy * dy < rr * rr) {
                    p.itemTaken = true
                    collect(p.item, ix, iy)
                }
            }
        }
        pipes.removeAll { it.x < vl - CAP_W }

        if (birdY + BIRD_R >= GROUND_Y) {
            hit(true)
        } else if (invuln <= 0 && pipes.any { hitsPipe(it) }) {
            if (shield) breakShield() else hit(false)
        }
    }

    // ------------------------------- drawing --------------------------------

    private fun ensureShaders() {
        val key = (nightMix * 40f).roundToInt()
        if (key == shaderKey) return
        shaderKey = key
        val nm = key / 40f
        skyShader = LinearGradient(
            0f, 0f, 0f, GROUND_Y,
            mixC(Color.rgb(100, 166, 212), Color.rgb(11, 16, 38), nm),
            mixC(Color.rgb(228, 237, 245), Color.rgb(40, 54, 104), nm),
            Shader.TileMode.CLAMP
        )
        groundShader = LinearGradient(
            0f, GROUND_Y, 0f, H,
            mixC(Color.rgb(206, 198, 178), Color.rgb(76, 74, 104), nm * 0.7f),
            mixC(Color.rgb(188, 180, 160), Color.rgb(52, 50, 82), nm * 0.7f),
            Shader.TileMode.CLAMP
        )
        sunGlow = RadialGradient(
            SUN_X, SUN_Y, 95f,
            Color.argb(((1f - nm) * 150f).roundToInt(), 255, 240, 150),
            Color.argb(0, 255, 240, 150),
            Shader.TileMode.CLAMP
        )
        moonGlow = RadialGradient(
            SUN_X, SUN_Y, 80f,
            Color.argb((nm * 90f).roundToInt(), 200, 215, 255),
            Color.argb(0, 200, 215, 255),
            Shader.TileMode.CLAMP
        )
    }

    override fun drawScreen(c: Canvas) {
        ensureShaders()
        val nm = nightMix
        drawSky(c)
        for (cl in clouds) drawCloud(c, cl, nm)
        drawHills(c, hill1, GROUND_Y - 70f, 22f, 0.011f, mixC(Color.rgb(172, 204, 204), Color.rgb(32, 54, 88), nm))
        drawHills(c, hill2, GROUND_Y - 32f, 16f, 0.02f, mixC(Color.rgb(138, 180, 178), Color.rgb(24, 44, 72), nm))
        drawPipes(c)
        drawGround(c)
        drawParticles(c)

        val dead = state == State.DYING || state == State.GAME_OVER
        val blink = state == State.PLAYING && invuln > 0 && (invuln / 4) % 2 == 0
        if (!blink) drawBird(c, birdX, birdY, rot, wing, dead)
        if (shield && !dead) drawShieldBubble(c)

        drawHud(c)
    }

    // --- scenery ---

    private fun drawSky(c: Canvas) {
        val nm = nightMix
        fill.shader = skyShader
        fill.color = Color.WHITE
        c.drawRect(vl - 60f, vt - 60f, vr + 60f, GROUND_Y + 20f, fill)
        fill.shader = null

        if (nm > 0.02f) {
            for (s in stars) {
                val a = nm * (0.5f + 0.5f * sin(tick * 0.05f + s.phase))
                fillRect(c, s.x, s.y, s.x + s.size, s.y + s.size, withA(Color.WHITE, a))
            }
        }

        if (nm < 0.98f) {
            fill.shader = sunGlow
            fill.color = Color.WHITE
            c.drawCircle(SUN_X, SUN_Y, 95f, fill)
            fill.shader = null
            fillOval(c, SUN_X - 34f, SUN_Y - 34f, SUN_X + 34f, SUN_Y + 34f, withA(Color.rgb(255, 232, 120), 1f - nm))
        }
        if (nm > 0.02f) {
            fill.shader = moonGlow
            fill.color = Color.WHITE
            c.drawCircle(SUN_X, SUN_Y, 80f, fill)
            fill.shader = null
            fillOval(c, SUN_X - 30f, SUN_Y - 30f, SUN_X + 30f, SUN_Y + 30f, withA(Color.rgb(242, 242, 226), nm))
            val crater = withA(Color.rgb(212, 212, 196), nm)
            fillOval(c, SUN_X - 14f, SUN_Y - 12f, SUN_X, SUN_Y + 2f, crater)
            fillOval(c, SUN_X + 6f, SUN_Y + 6f, SUN_X + 16f, SUN_Y + 16f, crater)
            fillOval(c, SUN_X - 4f, SUN_Y + 12f, SUN_X + 3f, SUN_Y + 19f, crater)
        }
    }

    private fun drawHills(c: Canvas, offset: Float, base: Float, amp: Float, freq: Float, color: Int) {
        path.reset()
        path.moveTo(vl - 10f, GROUND_Y + 5f)
        var x = vl - 10f
        while (x <= vr + 10f) {
            val y = base + sin((x + offset) * freq) * amp + sin((x + offset) * freq * 2.3f + 1f) * amp * 0.4f
            path.lineTo(x, y)
            x += 6f
        }
        path.lineTo(vr + 10f, GROUND_Y + 5f)
        path.close()
        fill.shader = null
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun drawPipes(c: Canvas) {
        val nm = nightMix
        val light = mixC(Color.rgb(120, 200, 164), Color.rgb(70, 120, 100), nm * 0.8f)
        val dark = mixC(Color.rgb(72, 152, 122), Color.rgb(40, 84, 76), nm * 0.8f)
        val mLight = mixC(Color.rgb(136, 176, 236), Color.rgb(60, 110, 130), nm * 0.8f)
        val mDark = mixC(Color.rgb(90, 128, 200), Color.rgb(36, 76, 100), nm * 0.8f)
        val top = vt - 20f

        for (p in pipes) {
            val moving = p.amp > 0f
            val l = if (moving) mLight else light
            val d = if (moving) mDark else dark
            val gt = p.gapTop
            val gb = gt + difficulty.gap
            val capX = p.x - (CAP_W - PIPE_W) / 2f
            pipePart(c, p.x, top, PIPE_W, gt - CAP_H - top, 0f, l, d)
            pipePart(c, p.x, gb + CAP_H, PIPE_W, GROUND_Y + 6f - (gb + CAP_H), 0f, l, d)
            pipePart(c, capX, gt - CAP_H, CAP_W, CAP_H, 8f, l, d)
            pipePart(c, capX, gb, CAP_W, CAP_H, 8f, l, d)
            drawItem(c, p)
        }
    }

    private fun pipePart(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, light: Int, dark: Int) {
        if (h <= 0f) return
        fillRRect(c, x, y, x + w, y + h, r, dark)
        fillRRect(c, x, y, x + w * 0.72f, y + h, r, light)
        val inset = if (r > 0f) 4f else 0f
        fillRRect(c, x + 6f, y + inset, x + 11f, y + h - inset, 2.5f, withA(Color.WHITE, 0.18f))
    }

    private fun drawItem(c: Canvas, p: Pipe) {
        if (p.item == 0 || p.itemTaken) return
        val ix = p.x + PIPE_W / 2f
        val iy = p.gapTop + difficulty.gap / 2f + sin(tick * 0.1f + p.phase) * 3f
        if (p.item == 1) {
            // spinning coin
            val k = 0.25f + 0.75f * abs(cos(tick * 0.07f + p.phase))
            val rw = 11f * k
            fillOval(c, ix - rw, iy - 11f, ix + rw, iy + 11f, GOLD)
            strokeOval(c, ix - rw * 0.55f, iy - 6.5f, ix + rw * 0.55f, iy + 6.5f, Color.rgb(220, 150, 20), 2f)
        } else {
            // shield bubble pickup
            val r = 14f + sin(tick * 0.12f) * 1.2f
            fillOval(c, ix - r, iy - r, ix + r, iy + r, withA(SHIELD_BLUE, 0.55f))
            strokeOval(c, ix - r, iy - r, ix + r, iy + r, Color.rgb(40, 110, 200), 2.5f)
            fillOval(c, ix - r * 0.6f, iy - r * 0.6f, ix - r * 0.1f, iy - r * 0.25f, withA(Color.WHITE, 0.85f))
        }
    }

    private fun drawGround(c: Canvas) {
        val nm = nightMix
        val gy = GROUND_Y
        fill.shader = groundShader
        fill.color = Color.WHITE
        c.drawRect(vl - 60f, gy, vr + 60f, vb + 60f, fill)
        fill.shader = null

        // dirt specks (positions are tied to world scroll so nothing pops)
        fill.color = withA(OUTLINE, 0.08f)
        var k = floor((groundOff + vl) / 40f).toInt() - 1
        while (k * 40f - groundOff < vr) {
            val x = k * 40f - groundOff
            val y = gy + 40f + (k and 3) * 12f
            c.drawRect(x, y, x + 12f, y + 4f, fill)
            k++
        }

        // thin edge line on top of the ground
        fillRect(c, vl - 60f, gy, vr + 60f, gy + 5f, mixC(Color.rgb(134, 170, 150), Color.rgb(50, 110, 70), nm))
    }

    // --- bird ---

    private fun drawBird(c: Canvas, cx: Float, cy: Float, rotation: Float, wingPhase: Float, dead: Boolean) {
        val sk = skins[skinIdx]
        if (birdShaderSkin != skinIdx || birdShader == null) {
            birdShader = LinearGradient(0f, -15f, 0f, 15f, sk.bodyTop, sk.bodyBottom, Shader.TileMode.CLAMP)
            birdShaderSkin = skinIdx
        }

        c.save()
        c.translate(cx, cy)
        c.rotate(rotation * RAD2DEG)

        // tail
        path.reset()
        path.moveTo(-17f, -2f)
        path.lineTo(-29f, -7f)
        path.lineTo(-27f, 2f)
        path.lineTo(-29f, 9f)
        path.lineTo(-17f, 6f)
        path.close()
        fill.shader = null
        fill.color = sk.tail
        c.drawPath(path, fill)

        // body + belly
        fill.shader = birdShader
        fill.color = Color.WHITE
        rf.set(-20f, -15f, 20f, 15f)
        c.drawOval(rf, fill)
        fill.shader = null
        fillOval(c, -9f, 3.5f, 14f, 14.5f, sk.belly)

        // wing
        c.save()
        c.translate(-6f, 3f)
        c.rotate((if (dead) 0.9f else sin(wingPhase) * 0.85f - 0.1f) * RAD2DEG)
        fillOval(c, -19f, -5.5f, 4f, 5.5f, Color.rgb(255, 250, 235))
        c.restore()

        // eye
        if (dead) {
            line.color = OUTLINE
            line.strokeWidth = 2.4f
            c.drawLine(4f, -10f, 12f, -2f, line)
            c.drawLine(12f, -10f, 4f, -2f, line)
        } else {
            fillOval(c, 1f, -13f, 15f, 1f, Color.WHITE)
            fillOval(c, 8f, -9.5f, 14f, -3.5f, OUTLINE)
        }

        // beak
        fillRRect(c, 11f, 5f, 26f, 11f, 3f, sk.beakLower)
        fillRRect(c, 11f, -1.5f, 29f, 5.5f, 3f, sk.beakUpper)

        c.restore()
    }

    private fun drawShieldBubble(c: Canvas) {
        val r = 29f + sin(tick * 0.15f) * 1.5f
        fillOval(c, birdX - r, birdY - r, birdX + r, birdY + r, withA(SHIELD_BLUE, 0.22f))
        strokeOval(c, birdX - r, birdY - r, birdX + r, birdY + r, withA(Color.WHITE, 0.9f), 3f)
        fillOval(c, birdX - r * 0.62f, birdY - r * 0.72f, birdX - r * 0.22f, birdY - r * 0.42f, withA(Color.WHITE, 0.7f))
    }

    // --- HUD ---

    private fun drawScore(c: Canvas) {
        val s = 1f + 0.4f * scorePop
        c.save()
        c.translate(W / 2f, 100f)
        c.scale(s, s)
        text(c, score.toString(), 0f, 0f, 64f, Color.WHITE, 9f)
        c.restore()
    }

    private fun drawCoinCounter(c: Canvas) {
        if (coins <= 0) return
        fillOval(c, 24f, 33f, 46f, 55f, GOLD)
        text(c, "x$coins", 74f, 52f, 22f, Color.WHITE)
    }

    private fun drawPauseIcon(c: Canvas) {
        fillOval(c, PAUSE_X - 24f, PAUSE_Y - 24f, PAUSE_X + 24f, PAUSE_Y + 24f, withA(Color.BLACK, 0.28f))
        fillRRect(c, PAUSE_X - 9f, PAUSE_Y - 10f, PAUSE_X - 3f, PAUSE_Y + 10f, 2f, Color.WHITE)
        fillRRect(c, PAUSE_X + 3f, PAUSE_Y - 10f, PAUSE_X + 9f, PAUSE_Y + 10f, 2f, Color.WHITE)
    }

    private fun nextSkinHint(): String {
        for (s in skins) if (s.unlockAt > maxBest) return t("next_skin", s.unlockAt)
        return t("all_skins")
    }

    private fun drawHud(c: Canvas) {
        val tm = tick.toFloat()
        when (state) {
            State.MENU -> {
                text(c, "FLAPPY", W / 2f - 20f, 128f, 56f, Color.WHITE, maxW = 360f)
                text(c, "BIRD", W / 2f - 20f, 190f, 56f, Ui.ACCENT, maxW = 360f)
                for (b in menuButtons) drawButton(c, b)
                text(c, t("best_coins", best, totalCoins), W / 2f, GROUND_Y + 46f, 16f, Ui.INK, maxW = 440f, shadow = false)
                text(c, nextSkinHint(), W / 2f, GROUND_Y + 74f, 14f, Color.rgb(78, 88, 108), maxW = 440f, shadow = false)
            }
            State.SETTINGS -> {
                text(c, t("settings"), W / 2f, 130f, 42f, Color.WHITE, maxW = 400f)
                for (b in settingsButtons) drawButton(c, b)
                text(c, t("shields_hint"), W / 2f, 505f, 14f, Color.rgb(78, 88, 108), maxW = 440f, shadow = false)
            }
            State.READY -> {
                drawScore(c)
                val pulse = 1f + 0.04f * sin(tm * 0.15f)
                c.save()
                c.translate(W / 2f, 215f)
                c.scale(pulse, pulse)
                text(c, t("get_ready"), 0f, 0f, 44f, Color.WHITE, maxW = 420f)
                c.restore()
                text(c, t("mode_x", t(difficulty.key)), W / 2f, 262f, 16f, Color.WHITE, maxW = 400f)
                val a = 0.55f + 0.45f * sin(tm * 0.1f)
                c.saveLayerAlpha(vl, vt, vr, vb, (a * 255f).roundToInt())
                text(c, t("tap_to_flap"), W / 2f, 480f, 20f, Color.WHITE, maxW = 420f)
                c.restore()
            }
            State.PLAYING -> {
                drawScore(c)
                drawCoinCounter(c)
                drawPauseIcon(c)
            }
            State.DYING -> {
                drawScore(c)
                drawCoinCounter(c)
            }
            State.COUNTDOWN -> {
                drawScore(c)
                drawCoinCounter(c)
                val n = 3 - cdTimer / 40
                val k = 1f - (cdTimer % 40) / 40f
                c.save()
                c.translate(W / 2f, 330f)
                c.scale(1f + 0.5f * k, 1f + 0.5f * k)
                text(c, n.coerceIn(1, 3).toString(), 0f, 0f, 110f, Color.WHITE, 14f)
                c.restore()
                text(c, t("get_ready"), W / 2f, 440f, 22f, Color.WHITE, maxW = 420f)
            }
            State.PAUSED -> {
                drawScore(c)
                fillRect(c, vl - 60f, vt - 60f, vr + 60f, vb + 60f, Color.argb(130, 0, 0, 0))
                text(c, t("paused"), W / 2f, 250f, 52f, Color.WHITE, maxW = 420f)
                for (b in pauseButtons) drawButton(c, b)
            }
            State.GAME_OVER -> drawGameOver(c)
        }
    }

    private fun drawMedal(c: Canvas, cx: Float, cy: Float, medalScore: Int) {
        val r = 36f
        val cols: IntArray? = when {
            medalScore >= 30 -> intArrayOf(Color.rgb(235, 248, 255), Color.rgb(150, 180, 205))
            medalScore >= 20 -> intArrayOf(Color.rgb(255, 225, 90), Color.rgb(220, 150, 20))
            medalScore >= 10 -> intArrayOf(Color.rgb(225, 228, 232), Color.rgb(150, 155, 165))
            medalScore >= 5 -> intArrayOf(Color.rgb(230, 150, 80), Color.rgb(150, 80, 30))
            else -> null
        }
        if (cols == null) {
            fillOval(c, cx - r, cy - r, cx + r, cy + r, Color.argb(24, 255, 255, 255))
            // dashed empty medal slot
            var a = 0f
            while (a < 6.28f) {
                val x0 = cx + cos(a) * r
                val y0 = cy + sin(a) * r
                val x1 = cx + cos(a + 0.17f) * r
                val y1 = cy + sin(a + 0.17f) * r
                line.color = Color.rgb(84, 94, 118)
                line.strokeWidth = 3f
                c.drawLine(x0, y0, x1, y1, line)
                a += 0.34f
            }
            return
        }
        fill.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, cols[0], cols[1], Shader.TileMode.CLAMP)
        fill.color = Color.WHITE
        rf.set(cx - r, cy - r, cx + r, cy + r)
        c.drawOval(rf, fill)
        fill.shader = null
        strokeOval(c, cx - r + 9f, cy - r + 9f, cx + r - 9f, cy + r - 9f, withA(Color.WHITE, 0.6f), 2f)
        // sparkle
        val sp = 0.5f + 0.5f * sin(tick * 0.12f)
        val l = 4f + 6f * sp
        val sx = cx - 15f
        val sy = cy - 15f
        line.color = Color.WHITE
        line.strokeWidth = 2.5f
        c.drawLine(sx - l, sy, sx + l, sy, line)
        c.drawLine(sx, sy - l, sx, sy + l, line)
    }

    private fun drawGameOver(c: Canvas) {
        // title
        val p1 = clampF(goTimer / 14f, 0f, 1f)
        c.saveLayerAlpha(vl, vt, vr, vb, (p1 * 255f).roundToInt())
        text(c, t("game_over"), W / 2f, 118f - (1f - p1) * 40f, 44f, Color.WHITE, maxW = 420f)
        c.restore()

        // panel slides up
        val pp = clampF((goTimer - 15) / 35f, 0f, 1f)
        val panelY = lerp(H + 20f, 170f, easeOutBack(pp))
        drawPanel(c, 60f, panelY, 420f, panelY + 300f)

        val labelColor = Ui.MUTED
        plainText(c, t("mode_x", t(difficulty.key)), W / 2f, panelY + 38f, 14f, Ui.MUTED, 0, 300f)
        plainText(c, t("medal"), 135f, panelY + 66f, 13f, labelColor, 0, 100f)
        plainText(c, t("score"), 305f, panelY + 66f, 13f, labelColor, 0, 100f)
        plainText(c, t("best"), 305f, panelY + 176f, 13f, labelColor, 0, 100f)

        val done = displayScore >= score
        drawMedal(c, 135f, panelY + 140f, if (done) score else -1)
        text(c, displayScore.toString(), 305f, panelY + 128f, 40f, Ui.TEXT, shadow = false)
        text(c, best.toString(), 305f, panelY + 238f, 40f, Ui.TEXT, shadow = false)

        if (coins > 0) {
            fillOval(c, 92f, panelY + 232f, 110f, panelY + 250f, GOLD)
            text(c, "x$coins", 138f, panelY + 248f, 18f, Ui.TEXT, shadow = false)
        }

        if (newBest && done) {
            val pulse = 1f + 0.06f * sin(tick * 0.2f)
            c.save()
            c.translate(372f, panelY + 190f)
            c.rotate(-0.22f * RAD2DEG)
            c.scale(pulse, pulse)
            fillRRect(c, -26f, -13f, 26f, 13f, 10f, Ui.DANGER)
            text(c, t("new_badge"), 0f, 5f, 13f, Color.WHITE, maxW = 46f, shadow = false)
            c.restore()
        }

        val sk = unlockedSkin
        if (sk != null && done) {
            text(c, t("new_skin_x", t(sk.key)), W / 2f, 500f, 20f, Color.rgb(255, 230, 90), 5f, maxW = 440f)
        }

        val appear = clampF((goTimer - 45) / 15f, 0f, 1f)
        for (b in overButtons) drawButton(c, b, max(0f, easeOutBack(appear)))
    }
}
