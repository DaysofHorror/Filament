package com.daysofhorror.filament

import android.graphics.Canvas
import com.daysofhorror.filament.scenes.AuroraScene
import com.daysofhorror.filament.scenes.BloomScene
import com.daysofhorror.filament.scenes.ClockworkScene
import com.daysofhorror.filament.scenes.ConstellationScene
import com.daysofhorror.filament.scenes.FirefliesScene
import com.daysofhorror.filament.scenes.SpecimenScene
import com.daysofhorror.filament.scenes.TideScene

/** What the outside world is doing to the scene this frame. */
class Env {
    /** Gravity across the screen, -1..1. +X = right side down. Phone flat on a table = 0. */
    var tiltX = 0f
    var tiltY = 0f

    /** Finger currently held on the screen (scene coordinates). */
    var touching = false
    var touchX = 0f
    var touchY = 0f

    /** Taps that landed this frame (x, y in scene coordinates). Cleared after each frame. */
    val taps = ArrayList<FloatArray>()

    /** 0 = scene unpowered (before the surge hits), 1 = fully on. Flickers while powering up. */
    var power = 1f
}

/** Simple spring for sloshing / wobbling. */
class Spring(private val stiffness: Float, private val damping: Float) {
    var x = 0f
    var v = 0f
    fun step(target: Float, dt: Float): Float {
        if (dt <= 0f) return x
        val steps = 2
        val h = dt / steps
        repeat(steps) {
            val a = (target - x) * stiffness - v * damping
            v += a * h
            x += v * h
        }
        return x
    }
}

/**
 * One charging animation.
 * t  = scene time in seconds (slows to a stop when the battery is full)
 * dt = scene time since the last frame (0 once stopped)
 * level = battery 0..1
 */
abstract class Scene {
    var env = Env()
    abstract fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float)
}

class SceneInfo(
    val id: String,
    val title: String,
    val blurb: String,
    val accent: Int,
    val make: () -> Scene
)

object Scenes {
    val all = listOf(
        SceneInfo("tide", "Tide", "The sea rises with your charge", col(0xFF4FE6D6)) { TideScene() },
        SceneInfo("fireflies", "Fireflies", "One firefly wakes for every 1%", col(0xFFC8FF6A)) { FirefliesScene() },
        SceneInfo("aurora", "Aurora", "Northern lights over a lonely bothy", col(0xFF6DFFB0)) { AuroraScene() },
        SceneInfo("clockwork", "Clockwork", "Victorian brass gauge and gears", col(0xFFE8B95A)) { ClockworkScene() },
        SceneInfo("constellation", "Constellation", "A new star for every 1%", col(0xFF9FD8FF)) { ConstellationScene() },
        SceneInfo("bloom", "Bloom", "A tree grows, then blossoms at full", col(0xFFFF9EC4)) { BloomScene() },
        SceneInfo("specimen", "Specimen", "A sealed vial fills with live culture", col(0xFF8CFF4A)) { SpecimenScene() },
    )

    fun byId(id: String?): SceneInfo = all.firstOrNull { it.id == id } ?: all[0]
}
