package com.daysofhorror.filament.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.daysofhorror.filament.Glow
import com.daysofhorror.filament.Scene
import com.daysofhorror.filament.Spring
import com.daysofhorror.filament.clamp01
import com.daysofhorror.filament.col
import com.daysofhorror.filament.hashF
import com.daysofhorror.filament.lerp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Each 1% lights a new star and threads it into a growing constellation. */
class ConstellationScene : Scene() {
    private val n = 100
    private val sx = FloatArray(n)
    private val sy = FloatArray(n)
    private val edgeA = ArrayList<Int>()
    private val edgeB = ArrayList<Int>()
    private val appear = FloatArray(n) { -1f }
    private var started = false
    private val par = Spring(14f, 6f)
    private class Temp(val x: Float, val y: Float, var age: Float, val a: Int, val b: Int)
    private val temps = ArrayList<Temp>()

    private var lw = -1f
    private var lh = -1f
    private val bg = Paint()
    private val neb = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val nebPos = FloatArray(9)
    private val dust = FloatArray(180 * 3)
    private val dustP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFFFFFFFF) }
    private val linkP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF9FD8FF) }
    private val coreP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFFFFFFFF) }
    private val flareP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xFFFFFFFF)
        strokeCap = Paint.Cap.ROUND
    }

    init {
        val r = Random(42)
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        var tries = 0
        while (px.size < n && tries < 30000) {
            tries++
            val x = 0.08f + r.nextFloat() * 0.84f
            val y = 0.30f + r.nextFloat() * 0.62f
            var ok = true
            for (i in px.indices) {
                val dx = x - px[i]
                val dy = (y - py[i]) * 2f
                if (dx * dx + dy * dy < 0.065f * 0.065f) { ok = false; break }
            }
            if (ok || tries > 25000) { px.add(x); py.add(y) }
        }
        while (px.size < n) { px.add(0.1f + r.nextFloat() * 0.8f); py.add(0.3f + r.nextFloat() * 0.6f) }

        // Greedy chain from the bottom middle, so the figure grows outwards.
        val used = BooleanArray(n)
        var cur = 0
        var best = Float.MAX_VALUE
        for (i in 0 until n) {
            val d = kotlin.math.abs(px[i] - 0.5f) + kotlin.math.abs(py[i] - 0.88f)
            if (d < best) { best = d; cur = i }
        }
        for (k in 0 until n) {
            used[cur] = true
            sx[k] = px[cur]; sy[k] = py[cur]
            var next = -1
            var nd = Float.MAX_VALUE
            for (j in 0 until n) if (!used[j]) {
                val dx = px[j] - px[cur]
                val dy = (py[j] - py[cur]) * 2f
                val d = dx * dx + dy * dy
                if (d < nd) { nd = d; next = j }
            }
            if (next < 0) break
            cur = next
        }
        for (i in 1 until n) {
            edgeA.add(i - 1); edgeB.add(i)
            if (i >= 4 && hashF(i * 13) < 0.4f) {
                var bj = -1
                var bd = Float.MAX_VALUE
                for (j in 0 until i - 1) {
                    val dx = sx[j] - sx[i]
                    val dy = (sy[j] - sy[i]) * 2f
                    val d = dx * dx + dy * dy
                    if (d < bd) { bd = d; bj = j }
                }
                if (bj >= 0) { edgeA.add(bj); edgeB.add(i) }
            }
        }
    }

    private fun setup(w: Float, h: Float) {
        lw = w; lh = h
        bg.shader = LinearGradient(0f, 0f, 0f, h, col(0xFF03040D), col(0xFF0A1024), Shader.TileMode.CLAMP)
        val cols = intArrayOf(col(0x40804CFF), col(0x302C6BFF), col(0x30FF4C9A))
        val pos = floatArrayOf(0.25f, 0.35f, 0.6f, 0.75f, 0.6f, 0.55f, 0.4f, 0.85f, 0.4f)
        for (i in 0 until 3) {
            nebPos[i * 3] = pos[i * 3] * w
            nebPos[i * 3 + 1] = pos[i * 3 + 1] * h
            nebPos[i * 3 + 2] = pos[i * 3 + 2] * w
            neb[i].shader = RadialGradient(nebPos[i * 3], nebPos[i * 3 + 1], nebPos[i * 3 + 2],
                cols[i], cols[i] and 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        val r = Random(8)
        for (i in 0 until 180) {
            dust[i * 3] = r.nextFloat() * w
            dust[i * 3 + 1] = r.nextFloat() * h
            dust[i * 3 + 2] = 0.4f + r.nextFloat() * 0.9f
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float) {
        if (w != lw || h != lh) setup(w, h)
        val u = w / 400f
        c.drawRect(0f, 0f, w, h, bg)
        val pv = par.step(env.tiltX.coerceIn(-1f, 1f), dt)
        for (i in 0 until 3) c.drawCircle(nebPos[i * 3] + pv * 2f * u, nebPos[i * 3 + 1], nebPos[i * 3 + 2], neb[i])
        for (i in 0 until 180) {
            dustP.alpha = ((0.25f + 0.35f * (0.5f + 0.5f * sin(t * (0.7f + hashF(i + 3)) + i))) * 255).toInt()
            c.drawCircle(dust[i * 3] + pv * 4f * u, dust[i * 3 + 1], dust[i * 3 + 2] * u, dustP)
        }

        val count = floor(level * n + 0.0001f).toInt().coerceIn(0, n)
        for (i in 0 until n) {
            if (i < count) {
                if (appear[i] < 0f) appear[i] = if (started) t else t - 10f
            } else appear[i] = -1f
        }
        started = true

        // Parallax: the constellation layer moves most when you tilt
        val ofs = pv * 10f * u
        c.save()
        c.translate(ofs, 0f)

        // Taps light a new star, which links itself to its two nearest neighbours
        for (tap in env.taps) {
            val x = tap[0] - ofs
            val y = tap[1]
            var a = -1; var b = -1; var da = Float.MAX_VALUE; var db = Float.MAX_VALUE
            for (i in 0 until count) {
                val dx = sx[i] * w - x
                val dy = sy[i] * h - y
                val d = dx * dx + dy * dy
                if (d < da) { db = da; b = a; da = d; a = i } else if (d < db) { db = d; b = i }
            }
            temps.add(Temp(x, y, 0f, a, b))
            if (temps.size > 12) temps.removeAt(0)
        }

        linkP.strokeWidth = 1.1f * u
        for (e in edgeA.indices) {
            val a = edgeA[e]
            val b = edgeB[e]
            if (b >= count) continue
            val p = clamp01((t - appear[b]) / 0.9f)
            linkP.alpha = 95
            val ax = sx[a] * w; val ay = sy[a] * h
            c.drawLine(ax, ay, lerp(ax, sx[b] * w, p), lerp(ay, sy[b] * h, p), linkP)
        }

        for (i in 0 until count) {
            val x = sx[i] * w
            val y = sy[i] * h
            val tw = 0.7f + 0.3f * sin(t * 2.3f + i * 1.9f)
            Glow.draw(c, col(0xFF9FD8FF), x, y, 11f * u * tw, 0.75f)
            c.drawCircle(x, y, 1.8f * u, coreP)
            val age = t - appear[i]
            if (age < 2f) {
                val f = 1f - age / 2f
                Glow.draw(c, col(0xFFFFFFFF), x, y, 48f * u * f, f * 0.9f)
                flareP.strokeWidth = 1.2f * u
                flareP.alpha = (f * 220).toInt()
                val l = 34f * u * f
                c.drawLine(x - l, y, x + l, y, flareP)
                c.drawLine(x, y - l, x, y + l, flareP)
            }
        }

        // A pulse of light travelling along the chain
        if (count > 1) {
            val pos = (t * 2.5f) % (count - 1).toFloat()
            val i0 = pos.toInt()
            val fr = pos - i0
            val px = lerp(sx[i0], sx[i0 + 1], fr) * w
            val py = lerp(sy[i0], sy[i0 + 1], fr) * h
            Glow.draw(c, col(0xFF7FE8FF), px, py, 15f * u, 0.85f)
        }

        val tit = temps.iterator()
        while (tit.hasNext()) {
            val s = tit.next()
            s.age += dt
            if (s.age > 6f) { tit.remove(); continue }
            val life = clamp01((6f - s.age) / 2f)
            val grow = clamp01(s.age / 0.7f)
            linkP.alpha = (120 * life).toInt()
            for (k in intArrayOf(s.a, s.b)) {
                if (k < 0) continue
                c.drawLine(s.x, s.y, lerp(s.x, sx[k] * w, grow), lerp(s.y, sy[k] * h, grow), linkP)
            }
            val f = clamp01(1f - s.age / 1.5f)
            Glow.draw(c, col(0xFFFFE9B0), s.x, s.y, 14f * u, life)
            Glow.draw(c, col(0xFFFFFFFF), s.x, s.y, 56f * u * f, f)
            flareP.strokeWidth = 1.2f * u
            flareP.alpha = (f * 230).toInt()
            c.drawLine(s.x - 38f * u * f, s.y, s.x + 38f * u * f, s.y, flareP)
            c.drawLine(s.x, s.y - 38f * u * f, s.x, s.y + 38f * u * f, flareP)
            c.drawCircle(s.x, s.y, 2.2f * u, coreP)
        }
        c.restore()

        // Occasional shooting star
        val cycle = floor(t / 6.5f).toInt()
        val ph = (t % 6.5f) / 1.1f
        if (ph < 1f) {
            val x0 = (0.35f + hashF(cycle * 7) * 0.6f) * w
            val y0 = (0.04f + hashF(cycle * 11 + 3) * 0.22f) * h
            val dx = -0.9f
            val dy = 0.44f
            val len = sqrt(dx * dx + dy * dy)
            val hx = x0 + dx / len * ph * w * 0.6f
            val hy = y0 + dy / len * ph * w * 0.6f
            val tail = 0.2f * w * sin(ph * 3.14159f)
            flareP.strokeWidth = 1.6f * u
            flareP.alpha = ((1f - ph) * 230).toInt()
            c.drawLine(hx, hy, hx - dx / len * tail, hy - dy / len * tail, flareP)
            Glow.draw(c, col(0xFFFFFFFF), hx, hy, 10f * u, 1f - ph)
        }
    }
}
