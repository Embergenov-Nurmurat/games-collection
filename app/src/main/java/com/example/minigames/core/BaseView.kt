package com.example.minigames.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Base class of every screen. It provides:
 *  - a 480x720 logical coordinate system scaled to fit the real display,
 *  - a fixed 60 Hz simulation step driven by the Choreographer (same speed on any refresh rate),
 *  - buttons with press animation, touch dispatching, fade transitions, particles, drawing helpers.
 */
abstract class BaseView(context: Context, protected val host: Host) : View(context), Choreographer.FrameCallback {

    protected val store: Store get() = host.store
    protected val sfx: Sfx get() = host.sfx

    // viewport: the logical 480x720 area fitted into the real screen
    protected var scale = 1f
    protected var ox = 0f
    protected var oy = 0f
    protected var vl = 0f      // visible logical bounds (can exceed 0..W / 0..H on tall screens)
    protected var vr = W
    protected var vt = 0f
    protected var vb = H

    protected var tick = 0
    protected var shake = 0f
    protected var flash = 0f

    protected val clouds = ArrayList<Cloud>()
    protected val particles = ArrayList<Particle>()

    // fade transitions (every screen fades in when created)
    private var fadeAlpha = 1f
    private var fadeDir = -1
    private var pending: (() -> Unit)? = null
    protected val fading: Boolean get() = fadeDir != 0

    // game loop
    private var running = false
    private var lastNs = 0L
    private var acc = 0f

    // paints and scratch objects (allocated once)
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    protected val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val mediumFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    /** Main colour of the screen (primary buttons, highlights). */
    protected open val accent: Int get() = Ui.ACCENT

    protected val rf = RectF()
    protected val path = Path()

    // buttons
    private val registry = ArrayList<Button>()
    private var heldBtn: Button? = null
    private var bgShader: Shader? = null
    private var bgKey = ""

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // ------------------------------ to implement ----------------------------

    /** One fixed simulation step (1/60 s). */
    protected abstract fun update()

    /** Draw the screen in logical coordinates. */
    protected abstract fun drawScreen(c: Canvas)

    /** Buttons that are active (and drawn by the screen itself) right now. */
    protected open fun buttons(): List<Button> = emptyList()

    /** A finger went down outside of every button. */
    protected open fun onPointerDown(x: Float, y: Float) {}

    /** The finger that started in [onPointerDown] moved (for swipes and drags). */
    protected open fun onPointerMove(x: Float, y: Float) {}

    /** The finger that started in [onPointerDown] was lifted (or the gesture was cancelled). */
    protected open fun onPointerUp(x: Float, y: Float) {}

    protected open fun onViewportChanged() {}

    protected open fun onKey(keyCode: Int): Boolean = false

    /** Back button / gesture. Return true when the screen handled it. */
    open fun onBack(): Boolean = false

    /** The app goes to the background. */
    open fun onHostPause() {
        stop()
        releaseHeld()
    }

    open fun onHostResume() {
        start()
    }

    // ------------------------------ lifecycle -------------------------------

    fun start() {
        if (running) return
        running = true
        lastNs = 0L
        acc = 0f
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        scale = min(w / W, h / H)
        ox = (w - W * scale) / 2f
        oy = (h - H * scale) / 2f
        vl = -ox / scale
        vr = (w - ox) / scale
        vt = -oy / scale
        vb = (h - oy) / scale
        clouds.clear()
        repeat(6) {
            clouds.add(Cloud(rnd(vl, vr), rnd(vt + 30f, 330f), rnd(0.7f, 1.4f), rnd(0.15f, 0.45f)))
        }
        bgKey = ""
        onViewportChanged()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastNs == 0L) lastNs = frameTimeNanos
        acc += (frameTimeNanos - lastNs) / 1e9f
        lastNs = frameTimeNanos
        if (acc > 0.25f) acc = 0.25f
        // small tolerance so a steady 60 Hz display never skips a simulation step
        while (acc >= STEP - 0.001f && running) {
            baseUpdate()
            update()
            acc -= STEP
        }
        if (!running) return
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun baseUpdate() {
        tick++
        Toasts.tick()
        if (fadeDir == 1) {
            fadeAlpha += 0.09f
            if (fadeAlpha >= 1f) {
                fadeAlpha = 1f
                val action = pending
                pending = null
                fadeDir = -1
                action?.invoke()
            }
        } else if (fadeDir == -1) {
            fadeAlpha -= 0.07f
            if (fadeAlpha <= 0f) {
                fadeAlpha = 0f
                fadeDir = 0
            }
        }
        flash *= 0.88f
        shake *= 0.88f
        for (b in registry) {
            b.down += ((if (b.held) 1f else 0f) - b.down) * 0.3f
            b.press *= 0.8f
        }
        var i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.x += p.vx
            p.y += p.vy
            p.vy += p.gravity
            p.life--
            if (p.life <= 0) particles.removeAt(i)
            i--
        }
    }

    /** Fade out, run [action], fade in. Ignored while another transition is running. */
    protected fun transition(action: () -> Unit) {
        if (fadeDir != 0) return
        pending = action
        fadeDir = 1
    }

    protected fun leaveTo(id: GameId) {
        transition { host.open(id) }
    }

    // ------------------------------- input ----------------------------------

    private fun toLx(x: Float) = (x - ox) / scale
    private fun toLy(y: Float) = (y - oy) / scale

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pointerDown(toLx(event.x), toLy(event.y))
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                pointerDown(toLx(event.getX(i)), toLy(event.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> {
                val b = heldBtn
                if (b != null) b.held = b.contains(toLx(event.x), toLy(event.y))
                else if (freePointer) onPointerMove(toLx(event.x), toLy(event.y))
            }
            MotionEvent.ACTION_UP -> {
                if (freePointer) {
                    freePointer = false
                    onPointerUp(toLx(event.x), toLy(event.y))
                } else {
                    pointerUp(toLx(event.x), toLy(event.y))
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (freePointer) {
                    freePointer = false
                    onPointerUp(-1000f, -1000f)
                }
                releaseHeld()
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!fading && onKey(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    private var freePointer = false

    private fun pointerDown(x: Float, y: Float) {
        if (fading) return
        if (heldBtn == null) {
            for (b in buttons()) {
                if (b.visible() && b.enabled() && b.contains(x, y)) {
                    heldBtn = b
                    b.held = true
                    return
                }
            }
        }
        freePointer = true
        onPointerDown(x, y)
    }

    private fun pointerUp(x: Float, y: Float) {
        val b = heldBtn ?: return
        heldBtn = null
        val hit = b.held && b.contains(x, y)
        b.held = false
        if (hit && !fading && b.visible() && b.enabled()) {
            b.press = 1f
            sfx.play(Sfx.Kind.CLICK)
            b.action()
        }
    }

    protected fun releaseHeld() {
        heldBtn?.held = false
        heldBtn = null
    }

    protected fun haptic(strong: Boolean = false) {
        if (!store.haptics) return
        performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Creates a button and registers it for animation. Buttons are shown only if returned by [buttons]. */
    protected fun btn(
        label: () -> String,
        cx: Float, cy: Float, w: Float, h: Float,
        style: () -> Int = { 0 },
        enabled: () -> Boolean = { true },
        visible: () -> Boolean = { true },
        action: () -> Unit
    ): Button {
        val b = Button(label, cx, cy, w, h, style, enabled, visible, action)
        registry.add(b)
        return b
    }

    // ------------------------------- drawing --------------------------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        if (shake > 0.5f) canvas.translate(rnd(-shake, shake), rnd(-shake, shake))
        drawScreen(canvas)
        if (flash > 0.02f) fillRect(canvas, vl - 60f, vt - 60f, vr + 60f, vb + 60f, withA(Color.WHITE, flash * 0.75f))
        drawToast(canvas)
        if (fadeAlpha > 0f) fillRect(canvas, vl - 60f, vt - 60f, vr + 60f, vb + 60f, withA(Color.BLACK, fadeAlpha))
        canvas.restore()
    }

    /** Banner with the newest achievement, sliding in from the top. */
    private fun drawToast(c: Canvas) {
        val it = Toasts.current() ?: return
        val inT = clampF(it.age / Toasts.SLIDE.toFloat(), 0f, 1f)
        val outT = clampF((Toasts.LIFE - it.age) / Toasts.SLIDE.toFloat(), 0f, 1f)
        val k = min(easeOutCubic(inT), easeOutCubic(outT))
        val top = vt + 12f - (1f - k) * 90f
        val l = W / 2f - 200f
        val r = W / 2f + 200f
        fillRRect(c, l, top, r, top + 62f, 16f, Ui.SURFACE_HI)
        strokeRRect(c, l, top, r, top + 62f, 16f, Ui.ACCENT, 1.5f)
        fillOval(c, l + 14f, top + 13f, l + 50f, top + 49f, withA(Ui.ACCENT, 0.2f))
        // star
        path.reset()
        for (i in 0 until 10) {
            val ang = -Math.PI / 2 + i * Math.PI / 5
            val rad = if (i % 2 == 0) 11f else 4.6f
            val px = l + 32f + (cos(ang) * rad).toFloat()
            val py = top + 31f + (sin(ang) * rad).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.shader = null
        fill.color = Ui.ACCENT
        c.drawPath(path, fill)
        plainText(c, it.title, l + 64f, top + 25f, 12f, Ui.MUTED, -1, 320f)
        plainText(c, it.sub, l + 64f, top + 47f, 18f, Ui.TEXT, -1, 320f)
    }

    protected fun fillRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawRect(l, t, r, b, fill)
    }

    protected fun fillOval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.shader = null
        fill.color = color
        rf.set(l, t, r, b)
        c.drawOval(rf, fill)
    }

    protected fun strokeOval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, w: Float) {
        line.color = color
        line.strokeWidth = w
        rf.set(l, t, r, b)
        c.drawOval(rf, line)
    }

    protected fun fillRRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        fill.shader = null
        fill.color = color
        rf.set(l, t, r, b)
        c.drawRoundRect(rf, rad, rad, fill)
    }

    protected fun strokeRRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int, w: Float) {
        line.color = color
        line.strokeWidth = w
        rf.set(l, t, r, b)
        c.drawRoundRect(rf, rad, rad, line)
    }

    private fun applyFace(size: Float, spacing: Float) {
        textFill.typeface = if (size >= 34f) Typeface.DEFAULT_BOLD else mediumFace
        textFill.letterSpacing = if (size >= 34f) 0.02f else spacing
    }

    /**
     * Text centred on [x] with the given baseline; shrinks to fit [maxW]. Large sizes use the bold face,
     * small ones the medium face. [sw] and [outline] are ignored (kept so older call sites still compile).
     */
    protected fun text(
        c: Canvas, s: String, x: Float, base: Float, size: Float,
        color: Int, sw: Float = 0f, outline: Int = OUTLINE, maxW: Float = 0f, shadow: Boolean = true
    ) {
        applyFace(size, 0.05f)
        var sz = size
        textFill.textSize = sz
        if (maxW > 0f) {
            val m = textFill.measureText(s)
            if (m > maxW) sz = size * maxW / m
        }
        textFill.textSize = sz
        textFill.textAlign = Paint.Align.CENTER
        if (shadow) {
            textFill.color = Color.argb(70, 0, 0, 0)
            c.drawText(s, x, base + max(1f, sz * 0.05f), textFill)
        }
        textFill.color = color
        c.drawText(s, x, base, textFill)
    }

    /** Plain text without shadow. [align]: -1 left, 0 centre, 1 right. */
    protected fun plainText(
        c: Canvas, s: String, x: Float, base: Float, size: Float, color: Int,
        align: Int = 0, maxW: Float = 0f
    ) {
        applyFace(size, 0.03f)
        var sz = size
        textFill.textSize = sz
        if (maxW > 0f) {
            val m = textFill.measureText(s)
            if (m > maxW) sz = size * maxW / m
        }
        textFill.textSize = sz
        textFill.color = color
        textFill.textAlign = when (align) {
            -1 -> Paint.Align.LEFT
            1 -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
        c.drawText(s, x, base, textFill)
        textFill.textAlign = Paint.Align.CENTER
    }

    protected fun measure(s: String, size: Float): Float {
        applyFace(size, 0.05f)
        textFill.textSize = size
        return textFill.measureText(s)
    }

    /** Full-screen vertical gradient. */
    protected fun drawGradient(c: Canvas, top: Int, bottom: Int) {
        val key = "$top/$bottom/$vt/$vb"
        if (key != bgKey) {
            bgKey = key
            bgShader = LinearGradient(0f, vt, 0f, vb, top, bottom, Shader.TileMode.CLAMP)
        }
        fill.shader = bgShader
        fill.color = Color.WHITE
        c.drawRect(vl - 60f, vt - 60f, vr + 60f, vb + 60f, fill)
        fill.shader = null
    }

    private var glowShader: Shader? = null
    private var glowColor = 0
    private var glowAlpha = 0f

    /** Dark background with a soft glow of the screen's accent colour at the top. */
    protected fun drawBackdrop(c: Canvas, glow: Int = accent) {
        drawGradient(c, Ui.BG_TOP, Ui.BG_BOTTOM)
        val ga = Ui.GLOW_ALPHA
        if (glowShader == null || glowColor != glow || glowAlpha != ga) {
            glowColor = glow
            glowAlpha = ga
            glowShader = RadialGradient(W / 2f, 120f, 430f, withA(glow, ga), withA(glow, 0f), Shader.TileMode.CLAMP)
        }
        fill.shader = glowShader
        fill.color = Color.WHITE
        c.drawRect(vl - 60f, vt - 60f, vr + 60f, vb + 60f, fill)
        fill.shader = null
    }

    protected fun updateClouds(extraSpeed: Float) {
        for (cl in clouds) {
            cl.x -= cl.speed + extraSpeed
            if (cl.x < vl - 150f) {
                cl.x = vr + 150f
                cl.y = rnd(vt + 30f, 330f)
            }
        }
    }

    protected fun drawCloud(c: Canvas, cl: Cloud, night: Float) {
        val s = cl.scale
        path.reset()
        rf.set(cl.x - 38f * s, cl.y - 12f * s, cl.x + 38f * s, cl.y + 18f * s)
        path.addOval(rf, Path.Direction.CW)
        rf.set(cl.x - 22f * s, cl.y - 28f * s, cl.x + 22f * s, cl.y + 12f * s)
        path.addOval(rf, Path.Direction.CW)
        rf.set(cl.x + 2f * s, cl.y - 20f * s, cl.x + 42f * s, cl.y + 14f * s)
        path.addOval(rf, Path.Direction.CW)
        fill.shader = null
        fill.color = withA(mixC(Color.WHITE, Color.rgb(120, 130, 175), night), 0.55f - 0.25f * night)
        c.drawPath(path, fill)
    }

    protected fun drawParticles(c: Canvas) {
        for (p in particles) {
            val a = p.life.toFloat() / p.maxLife
            val s = p.size * (0.4f + 0.6f * a)
            fillOval(c, p.x - s / 2f, p.y - s / 2f, p.x + s / 2f, p.y + s / 2f, withA(p.color, a))
        }
    }

    /** Burst of particles. [colors] are cycled. */
    protected fun emit(
        x: Float, y: Float, n: Int, s0: Float, s1: Float, life: Int,
        z0: Float, z1: Float, g: Float, vararg colors: Int
    ) {
        if (particles.size > 300) return
        repeat(n) {
            val a = rnd(0f, 6.2832f)
            val s = rnd(s0, s1)
            particles.add(
                Particle(x, y, cos(a) * s, sin(a) * s, life, life, colors[it % colors.size], rnd(z0, z1), g)
            )
        }
    }

    /** Falling confetti over the whole screen. */
    protected fun confetti() {
        val cols = intArrayOf(
            Color.rgb(255, 205, 50), Color.rgb(255, 110, 90), Color.rgb(90, 200, 255),
            Color.rgb(140, 230, 110), Color.WHITE, Color.rgb(230, 130, 255)
        )
        if (particles.size > 260) return
        repeat(50) {
            particles.add(
                Particle(
                    rnd(vl, vr), vt - rnd(0f, 120f), rnd(-1f, 1f), rnd(1.5f, 4f),
                    120, 120, cols[it % cols.size], rnd(6f, 11f), 0.03f
                )
            )
        }
    }

    /** Flat rounded button. Styles: 0 primary (accent), 1 neutral, 2 selected/on (accent outline), 3 off, 4 disabled. */
    protected fun drawButton(c: Canvas, b: Button, appear: Float = 1f) {
        if (!b.visible()) return
        val sc = (1f - 0.04f * b.down) * appear
        if (sc <= 0.01f) return
        val enabled = b.enabled()
        val st = if (enabled) b.style().coerceIn(0, 3) else 4
        val pr = if (enabled) max(b.down, b.press) else 0f
        val l = -b.w / 2f
        val tp = -b.h / 2f
        val r = b.w / 2f
        val bt = b.h / 2f
        val rad = min(14f, b.h * 0.32f)
        val acc = accent

        c.save()
        c.translate(b.cx, b.cy)
        c.scale(sc, sc)

        val fg: Int
        when (st) {
            0 -> {
                fillRRect(c, l, tp, r, bt, rad, mixC(acc, Color.WHITE, 0.14f * pr))
                fg = Ui.INK
            }
            1 -> {
                fillRRect(c, l, tp, r, bt, rad, mixC(Ui.SURFACE, Color.WHITE, 0.07f * pr))
                strokeRRect(c, l, tp, r, bt, rad, Ui.BORDER, 1.5f)
                fg = Ui.TEXT
            }
            2 -> {
                fillRRect(c, l, tp, r, bt, rad, mixC(Ui.SURFACE, acc, 0.16f + 0.12f * pr))
                strokeRRect(c, l, tp, r, bt, rad, acc, 1.5f)
                fg = acc
            }
            3 -> {
                fillRRect(c, l, tp, r, bt, rad, mixC(Ui.SURFACE, Color.WHITE, 0.07f * pr))
                strokeRRect(c, l, tp, r, bt, rad, Ui.BORDER, 1.5f)
                fg = Color.rgb(226, 128, 128)
            }
            else -> {
                fillRRect(c, l, tp, r, bt, rad, Ui.DISABLED_BG)
                fg = Ui.DISABLED_FG
            }
        }

        val size = min(20f, b.h * 0.4f)
        text(c, b.label(), 0f, size * 0.36f, size, fg, maxW = b.w - 22f, shadow = false)
        c.restore()
    }

    /** Dark surface panel with a thin border (result panels, dialogs). */
    protected fun drawPanel(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        fillRRect(c, l, t, r, b, 22f, Ui.SURFACE)
        strokeRRect(c, l, t, r, b, 22f, Ui.BORDER, 1.5f)
    }

    /** Large screen title with a short accent bar below it. */
    protected fun drawTitle(c: Canvas, s: String, cx: Float, base: Float, size: Float, color: Int, maxW: Float, bar: Boolean = true) {
        text(c, s, cx, base, size, color, maxW = maxW, shadow = false)
        if (bar) fillRRect(c, cx - 22f, base + 16f, cx + 22f, base + 19f, 1.5f, accent)
    }
}
