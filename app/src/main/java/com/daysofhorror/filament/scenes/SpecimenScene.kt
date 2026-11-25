package com.daysofhorror.filament.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.daysofhorror.filament.Glow
import com.daysofhorror.filament.Scene
import com.daysofhorror.filament.TAU
import com.daysofhorror.filament.clamp01
import com.daysofhorror.filament.col
import com.daysofhorror.filament.lerp
import com.daysofhorror.filament.withAlpha
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A sealed containment vial in a dark lab. It fills with glowing viral culture as you charge. */
class SpecimenScene : Scene {
    private var lw = -1f
    private var lh = -1f
    private val bg = Paint()
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0x0E7CFF4F) }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = col(0x66CFFFE0)
    }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        color = col(0x30FFFFFF)
    }
    private val fluid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val surfaceP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = col(0xFFCBFF9A)
    }
    private val steel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val steelEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = col(0xFF0B0E10)
    }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF2A3036) }
    private val stripeY = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFFE8B714) }
    private val stripeK = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFF15171A) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xE6E9EDE6) }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col(0xFF1A1D1F)
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val hazardRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col(0xFFC0221B) }
    private val virus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        color = col(0xFFB6FF6E)
    }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tube = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = col(0xFF1C2226)
    }

    private val vialPath = Path()
    private val fluidPath = Path()
    private val tubePath = Path()
    private val stripeClip = Path()
    private val r = RectF()

    // vial geometry
    private var vx0 = 0f
    private var vx1 = 0f
    private var vy0 = 0f
    private var vy1 = 0f
    private var capH = 0f

    private class Virion(var x: Float, var y: Float, var size: Float, var speed: Float, var phase: Float, var spin: Float)
    private class Bub(var x: Float, var y: Float, var r: Float, var speed: Float)

    private val virions = ArrayList<Virion>()
    private val bubbles = ArrayList<Bub>()
    private val rnd = Random(66)

    private fun setup(w: Float, h: Float) {
        lw = w; lh = h
        val u = w / 400f
        bg.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(col(0xFF05090A), col(0xFF0B1612), col(0xFF040605)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)

        val vw = w * 0.34f
        vx0 = w / 2f - vw / 2f
        vx1 = w / 2f + vw / 2f
        capH = 34f * u
        vy0 = h * 0.33f + capH
        vy1 = h * 0.84f - capH
        val rad = vw * 0.22f
        vialPath.rewind()
        r.set(vx0, vy0, vx1, vy1)
        vialPath.addRoundRect(r, rad, rad, Path.Direction.CW)

        glass.shader = LinearGradient(vx0, 0f, vx1, 0f,
            intArrayOf(col(0x30A8FFD0), col(0x0AA8FFD0), col(0x14A8FFD0), col(0x36A8FFD0)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        fluid.shader = LinearGradient(0f, vy0, 0f, vy1,
            intArrayOf(col(0xF09CFF3C), col(0xF045C21C), col(0xF0145A12)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        steel.shader = LinearGradient(vx0 - 20f * u, 0f, vx1 + 20f * u, 0f,
            intArrayOf(col(0xFF3A4249), col(0xFFAEB8BF), col(0xFF59636B), col(0xFF2A3036)),
            floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP)

        // Feed pipe from the top cap up and off to the side
        tubePath.rewind()
        tubePath.moveTo(w / 2f, h * 0.33f - capH * 0.2f)
        tubePath.cubicTo(w / 2f, h * 0.28f, w * 0.95f, h * 0.31f, w * 1.1f, h * 0.27f)

        virions.clear()
        repeat(24) {
            virions.add(Virion(
                rnd.nextFloat(), vy0 + rnd.nextFloat() * (vy1 - vy0),
                (5f + rnd.nextFloat() * 7f) * u, (6f + rnd.nextFloat() * 14f) * u,
                rnd.nextFloat() * TAU, (rnd.nextFloat() - 0.5f) * 1.6f
            ))
        }
        bubbles.clear()
        repeat(30) { bubbles.add(Bub(rnd.nextFloat(), vy1 - rnd.nextFloat() * (vy1 - vy0), (1.2f + rnd.nextFloat() * 3f) * u, (30f + rnd.nextFloat() * 60f) * u)) }
    }

    override fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float) {
        if (w != lw || h != lh) setup(w, h)
        val u = w / 400f
        val cx = w / 2f

        c.drawRect(0f, 0f, w, h, bg)
        // Faint lab grid
        val gs = 32f * u
        var gx = (w / 2f) % gs
        while (gx < w) { c.drawRect(gx, 0f, gx + 1f, h, grid); gx += gs }
        var gy = 0f
        while (gy < h) { c.drawRect(0f, gy, w, gy + 1f, grid); gy += gs }

        // Containment glow grows with the culture
        Glow.draw(c, col(0xFF6CFF3A), cx, lerp(vy1, vy0, level * 0.6f), w * (0.45f + 0.25f * level), 0.18f + 0.42f * level)

        c.drawPath(tubePath, tube.apply { strokeWidth = 14f * u })

        // Glass back
        c.drawPath(vialPath, glass)

        // Fluid, clipped to the inside of the vial
        val inset = 5f * u
        val top = vy0 + inset
        val bot = vy1 - inset
        val surf = lerp(bot, top - 6f * u, level)
        c.save()
        c.clipPath(vialPath)
        if (level > 0.003f) {
            fluidPath.rewind()
            fluidPath.moveTo(vx0, vy1 + 10f)
            var x = vx0
            val step = 4f * u
            while (x <= vx1 + step) {
                val k = (x - vx0) / (vx1 - vx0)
                fluidPath.lineTo(x, surf + sin(k * 9f + t * 2.4f) * 2.6f * u + sin(k * 17f - t * 3.7f) * 1.2f * u)
                x += step
            }
            fluidPath.lineTo(vx1 + step, vy1 + 10f)
            fluidPath.close()
            c.drawPath(fluidPath, fluid)

            // Bubbles
            for (b in bubbles) {
                b.y -= b.speed * dt
                if (b.y < surf + b.r) { b.y = bot - rnd.nextFloat() * 20f * u; b.x = rnd.nextFloat() }
                if (b.y < surf) continue
                val bx = lerp(vx0 + 10f * u, vx1 - 10f * u, b.x) + sin(t * 3f + b.r) * 2f * u
                bubble.strokeWidth = 1f * u
                bubble.color = withAlpha(col(0xFFE6FFD0), 150)
                c.drawCircle(bx, b.y, b.r, bubble)
            }

            // Spiked virus particles drifting in the culture
            val shownCount = (virions.size * (0.15f + 0.85f * level)).toInt().coerceAtLeast(2)
            for ((idx, v) in virions.withIndex()) {
                if (idx >= shownCount) break
                v.y -= v.speed * dt
                if (v.y < surf + v.size * 2f || v.y > bot) {
                    v.y = bot - rnd.nextFloat() * (bot - surf) * 0.5f
                    v.x = rnd.nextFloat()
                }
                if (v.y < surf + v.size) continue
                val px = lerp(vx0 + 18f * u, vx1 - 18f * u, v.x) + sin(t * 0.7f + v.phase) * 10f * u
                val a = clamp01((v.y - surf) / (24f * u))
                Glow.draw(c, col(0xFF9CFF4A), px, v.y, v.size * 3.4f, 0.45f * a)
                drawVirion(c, px, v.y, v.size, t * v.spin + v.phase, a)
            }

            // Meniscus line glowing on top
            surfaceP.strokeWidth = 2f * u
            c.drawLine(vx0, surf, vx1, surf, surfaceP)
            Glow.draw(c, col(0xFFCBFF9A), cx, surf, (vx1 - vx0) * 0.6f, 0.35f)
        }
        c.restore()

        // Glass front: edge + reflections
        glassEdge.strokeWidth = 2.5f * u
        c.drawPath(vialPath, glassEdge)
        shine.strokeWidth = 7f * u
        c.drawLine(vx0 + 14f * u, vy0 + 30f * u, vx0 + 14f * u, vy1 - 40f * u, shine)
        shine.strokeWidth = 3f * u
        c.drawLine(vx1 - 16f * u, vy0 + 50f * u, vx1 - 16f * u, vy0 + 120f * u, shine)

        // Label band with a hazard mark
        val ly0 = lerp(vy0, vy1, 0.58f)
        val ly1 = ly0 + 46f * u
        r.set(vx0 + 3f * u, ly0, vx1 - 3f * u, ly1)
        c.drawRect(r, label)
        c.drawRect(vx0 + 3f * u, ly0, vx1 - 3f * u, ly0 + 5f * u, hazardRed)
        labelText.textSize = 13f * u
        labelText.letterSpacing = 0.12f
        c.drawText("SPECIMEN 07", cx, ly0 + 22f * u, labelText)
        labelText.textSize = 8f * u
        labelText.letterSpacing = 0.2f
        c.drawText("LIVE CULTURE · DO NOT OPEN", cx, ly0 + 36f * u, labelText)

        // Steel caps with bolts
        drawCap(c, u, vy0 - capH, vy0 + 6f * u, true, t)
        drawCap(c, u, vy1 - 6f * u, vy1 + capH, false, t)

        // Hazard-striped plinth
        val py0 = vy1 + capH
        val py1 = py0 + 26f * u
        val px0 = vx0 - 40f * u
        val px1 = vx1 + 40f * u
        c.drawRect(px0, py0, px1, py1, stripeK)
        c.save()
        stripeClip.rewind()
        stripeClip.addRect(px0, py0 + 4f * u, px1, py1 - 4f * u, Path.Direction.CW)
        c.clipPath(stripeClip)
        var sx = px0 - 30f * u
        while (sx < px1 + 30f * u) {
            stripeClip.rewind()
            stripeClip.moveTo(sx, py1)
            stripeClip.lineTo(sx + 12f * u, py1)
            stripeClip.lineTo(sx + 30f * u, py0)
            stripeClip.lineTo(sx + 18f * u, py0)
            stripeClip.close()
            c.drawPath(stripeClip, stripeY)
            sx += 24f * u
        }
        c.restore()
        c.drawRect(px0 - 20f * u, py1, px1 + 20f * u, py1 + 10f * u, steel)
    }

    private fun drawCap(c: Canvas, u: Float, y0: Float, y1: Float, isTop: Boolean, t: Float) {
        val x0 = vx0 - 10f * u
        val x1 = vx1 + 10f * u
        r.set(x0, y0, x1, y1)
        c.drawRoundRect(r, 6f * u, 6f * u, steel)
        steelEdge.strokeWidth = 1.5f * u
        c.drawRoundRect(r, 6f * u, 6f * u, steelEdge)
        val my = (y0 + y1) / 2f
        for (i in 0 until 4) {
            val bx = lerp(x0 + 14f * u, x1 - 14f * u, i / 3f)
            c.drawCircle(bx, my, 3.5f * u, bolt)
        }
        if (isTop) {
            // Status lamp: blinking red while charging
            val on = 0.5f + 0.5f * sin(t * 4f)
            val lx = (vx0 + vx1) / 2f
            Glow.draw(c, col(0xFFFF3022), lx, y0 - 4f * u, 26f * u, 0.3f + 0.6f * on)
            c.drawCircle(lx, y0 - 4f * u, 4f * u, hazardRed)
        }
    }

    private fun drawVirion(c: Canvas, x: Float, y: Float, s: Float, rot: Float, a: Float) {
        virus.alpha = (a * 255).toInt()
        virus.strokeWidth = s * 0.16f
        val spikes = 11
        for (k in 0 until spikes) {
            val ang = rot + TAU * k / spikes
            val ca = cos(ang)
            val sa = sin(ang)
            c.drawLine(x + ca * s * 0.8f, y + sa * s * 0.8f, x + ca * s * 1.45f, y + sa * s * 1.45f, virus)
            c.drawCircle(x + ca * s * 1.5f, y + sa * s * 1.5f, s * 0.18f, virus)
        }
        c.drawCircle(x, y, s * 0.85f, virus)
        virus.color = col(0xFF2E7A1A)
        virus.alpha = (a * 255).toInt()
        c.drawCircle(x, y, s * 0.55f, virus)
        virus.color = col(0xFFB6FF6E)
    }
}
