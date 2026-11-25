package com.daysofhorror.filament.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.daysofhorror.filament.Glow
import com.daysofhorror.filament.PIf
import com.daysofhorror.filament.Scene
import com.daysofhorror.filament.clamp01
import com.daysofhorror.filament.col
import com.daysofhorror.filament.hashF
import com.daysofhorror.filament.lerpColor
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A tree grows with the charge, then blossoms and sheds petals as it nears full. */
class BloomScene : Scene {
    private val maxDepth = 9
    private val tipX = FloatArray(2 shl maxDepth)
    private val tipY = FloatArray(2 shl maxDepth)
    private val tipId = IntArray(2 shl maxDepth)
    private var tipCount = 0

    private var lw = -1f
    private var lh = -1f
    private val sky = Paint()
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF0C0712) }
    private val groundPath = Path()
    private val branch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val petal = Paint(Paint.ANTI_ALIAS_FLAG)

    private var u = 1f
    private var tt = 0f

    private val maxPetals = 80
    private val px = FloatArray(maxPetals)
    private val py = FloatArray(maxPetals)
    private val pr = FloatArray(maxPetals)
    private val pv = FloatArray(maxPetals)
    private val pl = FloatArray(maxPetals)
    private val alive = BooleanArray(maxPetals)
    private var spawnAcc = 0f
    private val rnd = Random(4)

    private fun setup(w: Float, h: Float) {
        lw = w; lh = h
        sky.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(col(0xFF120926), col(0xFF341A48), col(0xFF9E4E66), col(0xFFE3926E)),
            floatArrayOf(0f, 0.5f, 0.8f, 0.9f), Shader.TileMode.CLAMP
        )
        groundPath.rewind()
        groundPath.moveTo(0f, h)
        groundPath.lineTo(0f, h * 0.9f)
        groundPath.quadTo(w * 0.5f, h * 0.855f, w, h * 0.9f)
        groundPath.lineTo(w, h)
        groundPath.close()
    }

    override fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float) {
        if (w != lw || h != lh) setup(w, h)
        u = w / 400f
        tt = t
        c.drawRect(0f, 0f, w, h, sky)
        Glow.draw(c, col(0xFFFFB27A), w * 0.5f, h * 0.88f, w * 0.55f, 0.45f)

        val growth = 0.08f + 0.92f * clamp01(level / 0.85f)
        val maxD = 1f + (maxDepth - 1) * growth
        tipCount = 0
        val baseLen = min(h * 0.15f, w * 0.32f)
        c.drawPath(groundPath, ground)
        grow(c, w * 0.5f, h * 0.875f, -PIf / 2f, baseLen, 0, 1, maxD)

        // Blossoms open one by one as the charge passes ~45%
        val bloom = clamp01((level - 0.45f) / 0.5f)
        if (bloom > 0f) {
            for (k in 0 until tipCount) {
                val id = tipId[k]
                val hv = hashF(id)
                val open = clamp01((bloom * 1.05f - hv) * 6f)
                if (open <= 0f) continue
                val size = (3.5f + hashF(id * 5) * 4f) * u * open
                val cc = lerpColor(col(0xFFFF8FBA), col(0xFFFFEAF2), hashF(id * 9))
                Glow.draw(c, col(0xFFFF9EC4), tipX[k], tipY[k], size * 3.2f, 0.22f * open)
                petal.color = cc
                c.drawCircle(tipX[k], tipY[k], size, petal)
            }
        }

        // Falling petals
        if (dt > 0f && bloom > 0.3f && tipCount > 0) {
            spawnAcc += dt * 6f * bloom
            while (spawnAcc >= 1f) {
                spawnAcc -= 1f
                val slot = alive.indexOfFirst { !it }
                if (slot < 0) break
                val k = rnd.nextInt(tipCount)
                px[slot] = tipX[k]; py[slot] = tipY[k]
                pr[slot] = rnd.nextFloat() * 360f
                pv[slot] = (rnd.nextFloat() - 0.5f) * 200f
                pl[slot] = 0f
                alive[slot] = true
            }
        }
        petal.color = col(0xFFFFC2DA)
        for (i in 0 until maxPetals) {
            if (!alive[i]) continue
            pl[i] += dt
            py[i] += (20f + 10f * sin(pl[i] * 2f + i)) * u * dt
            px[i] += (sin(t * 0.8f + i) * 14f + 9f) * u * dt
            pr[i] += pv[i] * dt
            if (py[i] > h * 0.9f || pl[i] > 14f) { alive[i] = false; continue }
            petal.alpha = (min(1f, (14f - pl[i]) / 2f) * 230).toInt()
            c.save()
            c.translate(px[i], py[i])
            c.rotate(pr[i])
            c.drawOval(-3.2f * u, -1.7f * u, 3.2f * u, 1.7f * u, petal)
            c.restore()
        }
    }

    private fun addTip(x: Float, y: Float, id: Int) {
        if (tipCount < tipX.size) {
            tipX[tipCount] = x; tipY[tipCount] = y; tipId[tipCount] = id
            tipCount++
        }
    }

    private fun grow(c: Canvas, x: Float, y: Float, ang: Float, len: Float, depth: Int, id: Int, maxD: Float) {
        if (depth >= maxD) return
        val frac = min(1f, maxD - depth)
        val sway = sin(tt * 1.3f + depth * 0.7f + hashF(id) * 6f) * 0.018f * depth + sin(tt * 0.5f) * 0.01f * depth
        val a = ang + sway
        val l = len * frac
        val x2 = x + cos(a) * l
        val y2 = y + sin(a) * l
        branch.strokeWidth = max(0.9f * u, len * 0.11f)
        branch.color = lerpColor(col(0xFF1E120E), col(0xFF5A3E33), depth / maxDepth.toFloat())
        c.drawLine(x, y, x2, y2, branch)
        // Blossom points: every outer branch end, plus the growing tips
        if (depth >= 4 && frac > 0.6f) addTip(x2, y2, id)
        if (frac < 1f || depth == maxDepth - 1) return
        val spread = 0.36f + (hashF(id * 3 + 1) - 0.5f) * 0.16f
        val ratio = 0.74f + (hashF(id * 7 + 2) - 0.5f) * 0.08f
        val tilt = (hashF(id * 19) - 0.5f) * 0.12f
        grow(c, x2, y2, a - spread + tilt, len * ratio, depth + 1, id * 2, maxD)
        grow(c, x2, y2, a + spread + tilt, len * ratio, depth + 1, id * 2 + 1, maxD)
    }
}
