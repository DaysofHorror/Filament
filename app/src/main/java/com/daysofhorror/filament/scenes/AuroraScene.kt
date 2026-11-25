package com.daysofhorror.filament.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.daysofhorror.filament.Glow
import com.daysofhorror.filament.Scene
import com.daysofhorror.filament.Spring
import com.daysofhorror.filament.col
import com.daysofhorror.filament.hashF
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Northern lights over Highland hills and a little bothy. They grow taller as you charge. */
class AuroraScene : Scene() {
    private var lw = -1f
    private var lh = -1f
    private val sky = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val ribbons = arrayOfNulls<Shader>(3)
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFFFFFFFF) }
    private val farP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF0C1828) }
    private val nearP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF03070D) }
    private val windowP = Paint().apply { color = col(0xFFFFC36B) }
    private val farPath = Path()
    private val nearPath = Path()
    private val stars = FloatArray(150 * 3)
    private var winX = 0f
    private var winY = 0f
    private var winS = 0f
    private val lean = Spring(9f, 2.2f)
    private val par = Spring(14f, 6f)
    private val flares = ArrayList<FloatArray>() // x, age

    private fun nearY(x: Float, w: Float, h: Float): Float {
        val nx = x / w
        return h * 0.86f - h * (0.035f * sin(nx * 2.3f + 2f) + 0.01f * sin(nx * 7f))
    }

    private fun setup(w: Float, h: Float) {
        lw = w; lh = h
        sky.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(col(0xFF01030A), col(0xFF061428), col(0xFF0B1F30)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        for (i in 0 until 3) {
            val base = h * (0.36f + i * 0.05f)
            ribbons[i] = LinearGradient(
                0f, base + h * 0.05f, 0f, base - h * 0.42f,
                intArrayOf(col(0x0033FF99), col(0xFF3DFFA0), col(0xC02BD9C8), col(0x808A5CFF), col(0x00B05CFF)),
                floatArrayOf(0f, 0.12f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP
            )
        }
        val r = Random(3)
        for (i in 0 until 150) {
            stars[i * 3] = r.nextFloat() * w
            stars[i * 3 + 1] = r.nextFloat() * r.nextFloat() * h * 0.75f
            stars[i * 3 + 2] = 0.5f + r.nextFloat() * 1.3f
        }
        farPath.rewind()
        farPath.moveTo(0f, h)
        var x = 0f
        while (x <= w + 8f) {
            val nx = x / w
            farPath.lineTo(x, h * 0.74f - h * (0.05f * sin(nx * 4.1f + 1f) + 0.022f * sin(nx * 9.3f) + 0.04f))
            x += 6f
        }
        farPath.lineTo(w + 8f, h)
        farPath.close()

        nearPath.rewind()
        nearPath.moveTo(0f, h)
        x = 0f
        while (x <= w + 8f) {
            nearPath.lineTo(x, nearY(x, w, h))
            x += 6f
        }
        nearPath.lineTo(w + 8f, h)
        nearPath.close()

        // The bothy: stone walls, pitched roof, chimney
        val bw = w * 0.11f
        val bh = bw * 0.5f
        val bx = w * 0.28f
        val gy = nearY(bx, w, h) + bh * 0.15f
        nearPath.addRect(bx - bw / 2, gy - bh, bx + bw / 2, gy, Path.Direction.CW)
        nearPath.moveTo(bx - bw / 2 - bw * 0.06f, gy - bh)
        nearPath.lineTo(bx, gy - bh - bw * 0.36f)
        nearPath.lineTo(bx + bw / 2 + bw * 0.06f, gy - bh)
        nearPath.close()
        nearPath.addRect(bx + bw * 0.22f, gy - bh - bw * 0.36f, bx + bw * 0.32f, gy - bh - bw * 0.08f, Path.Direction.CW)
        winX = bx + bw * 0.18f
        winY = gy - bh * 0.55f
        winS = bw * 0.09f
    }

    override fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float) {
        if (w != lw || h != lh) setup(w, h)
        val u = w / 400f
        c.drawRect(0f, 0f, w, h, sky)

        val pv = par.step(env.tiltX.coerceIn(-1f, 1f), dt)
        val sk = lean.step(env.tiltX.coerceIn(-1f, 1f) * 0.35f, dt)
        for (tap in env.taps) flares.add(floatArrayOf(tap[0], 0f))
        val fit = flares.iterator()
        while (fit.hasNext()) { val f = fit.next(); f[1] += dt; if (f[1] > 3f) fit.remove() }

        for (i in 0 until 150) {
            val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1f + hashF(i) * 2f) + i))
            star.alpha = (a * 220).toInt()
            c.drawCircle(stars[i * 3] + pv * 5f * u, stars[i * 3 + 1], stars[i * 3 + 2] * u, star)
        }

        val intensity = (0.25f + 0.75f * level) * env.power
        val step = 5f * u
        for (i in 0 until 3) {
            line.shader = ribbons[i]
            val baseY = h * (0.36f + i * 0.05f)
            var x = -10f * u
            while (x <= w + 10f * u) {
                val nx = x / w
                val yb = baseY + h * (0.045f * sin(nx * 3f + t * 0.13f * (i + 1) + i * 1.7f) +
                    0.018f * sin(nx * 8f - t * 0.31f + i))
                // Taps send a bright surge through the curtains at that spot
                var boost = 0f
                for (f in flares) {
                    val d = (x - f[0]) / (w * 0.13f)
                    boost += exp(-d * d) * (1f - f[1] / 3f) * (0.6f + 0.4f * sin(f[1] * 9f))
                }
                val ht = h * (0.05f + 0.32f * level) * (0.55f + 0.45f * sin(nx * 4.5f + t * 0.37f + i * 2.1f)) * (1f + boost * 0.6f) * (0.3f + 0.7f * env.power)
                val a = minOf(1f, intensity * (0.55f + 0.45f * sin(nx * 40f + t * 1.6f * (1f + i * 0.3f) + i * 3f)) * (1f + boost * 1.4f))
                line.strokeWidth = step * 3.2f
                line.alpha = (a * 55).toInt()
                c.drawLine(x, yb, x + sk * ht, yb - ht, line)
                line.strokeWidth = step * 0.9f
                line.alpha = (a * 190).toInt()
                c.drawLine(x, yb, x + sk * ht, yb - ht, line)
                x += step
            }
        }

        c.save()
        c.translate(pv * 3f * u, 0f)
        c.drawPath(farPath, farP)
        c.restore()
        c.save()
        c.translate(pv * 10f * u, 0f)
        c.drawPath(nearPath, nearP)
        val flicker = 0.85f + 0.15f * sin(t * 7f) * sin(t * 3.1f)
        Glow.draw(c, col(0xFFFFB347), winX, winY, 26f * u, 0.7f * flicker)
        c.drawRect(winX - winS, winY - winS, winX + winS, winY + winS, windowP)
        c.restore()
    }
}
