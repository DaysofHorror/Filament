package com.daysofhorror.filament

import android.graphics.Canvas
import com.daysofhorror.filament.scenes.AuroraScene
import com.daysofhorror.filament.scenes.BloomScene
import com.daysofhorror.filament.scenes.ClockworkScene
import com.daysofhorror.filament.scenes.ConstellationScene
import com.daysofhorror.filament.scenes.FirefliesScene
import com.daysofhorror.filament.scenes.SpecimenScene
import com.daysofhorror.filament.scenes.TideScene

/**
 * One charging animation.
 * t  = scene time in seconds (slows to a stop when the battery is full)
 * dt = scene time since the last frame (0 once stopped)
 * level = battery 0..1
 */
interface Scene {
    fun draw(c: Canvas, w: Float, h: Float, t: Float, dt: Float, level: Float)
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
