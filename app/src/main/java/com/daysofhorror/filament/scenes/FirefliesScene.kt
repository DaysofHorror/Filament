package com.daysofhorror.filament.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.daysofhorror.filament.Glow
import com.daysofhorror.filament.Scene
import com.daysofhorror.filament.TAU
import com.daysofhorror.filament.col
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A dark pine wood. One firefly wakes up for every 1% of charge. */
class FirefliesScene : Scene() {
    private val n = 100
    private val bx = FloatArray(n)
    private val by = FloatArray(n)
    private val ax = FloatArray(n)
    private val ay = FloatArray(n)
    private val fx = FloatArray(n)
    private val fy = FloatArray(n)
    private val ph = FloatArray(n)
    private val bf = FloatArray(n)
    private val alpha = FloatArray(n)
    private val ox = FloatArray(n)
    private val oy = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val flare = FloatArray(n)

    private var lw = -1f
    private var lh = -1f
    private val sky = Paint()
    private val mist = Paint()
    private val far = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF0A1C20) }
    private val near = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF020606) }
    private val moon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFFF4F1DE) }
    private val farPath = Path()
    private val nearPath = Path()

    init {
        val r = Random(21)
        for (i in 0 until n) {
            bx[i] = 0.06f + r.nextFloat() * 0.88f
            by[i] = 0.40f + r.nextFloat() * 0.48f
            ax[i] = 0.02f + r.nextFloat() * 0.06f
            ay[i] = 0.01f + r.nextFloat() * 0.035f
            fx[i] = 0.10f + r.nextFloat() * 0.28f
            fy[i] = 0.14f + r.nextFloat() * 0.32f
            ph[i] = r.nextFloat() * TAU
            bf[i] = 0.7f + r.nextFloat() * 1.5f
        }
    }

    private fun pine(p: Path, x: Float, base: Float, height: Float, width: Float) {
        for (k in 0 until 4) {
            val ty = base - height + height * k * 0.2f
            val by2 = ty + height * 0.38f
            val hw = width * (0.3f + k * 0.2f)
            p.moveTo(x, ty)
            p.lineTo(x + hw, by2)
            p.lineTo(x - hw, by2)
            p.close()
        }
        p.addRect(x - width * 0.05f, base - height * 0.25f, x + width * 0.05f, base, Path.Direction.CW)
    }

    private fun setup(w: Float, h: Float) {
        lw = w; lh = h
        sky.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(col(0xFF040B14), col(0xFF0B2228), col(0xFF060E10)),
            floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        mist.shader = LinearGradient(0f, h * 0.7f, 0f, h * 0.9f, col(0x00A8D8C8), col(0x22A8D8C8), Shader.TileMode.CLAMP)
        val r = Random(5)
        farPath.rewind()
        for (i in 0..16) {
            val x = i / 16f * w + (r.nextFloat() - 0.5f) * w * 0.05f
            val ht = h * (0.15f + r.nextFloat() * 0.11f)
            pine(farPath, x, h * 0.84f, ht, ht * 0.42f)
        }
        farPath.addRect(0f, h * 0.835f, w, h, Path.Direction.CW)
        nearPath.rewind()
        val xs = floatArrayOf(0.02f, 0.13f, 0.24f, 0.8f, 0.9f, 1.0f)
        for (fxp in xs) {
            val ht = h * (0.32f + r.nextFloat() * 0.26f)
            pine(nearPath, fxp * w, h * 1.01f, ht, ht * 0.4f)
        }
        nearPath.moveTo(0f, h)
        nearPath.lineTo(0f, h * 0.93f)
        nearPath.quadTo(w * 0.5f, h * 0.89f, w, h * 0.94f)
        nearPath.lineTo(w, h)
        nearPath.close()
    }

    override fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float) {
        if (w != lw || h != lh) setup(w, h)
        val u = w / 400f
        c.drawRect(0f, 0f, w, h, sky)

        val mx = w * 0.8f
        val my = h * 0.12f
        Glow.draw(c, col(0xFF9FB8C0), mx, my, 110f * u, 0.35f)
        c.drawCircle(mx, my, 17f * u, moon)

        c.drawPath(farPath, far)
        c.drawRect(0f, h * 0.7f, w, h * 0.9f, mist)

        val count = (level * n).roundToInt()
        val fade = min(1f, dt * 1.5f)
        for (i in 0 until n) {
            val target = if (i < count) 1f else 0f
            alpha[i] += (target - alpha[i]) * fade
            if (dt == 0f && alpha[i] < 0.01f && target > 0f) alpha[i] = target // first frame / frozen
            if (alpha[i] < 0.01f) continue
            val x0 = (bx[i] + ax[i] * sin(t * fx[i] + ph[i]) + 0.025f * sin(t * fx[i] * 2.7f + ph[i] * 3f)) * w
            val y0 = (by[i] + ay[i] * sin(t * fy[i] + ph[i] * 1.7f)) * h
            // Tilt: drift towards the low side. Taps: scatter away and flare up.
            for (tap in env.taps) {
                val dx = x0 + ox[i] - tap[0]
                val dy = y0 + oy[i] - tap[1]
                val d = sqrt(dx * dx + dy * dy)
                val rr = 170f * u
                if (d < rr) {
                    val k = (1f - d / rr) * 900f * u / max(d, 1f)
                    vx[i] += dx * k
                    vy[i] += dy * k
                    flare[i] = 1f
                }
            }
            val tx = env.tiltX * 0.22f * w
            vx[i] += ((tx - ox[i]) * 1.3f) * dt
            vy[i] += ((0f - oy[i]) * 1.3f) * dt
            val damp = exp(-1.8f * dt)
            vx[i] *= damp; vy[i] *= damp
            ox[i] += vx[i] * dt
            oy[i] += vy[i] * dt
            flare[i] = max(0f, flare[i] - dt * 0.9f)
            val x = x0 + ox[i]
            val y = y0 + oy[i]
            val pulse = max(flare[i], 0.3f + 0.7f * max(0f, sin(t * bf[i] + ph[i])).pow(3))
            Glow.draw(c, col(0xFFC8FF6A), x, y, 34f * u * (0.6f + 0.4f * pulse), alpha[i] * pulse)
            Glow.draw(c, col(0xFFF6FFD8), x, y, 7f * u, alpha[i] * (0.5f + 0.5f * pulse))
        }

        c.drawPath(nearPath, near)
    }
}
