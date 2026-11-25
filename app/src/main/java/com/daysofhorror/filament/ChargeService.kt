package com.daysofhorror.filament

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings

/**
 * Android no longer lets apps hear "charger connected" unless they're running,
 * so this small service stays alive (with a silent, minimised notification)
 * and opens the charging screen the moment you plug in.
 */
class ChargeService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ showCharging() }, 700)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    handler.removeCallbacksAndMessages(null)
                    Notifs.cancelAlert(this@ChargeService)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)
        val n = Notifs.standby(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifs.ID_STANDBY, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifs.ID_STANDBY, n)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showCharging() {
        if (!Prefs.enabled(this)) return
        val bm = getSystemService(BatteryManager::class.java)
        if (bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) >= 100) return
        val i = Intent(this, ChargeActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        )
        if (Settings.canDrawOverlays(this)) {
            try {
                startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        Notifs.alert(this, i)
    }

    companion object {
        fun start(c: Context) {
            try {
                c.startForegroundService(Intent(c, ChargeService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, ChargeService::class.java))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.enabled(context)) ChargeService.start(context)
    }
}
