package com.daysofhorror.filament

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("filament", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun sceneId(c: Context): String = sp(c).getString("scene", "tide") ?: "tide"
    fun setScene(c: Context, id: String) = sp(c).edit().putString("scene", id).apply()

    fun shuffle(c: Context) = sp(c).getBoolean("shuffle", false)
    fun setShuffle(c: Context, v: Boolean) = sp(c).edit().putBoolean("shuffle", v).apply()

    /** The animation to show this time: the chosen one, or a different random one when shuffling. */
    fun pickScene(c: Context): SceneInfo {
        if (!shuffle(c)) return Scenes.byId(sceneId(c))
        val last = sp(c).getString("last", "")
        val pick = Scenes.all.filter { it.id != last }.random()
        sp(c).edit().putString("last", pick.id).apply()
        return pick
    }
}

object Notifs {
    const val CH_STANDBY = "standby"
    const val CH_ALERT = "charging"
    const val ID_STANDBY = 1
    const val ID_ALERT = 2

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STANDBY, "Waiting for charger", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps Filament ready to show the animation when you plug in"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Charging screen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Used to open the charging animation if pop-ups are not allowed"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun standby(c: Context): Notification {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(c, CH_STANDBY)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Filament is ready")
            .setContentText("Plug in your charger to see your animation")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    fun alert(c: Context, intent: Intent) {
        val pi = PendingIntent.getActivity(
            c, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Charging")
            .setContentText("Tap to watch it charge")
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }

    fun cancelAlert(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(ID_ALERT)
    }
}
