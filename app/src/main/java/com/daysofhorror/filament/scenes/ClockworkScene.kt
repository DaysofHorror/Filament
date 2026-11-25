package com.daysofhorror.filament.scenes

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.daysofhorror.filament.Glow
import com.daysofhorror.filament.PIf
import com.daysofhorror.filament.Scene
import com.daysofhorror.filament.TAU
import com.daysofhorror.filament.col
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** A Victorian brass charge gauge with properly meshing gears turning behind it. */
class ClockworkScene : Scene {

    private class Gear(val cx: Float, val cy: Float, val n: Int, val r: Float, val phase: Float, val ratio: Float) {
        val path = Path()
        var shader: Shader? = null
    }

    private val gears = ArrayList<Gear>()
    private var lw = -1f
    private var lh = -1f
    private var angle = 0f

    private val bg = Paint()
    private val gearFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gearEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = col(0xFF2A1806)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0x80000000) }
    private val axle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF1E1206) }
    private val bezel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xFF2B1D10)
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = col(0xAA3F8F4A)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xFF2B1D10)
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF1A120A) }
    private val needleTip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xFFB3261E)
        strokeCap = Paint.Cap.ROUND
    }
    private val glare = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = col(0x30FFFFFF)
    }
    private val rivet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF6B4414) }
    private val pipe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePath = Path()
    private val oval = RectF()
    private val tmp = Path()
    private val m = Matrix()

    private class Puff(var x: Float, var y: Float, var vx: Float, var life: Float)
    private val puffs = ArrayList<Puff>()
    private var puffAcc = 0f
    private val rnd = Random(9)

    private var gx = 0f
    private var gy = 0f
    private var gr = 0f

    private fun setup(w: Float, h: Float) {
        lw = w; lh = h
        gears.clear()
        val mod = w * 0.0075f

        fun master(cx: Float, cy: Float, n: Int, ratio: Float) =
            Gear(cx, cy, n, n * mod, 0f, ratio).also { gears.add(it) }

        fun mesh(p: Gear, n: Int, deg: Float): Gear {
            val th = deg / 180f * PIf
            val r = n * mod
            val d = p.r + r
            // Phase chosen so a gap on this gear sits exactly where the parent's tooth is.
            val ph = th + PIf + (th - p.phase) * p.n / n - PIf / n
            return Gear(p.cx + cos(th) * d, p.cy + sin(th) * d, n, r, ph, -p.ratio * p.n / n).also { gears.add(it) }
        }

        val a = master(w * 0.2f, h * 0.84f, 28, 1f)
        val b = mesh(a, 12, -55f)
        val c = mesh(b, 22, -5f)
        mesh(c, 16, 60f)
        mesh(a, 18, 160f)
        val e = master(w * 0.95f, h * 0.35f, 24, -0.7f)
        mesh(e, 14, 205f)
        mesh(e, 18, 100f)
        val hg = master(w * 0.02f, h * 0.4f, 20, 0.9f)
        mesh(hg, 12, -35f)

        for (g in gears) buildGear(g, mod)

        gx = w * 0.5f
        gy = h * 0.58f
        gr = w * 0.33f
        bg.shader = RadialGradient(w * 0.5f, h * 0.55f, max(w, h) * 0.75f, col(0xFF2E1D10), col(0xFF080402), Shader.TileMode.CLAMP)
        bezel.shader = RadialGradient(gx - gr * 0.3f, gy - gr * 0.3f, gr * 1.6f,
            intArrayOf(col(0xFFF6DB95), col(0xFFC28F3C), col(0xFF5E3B12)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        face.shader = RadialGradient(gx, gy - gr * 0.2f, gr * 1.1f, col(0xFFF7EDD3), col(0xFFC8B386), Shader.TileMode.CLAMP)
        pipe.shader = bezel.shader
    }

    private fun buildGear(g: Gear, mod: Float) {
        val p = g.path
        p.rewind()
        val tip = g.r + mod * 0.85f
        val root = g.r - mod * 0.95f
        val hp = PIf / g.n
        fun pt(r: Float, a: Float, move: Boolean = false) {
            if (move) p.moveTo(cos(a) * r, sin(a) * r) else p.lineTo(cos(a) * r, sin(a) * r)
        }
        pt(root, -hp, true)
        for (k in 0 until g.n) {
            val a = TAU * k / g.n
            pt(root, a - hp * 0.55f)
            pt(tip, a - hp * 0.3f)
            pt(tip, a + hp * 0.3f)
            pt(root, a + hp * 0.55f)
            pt(root, a + hp)
        }
        p.close()
        p.addCircle(0f, 0f, g.r * 0.7f, Path.Direction.CCW)
        val spokes = if (g.n < 16) 4 else 6
        val sw = g.r * 0.13f
        for (s in 0 until spokes) {
            tmp.rewind()
            tmp.addRect(-sw / 2f, 0f, sw / 2f, g.r * 0.74f, Path.Direction.CW)
            m.setRotate(s * 360f / spokes)
            tmp.transform(m)
            p.addPath(tmp)
        }
        p.addCircle(0f, 0f, g.r * 0.24f, Path.Direction.CW)
        g.shader = RadialGradient(0f, 0f, tip,
            intArrayOf(col(0xFFF3D58A), col(0xFFC9973F), col(0xFF7A4E1A)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float) {
        if (w != lw || h != lh) setup(w, h)
        val u = w / 400f
        c.drawRect(0f, 0f, w, h, bg)

        angle += dt * (0.25f + 0.35f * level)

        gearEdge.strokeWidth = 1.3f * u
        for (g in gears) {
            val deg = (g.phase + g.ratio * angle) * 180f / PIf
            c.save()
            c.translate(g.cx + 4f * u, g.cy + 6f * u)
            c.rotate(deg)
            c.drawPath(g.path, shadow)
            c.restore()
            c.save()
            c.translate(g.cx, g.cy)
            c.rotate(deg)
            gearFill.shader = g.shader
            c.drawPath(g.path, gearFill)
            c.drawPath(g.path, gearEdge)
            c.drawCircle(0f, 0f, g.r * 0.08f, axle)
            c.restore()
        }

        drawGauge(c, u, t, level)
        drawSteam(c, u, dt)
    }

    private fun drawGauge(c: Canvas, u: Float, t: Float, level: Float) {
        val r = gr
        // Steam pipe on top
        c.drawRect(gx - r * 0.09f, gy - r * 1.42f, gx + r * 0.09f, gy - r, pipe)
        c.drawRect(gx - r * 0.16f, gy - r * 1.46f, gx + r * 0.16f, gy - r * 1.36f, pipe)

        c.drawCircle(gx + 5f * u, gy + 8f * u, r * 1.12f, shadow)
        c.drawCircle(gx, gy, r * 1.12f, bezel)
        c.drawCircle(gx, gy, r * 1.12f, gearEdge)
        for (i in 0 until 12) {
            val a = TAU * i / 12f
            c.drawCircle(gx + cos(a) * r * 1.06f, gy + sin(a) * r * 1.06f, r * 0.025f, rivet)
        }
        c.drawCircle(gx, gy, r, face)
        c.drawCircle(gx, gy, r, gearEdge)

        // Green "full" band from 80 to 100
        arc.strokeWidth = r * 0.05f
        oval.set(gx - r * 0.95f, gy - r * 0.95f, gx + r * 0.95f, gy + r * 0.95f)
        c.drawArc(oval, 135f + 216f, 54f, false, arc)

        for (v in 0..100 step 2) {
            val a = (135f + v * 2.7f) / 180f * PIf
            val major = v % 10 == 0
            val r1 = r * 0.92f
            val r2 = if (major) r * 0.78f else r * 0.86f
            tick.strokeWidth = if (major) r * 0.022f else r * 0.01f
            c.drawLine(gx + cos(a) * r1, gy + sin(a) * r1, gx + cos(a) * r2, gy + sin(a) * r2, tick)
        }
        text.textSize = r * 0.11f
        text.letterSpacing = 0f
        for (v in 0..100 step 20) {
            val a = (135f + v * 2.7f) / 180f * PIf
            c.drawText(v.toString(), gx + cos(a) * r * 0.63f, gy + sin(a) * r * 0.63f + r * 0.04f, text)
        }
        text.textSize = r * 0.085f
        text.letterSpacing = 0.25f
        c.drawText("CHARGE", gx, gy + r * 0.4f, text)
        text.textSize = r * 0.05f
        c.drawText("FILAMENT & CO.", gx, gy + r * 0.53f, text)

        // Needle with a little steam-pressure tremble (still at full)
        val jitter = sin(t * 31f) * 0.35f + sin(t * 17f) * 0.25f
        val deg = 135f + level * 270f + jitter
        c.save()
        c.rotate(deg, gx, gy)
        needlePath.rewind()
        needlePath.moveTo(gx - r * 0.16f, gy - r * 0.03f)
        needlePath.lineTo(gx + r * 0.86f, gy)
        needlePath.lineTo(gx - r * 0.16f, gy + r * 0.03f)
        needlePath.close()
        c.drawPath(needlePath, needle)
        needleTip.strokeWidth = r * 0.02f
        c.drawLine(gx + r * 0.6f, gy, gx + r * 0.85f, gy, needleTip)
        c.restore()
        c.drawCircle(gx, gy, r * 0.075f, bezel)
        c.drawCircle(gx, gy, r * 0.025f, axle)

        glare.strokeWidth = r * 0.06f
        oval.set(gx - r * 0.82f, gy - r * 0.82f, gx + r * 0.82f, gy + r * 0.82f)
        c.drawArc(oval, 200f, 55f, false, glare)
    }

    private fun drawSteam(c: Canvas, u: Float, dt: Float) {
        if (dt > 0f) {
            puffAcc += dt * 3.5f
            while (puffAcc >= 1f) {
                puffAcc -= 1f
                puffs.add(Puff(gx + (rnd.nextFloat() - 0.5f) * 6f * u, gy - gr * 1.46f, (rnd.nextFloat() - 0.5f) * 30f * u, 0f))
            }
        }
        val it = puffs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            p.y -= 45f * u * dt
            p.x += p.vx * dt
            if (p.life > 2.6f) { it.remove(); continue }
            Glow.draw(c, col(0xFF8C8478), p.x, p.y, 10f * u + p.life * 28f * u, (1f - p.life / 2.6f) * 0.3f)
        }
    }
}
