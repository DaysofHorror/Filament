package com.daysofhorror.filament

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Hosts one Scene and runs it every frame. When [full] becomes true the
 * animation eases down and then stops completely (no more frames drawn).
 */
class ChargeView(context: Context, info: SceneInfo, private val overlay: Boolean) : View(context) {

    private var scene: Scene = info.make()

    var level = 0.5f
        set(v) { field = clamp01(v); kick() }
    var percent = 50
    var status = ""
    var detail = ""
    var hint = ""

    /** Tiles on the picker screen wander between ~20% and ~90%. */
    var preview = false
    var previewOffset = 0f

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
        scene = info.make()
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

        // Full battery: let the motion glide to a halt over ~2.5 s.
        timeScale = if (full) max(0f, timeScale - dt / 2.5f) else min(1f, timeScale + dt / 0.6f)
        val ease = timeScale * timeScale * (3f - 2f * timeScale)
        val sdt = dt * ease
        t += sdt

        val target = if (preview) 0.55f + 0.36f * sin(t * 0.22f + previewOffset) else level
        shown = if (shown < 0f) target else shown + (target - shown) * min(1f, dt * 2.5f)

        scene.draw(c, w, h, t, sdt, shown)

        if (flash > 0f) {
            flash = max(0f, flash - dt / 1.4f)
            flashPaint.alpha = (flash * flash * 80).toInt()
            c.drawRect(0f, 0f, w, h, flashPaint)
        }

        if (overlay) drawOverlay(c, w, h)

        val settled = abs(target - shown) < 0.002f
        if (full && timeScale <= 0f && flash <= 0f && settled && realTime > 5f) {
            running = false // the final frame stays on screen, nothing moves
        }
    }

    private fun drawOverlay(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val y = h * 0.19f
        pctPaint.textSize = w * 0.24f
        signPaint.textSize = w * 0.08f
        val num = percent.toString()
        val numW = pctPaint.measureText(num)
        val signW = signPaint.measureText("%")
        val shift = signW / 2f
        c.drawText(num, cx - shift, y, pctPaint)
        c.drawText("%", cx - shift + numW / 2f + w * 0.008f, y, signPaint)

        statusPaint.textSize = w * 0.032f
        c.drawText(status, cx, y + w * 0.09f, statusPaint)
        if (detail.isNotEmpty()) {
            detailPaint.textSize = w * 0.036f
            c.drawText(detail, cx, y + w * 0.15f, detailPaint)
        }

        if (hint.isNotEmpty()) {
            val a = clamp01(1f - (realTime - 3.5f) / 1.2f)
            if (a > 0f) {
                detailPaint.textSize = w * 0.032f
                val old = detailPaint.alpha
                detailPaint.alpha = (a * 160).toInt()
                c.drawText(hint, cx, h - w * 0.12f, detailPaint)
                detailPaint.alpha = old
            }
        }
    }
}
