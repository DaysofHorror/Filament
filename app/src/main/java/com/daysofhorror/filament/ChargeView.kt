package com.daysofhorror.filament

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Hosts one Scene and runs it every frame. Plays the lightning "surge" power-on,
 * handles burn-in protection (slow pixel shift + idle dimming), and when [full]
 * becomes true eases the animation down and then stops completely.
 */
class ChargeView(context: Context, info: SceneInfo, private val overlay: Boolean) : View(context) {

    val env = Env()
    private var scene: Scene = info.make().also { it.env = env }
    private var accent = info.accent

    var level = 0.5f
        set(v) { field = clamp01(v); kick() }
    var percent = 50
    var status = ""
    var detail = ""
    var hint = ""

    /** Tiles on the picker screen wander between ~20% and ~90%. */
    var preview = false
    var previewOffset = 0f

    /** Play the lightning power-on when the view first appears. */
    var intro = false
    var onImpact: (() -> Unit)? = null

    var full = false
        set(v) {
            if (v != field) {
                field = v
                if (v) flash = 1f
                kick()
            }
        }

    private var t = 0f
    private var timeScale = 1f
    private var shown = -1f
    private var lastNanos = 0L
    private var running = false
    private var flash = 0f
    private var realTime = 0f
    private var lastTouch = 0f
    private var impactDone = false
    private var shiftX = 0f
    private var shiftY = 0f
    private var scale = 1f

    private val boltStart = 0.25f
    private val boltEnd = 0.85f
    private val boltX = FloatArray(17)
    private val boltY = FloatArray(17)
    private val boltPath = Path()
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    private val darkPaint = Paint().apply { color = col(0xFF000000) }
    private val flashPaint = Paint().apply { color = col(0xFFFFFFFF) }
    private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xF2FFFFFF)
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(24f, 0f, 0f, col(0x99000000))
    }
    private val signPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xCCFFFFFF)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
        setShadowLayer(18f, 0f, 0f, col(0x99000000))
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xE6FFFFFF)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.22f
        setShadowLayer(14f, 0f, 0f, col(0xAA000000))
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xB3FFFFFF)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(14f, 0f, 0f, col(0xAA000000))
    }

    private val ticker = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setScene(info: SceneInfo) {
        scene = info.make().also { it.env = env }
        accent = info.accent
        kick()
    }

    /** Feed touches in (from the activity). Coordinates are converted into scene space. */
    fun handleTouch(e: MotionEvent) {
        lastTouch = realTime
        val w = width.toFloat()
        val h = height.toFloat()
        val sx = (e.x - w / 2f - shiftX) / scale + w / 2f
        val sy = (e.y - h / 2f - shiftY) / scale + h / 2f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                env.taps.add(floatArrayOf(sx, sy))
                env.touching = true
                env.touchX = sx; env.touchY = sy
            }
            MotionEvent.ACTION_MOVE -> { env.touchX = sx; env.touchY = sy }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> env.touching = false
        }
        kick()
    }

    private fun kick() {
        if (!running && isAttachedToWindow) {
            running = true
            lastNanos = 0L
            Choreographer.getInstance().postFrameCallback(ticker)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        kick()
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
        realTime += dt

        // ---- Surge power-on ----
        val power: Float
        if (!intro) {
            power = 1f
        } else {
            if (!impactDone && realTime >= boltEnd) {
                impactDone = true
                flash = 0.9f
                onImpact?.invoke()
            }
            val p = clamp01((realTime - boltEnd) / 0.8f)
            val flick = if (p > 0f && p < 1f && hashF((realTime * 24f).toInt()) < 0.4f) 0.3f else 1f
            power = if (realTime < boltEnd) 0f else p * flick
        }
        env.power = power

        // Full battery: let the motion glide to a halt over ~2.5 s.
        timeScale = if (full) max(0f, timeScale - dt / 2.5f) else min(1f, timeScale + dt / 0.6f)
        val ease = timeScale * timeScale * (3f - 2f * timeScale)
        val sdt = dt * ease
        t += sdt

        val target = when {
            preview -> 0.55f + 0.36f * sin(t * 0.22f + previewOffset)
            intro && realTime < boltEnd -> 0f
            else -> level
        }
        val fillRate = if (intro && realTime < boltEnd + 3f) 1.4f else 2.5f
        shown = if (shown < 0f) (if (intro) 0f else target) else shown + (target - shown) * min(1f, dt * fillRate)

        // ---- Burn-in protection: the whole picture drifts a few pixels very slowly ----
        if (overlay) {
            val amp = w * 0.006f
            shiftX = amp * sin(realTime * TAU / 170f)
            shiftY = amp * sin(realTime * TAU / 230f + 1f)
            scale = 1f + amp * 2.2f / w
        } else {
            shiftX = 0f; shiftY = 0f; scale = 1f
        }

        c.save()
        c.translate(w / 2f + shiftX, h / 2f + shiftY)
        c.scale(scale, scale)
        c.translate(-w / 2f, -h / 2f)
        scene.draw(c, w, h, t, sdt, shown)
        c.restore()
        env.taps.clear()

        // Idle 3 min without a touch: dim the scene a little and the numbers a lot.
        val idle = if (overlay) clamp01((realTime - lastTouch - 180f) / 3f) else 0f
        val unpowered = (1f - power) * 0.9f
        val idleDark = idle * 0.25f
        val dark = 1f - (1f - unpowered) * (1f - idleDark)
        if (dark > 0.004f) {
            darkPaint.alpha = (dark * 255).toInt()
            c.drawRect(0f, 0f, w, h, darkPaint)
        }

        if (intro && realTime > boltStart && realTime < boltEnd + 0.25f) drawBolt(c, w, h)

        if (flash > 0f) {
            flash = max(0f, flash - dt / 1.4f)
            flashPaint.alpha = (flash * flash * 80).toInt()
            c.drawRect(0f, 0f, w, h, flashPaint)
        }

        if (overlay) {
            val textAlpha = (1f - 0.6f * idle) * clamp01(power * 1.5f)
            c.save()
            c.translate(shiftX * 1.6f, shiftY * 1.6f)
            drawOverlay(c, w, h, textAlpha)
            c.restore()
        }

        val settled = abs(target - shown) < 0.002f
        if (full && timeScale <= 0f && flash <= 0f && settled && realTime > 5f && idle == 0f) {
            running = false // the final frame stays on screen, nothing moves
        }
    }

    private fun drawBolt(c: Canvas, w: Float, h: Float) {
        val u = w / 400f
        val p = clamp01((realTime - boltStart) / (boltEnd - boltStart))
        val reach = p * p
        val fade = if (realTime > boltEnd) 1f - (realTime - boltEnd) / 0.25f else 1f
        val x0 = w / 2f
        val y0 = h + 4f
        val ty = h * 0.58f
        val tipY = lerp(y0, ty, reach)
        val seed = (realTime * 22f).toInt()
        val n = 16
        boltPath.rewind()
        for (i in 0..n) {
            val f = i / n.toFloat()
            val jitter = if (i == 0 || i == n) 0f else (hashF(seed * 31 + i) - 0.5f) * w * 0.09f
            boltX[i] = x0 + jitter
            boltY[i] = lerp(y0, tipY, f)
            if (i == 0) boltPath.moveTo(boltX[i], boltY[i]) else boltPath.lineTo(boltX[i], boltY[i])
        }
        // Little forks
        for (b in 0 until 3) {
            val k = 4 + b * 4
            val dir = if (hashF(seed * 7 + b) < 0.5f) -1f else 1f
            boltPath.moveTo(boltX[k], boltY[k])
            boltPath.lineTo(boltX[k] + dir * w * 0.06f, boltY[k] - h * 0.025f)
            boltPath.lineTo(boltX[k] + dir * w * (0.09f + hashF(seed + b) * 0.05f), boltY[k] - h * 0.06f)
        }
        boltPaint.color = withAlpha(accent, (70 * fade).toInt())
        boltPaint.strokeWidth = 16f * u
        c.drawPath(boltPath, boltPaint)
        boltPaint.color = withAlpha(accent, (160 * fade).toInt())
        boltPaint.strokeWidth = 6f * u
        c.drawPath(boltPath, boltPaint)
        boltPaint.color = withAlpha(col(0xFFFFFFFF), (255 * fade).toInt())
        boltPaint.strokeWidth = 2.2f * u
        c.drawPath(boltPath, boltPaint)
        Glow.draw(c, accent, x0, h, 90f * u, 0.8f * fade)
        Glow.draw(c, col(0xFFFFFFFF), x0, tipY, 40f * u, 0.9f * fade)
        Glow.draw(c, accent, x0, tipY, 110f * u, 0.7f * fade)
    }

    private fun drawOverlay(c: Canvas, w: Float, h: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val cx = w / 2f
        val y = h * 0.19f
        pctPaint.textSize = w * 0.24f
        pctPaint.alpha = (0xF2 * alpha).toInt()
        signPaint.textSize = w * 0.08f
        signPaint.alpha = (0xCC * alpha).toInt()
        val num = percent.toString()
        val numW = pctPaint.measureText(num)
        val signW = signPaint.measureText("%")
        val shift = signW / 2f
        c.drawText(num, cx - shift, y, pctPaint)
        c.drawText("%", cx - shift + numW / 2f + w * 0.008f, y, signPaint)

        statusPaint.textSize = w * 0.032f
        statusPaint.alpha = (0xE6 * alpha).toInt()
        c.drawText(status, cx, y + w * 0.09f, statusPaint)
        if (detail.isNotEmpty()) {
            detailPaint.textSize = w * 0.036f
            detailPaint.alpha = (0xB3 * alpha).toInt()
            c.drawText(detail, cx, y + w * 0.15f, detailPaint)
        }

        if (hint.isNotEmpty()) {
            val a = clamp01(1f - (realTime - 5f) / 1.2f) * alpha
            if (a > 0f) {
                detailPaint.textSize = w * 0.032f
                detailPaint.alpha = (a * 160).toInt()
                c.drawText(hint, cx, h - w * 0.12f, detailPaint)
            }
        }
    }
}
