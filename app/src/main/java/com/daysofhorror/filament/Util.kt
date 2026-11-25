package com.daysofhorror.filament

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

const val PIf = 3.1415927f
const val TAU = 6.2831855f

fun col(v: Long): Int = v.toInt()
fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val k = clamp01(t)
    fun ch(s: Int) = (((a shr s) and 255) + (((b shr s) and 255) - ((a shr s) and 255)) * k).toInt()
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Cheap deterministic 0..1 hash so shapes stay the same every frame. */
fun hashF(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

/** Soft additive glows, pre-rendered once per colour. */
object Glow {
    private val cache = HashMap<Int, Bitmap>()
    private val dst = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    private fun bitmap(color: Int): Bitmap = cache.getOrPut(color) {
        val s = 128
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(color, withAlpha(color, 150), withAlpha(color, 40), withAlpha(color, 0)),
            floatArrayOf(0f, 0.16f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        Canvas(b).drawCircle(s / 2f, s / 2f, s / 2f, p)
        b
    }

    fun draw(c: Canvas, color: Int, x: Float, y: Float, r: Float, alpha: Float) {
        if (alpha <= 0.004f || r <= 0.5f) return
        paint.alpha = (clamp01(alpha) * 255).toInt()
        dst.set(x - r, y - r, x + r, y + r)
        c.drawBitmap(bitmap(color), null, dst, paint)
    }
}
