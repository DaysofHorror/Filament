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
import com.daysofhorror.filament.TAU
import com.daysofhorror.filament.clamp01
import com.daysofhorror.filament.col
import com.daysofhorror.filament.hashF
import com.daysofhorror.filament.lerp
import com.daysofhorror.filament.withAlpha
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.random.Random

/** Water fills the screen to the charge level, with rolling waves, light rays and bubbles. */
class TideScene : Scene() {
    private var lw = -1f
    private var lh = -1f
    private val bg = Paint()
    private val deep = Paint(Paint.ANTI_ALIAS_FLAG)
    private val front = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crest = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = col(0xB0E8FFFB)
    }
    private val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Path()
    private val surface = Path()
    private val rayPath = Path()
    private val rnd = Random(11)

    private class Bubble(var x: Float, var y: Float, var r: Float, var speed: Float, var phase: Float)
    private val bubbles = ArrayList<Bubble>()
    private val slosh = Spring(16f, 1.8f)
    private val ripples = ArrayList<FloatArray>() // x, y, age
    private val pops = ArrayList<FloatArray>()    // x, y, age

    private fun setup(w: Float, h: Float) {
        lw = w; lh = h
        bg.shader = LinearGradient(0f, 0f, 0f, h, col(0xFF07142A), col(0xFF01040B), Shader.TileMode.CLAMP)
        deep.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(col(0xFF1A9FB5), col(0xFF0B4F7A), col(0xFF041A36)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        front.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(col(0xFF5FF2E0), col(0xFF139AB8), col(0xFF06305C)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        bubbles.clear()
        repeat(46) {
            val b = Bubble(0f, 0f, 0f, 0f, 0f)
            respawn(b, w, h)
            b.y = rnd.nextFloat() * h
            bubbles.add(b)
        }
    }

    private fun respawn(b: Bubble, w: Float, h: Float) {
        val u = w / 400f
        b.x = rnd.nextFloat() * w
        b.y = h + rnd.nextFloat() * 80f * u
        b.r = (1.5f + rnd.nextFloat() * rnd.nextFloat() * 7f) * u
        b.speed = (25f + rnd.nextFloat() * 50f) * u + b.r * 5f
        b.phase = rnd.nextFloat() * TAU
    }

    private fun wave(x: Float, base: Float, amp: Float, w: Float, t: Float, dir: Float): Float {
        val k = TAU / w
        return base +
            amp * sin(x * k * 1.3f + t * 0.9f * dir) +
            amp * 0.45f * sin(x * k * 3.1f - t * 1.7f * dir + 1.3f) +
            amp * 0.2f * sin(x * k * 7.3f + t * 2.9f)
    }

    override fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float) {
        if (w != lw || h != lh) setup(w, h)
        val u = w / 400f
        c.drawRect(0f, 0f, w, h, bg)

        val s = lerp(h * 0.985f, h * 0.035f, level)
        val step = 6f * u
        // Tilt: water piles up on the low side and sloshes back and forth
        val tilt = slosh.step(env.tiltX.coerceIn(-0.8f, 0.8f) * 0.3f, dt)
        val chop = min(1f, abs(slosh.v) * 3f)
        val amp = (12f + 10f * chop) * u
        fun surfAt(x: Float) = wave(x, s, amp, w, t, 1f) - tilt * (x - w / 2f)

        // Back wave
        body.rewind()
        body.moveTo(0f, h)
        var x = 0f
        while (x <= w + step) {
            body.lineTo(x, wave(x, s - 10f * u, 9f * u, w, t + 3f, -1f) - tilt * 0.9f * (x - w / 2f))
            x += step
        }
        body.lineTo(w + step, h)
        body.close()
        c.drawPath(body, deep)

        // Front wave + its crest line
        body.rewind(); surface.rewind()
        body.moveTo(0f, h)
        x = 0f
        var first = true
        while (x <= w + step) {
            val y = surfAt(x)
            body.lineTo(x, y)
            if (first) { surface.moveTo(x, y); first = false } else surface.lineTo(x, y)
            x += step
        }
        body.lineTo(w + step, h)
        body.close()
        c.drawPath(body, front)

        // Light rays slanting down from the surface
        ray.shader = LinearGradient(0f, s, 0f, s + h * 0.6f, col(0x34CFFFF8), col(0x00CFFFF8), Shader.TileMode.CLAMP)
        for (i in 0 until 4) {
            val cx = w * (0.12f + 0.26f * i) + sin(t * 0.3f + i * 1.9f) * 30f * u
            val top = 16f * u + hashF(i) * 20f * u
            val bot = 70f * u + hashF(i + 9) * 60f * u
            val skew = 70f * u
            rayPath.rewind()
            rayPath.moveTo(cx - top, s)
            rayPath.lineTo(cx + top, s)
            rayPath.lineTo(cx + bot + skew, s + h * 0.6f)
            rayPath.lineTo(cx - bot + skew, s + h * 0.6f)
            rayPath.close()
            ray.alpha = ((0.5f + 0.5f * sin(t * 0.45f + i * 2.3f)) * 255).toInt()
            c.drawPath(rayPath, ray)
        }

        crest.strokeWidth = 2.2f * u
        c.drawPath(surface, crest)

        // Taps: a ripple, and nearby bubbles pop
        for (tap in env.taps) {
            ripples.add(floatArrayOf(tap[0], tap[1], 0f))
            for (b in bubbles) {
                val dx = b.x - tap[0]
                val dy = b.y - tap[1]
                if (sqrt(dx * dx + dy * dy) < 80f * u && b.y > surfAt(b.x)) {
                    pops.add(floatArrayOf(b.x, b.y, 0f))
                    respawn(b, w, h)
                }
            }
        }

        // Bubbles rise and pop at the surface
        ring.strokeWidth = max(1f, 1.2f * u)
        for (b in bubbles) {
            b.y -= b.speed * dt
            val bx = b.x + sin(t * 2.2f + b.phase) * 5f * u
            val surfY = surfAt(bx)
            if (b.y < surfY + b.r) {
                if (dt > 0f) respawn(b, w, h)
                continue
            }
            if (b.y > h + b.r) continue
            val a = clamp01((b.y - surfY) / (30f * u))
            ring.color = withAlpha(col(0xFFE6FFFF), (150 * a).toInt())
            c.drawCircle(bx, b.y, b.r, ring)
            dot.color = withAlpha(col(0xFFFFFFFF), (190 * a).toInt())
            c.drawCircle(bx - b.r * 0.35f, b.y - b.r * 0.35f, b.r * 0.25f, dot)
        }

        // Glints riding the surface
        for (i in 0 until 10) {
            val gx = (hashF(i) * w + t * (8f + hashF(i + 50) * 14f) * u) % w
            val gy = surfAt(gx)
            val a = 0.5f + 0.5f * sin(t * 3f + i * 1.7f)
            Glow.draw(c, col(0xFFDFFFFA), gx, gy, 10f * u, a * 0.8f)
        }

        // Ripples and pops from taps
        val rit = ripples.iterator()
        while (rit.hasNext()) {
            val r = rit.next()
            r[2] += dt
            if (r[2] > 1.3f) { rit.remove(); continue }
            val a = 1f - r[2] / 1.3f
            ring.strokeWidth = 2.5f * u * a + 0.5f
            ring.color = withAlpha(col(0xFFE6FFFF), (200 * a).toInt())
            c.drawCircle(r[0], r[1], 12f * u + r[2] * 150f * u, ring)
            c.drawCircle(r[0], r[1], 4f * u + r[2] * 90f * u, ring)
        }
        val pit = pops.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p[2] += dt
            if (p[2] > 0.35f) { pit.remove(); continue }
            Glow.draw(c, col(0xFFFFFFFF), p[0], p[1], 6f * u + p[2] * 60f * u, 1f - p[2] / 0.35f)
        }
    }
}
