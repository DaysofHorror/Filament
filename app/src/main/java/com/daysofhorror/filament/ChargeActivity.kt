package com.daysofhorror.filament

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import kotlin.math.min
import kotlin.math.roundToInt

/** The full-screen charging animation. Closes itself when you unplug, or on a double-tap. */
class ChargeActivity : Activity() {

    private lateinit var view: ChargeView
    private val handler = Handler(Looper.getMainLooper())
    private var demo = false
    private var registered = false
    private var sawPlugged = false
    private var unplugCheckPosted = false
    private var sleepPosted = false
    private var lastPct = -1
    private val ticks = ArrayDeque<Pair<Long, Int>>()
    private var demoPct = 55

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> finish()
                Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)
            }
        }
    }

    // After a minute at full charge, let the screen go to sleep as normal.
    private val allowSleep = Runnable {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        demo = intent.getBooleanExtra(EXTRA_DEMO, false)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val info = intent.getStringExtra(EXTRA_SCENE)?.let { Scenes.byId(it) } ?: Prefs.pickScene(this)
        view = ChargeView(this, info, overlay = true)
        view.hint = "Double-tap to close"
        setContentView(view)
        hideBars()

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                finish()
                return true
            }
        })
        view.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) v.performClick()
            gd.onTouchEvent(e)
        }

        Notifs.cancelAlert(this)
        if (demo) startDemo()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onStart() {
        super.onStart()
        if (!demo && !registered) {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(receiver, f)
            registered = true
        }
    }

    override fun onStop() {
        if (registered) {
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun onBattery(i: Intent) {
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (lvl < 0 || scale <= 0) return
        val pct = (lvl * 100f / scale).roundToInt()
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)

        if (plugged == 0) {
            if (sawPlugged) finish()
            else if (!unplugCheckPosted) {
                unplugCheckPosted = true
                handler.postDelayed({ if (!sawPlugged) finish() }, 4000)
            }
            return
        }
        sawPlugged = true

        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "MAINS"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "CHARGING"
        }
        val tempStr = if (temp != Int.MIN_VALUE) "  ·  ${(temp / 10f).roundToInt()}°C" else ""
        val full = pct >= 100 || status == BatteryManager.BATTERY_STATUS_FULL
        val paused = !full && status == BatteryManager.BATTERY_STATUS_NOT_CHARGING

        if (pct != lastPct) {
            if (lastPct >= 0) {
                ticks.addLast(SystemClock.elapsedRealtime() to pct)
                while (ticks.size > 8) ticks.removeFirst()
            }
            lastPct = pct
        }

        show(pct, full, paused, "CHARGING  ·  $source$tempStr", eta(pct))
    }

    private fun eta(pct: Int): String {
        if (ticks.size < 2) return "Working out time to full…"
        val (t0, p0) = ticks.first()
        val (t1, p1) = ticks.last()
        val mins = (t1 - t0) / 60000f
        val gained = p1 - p0
        if (mins <= 0f || gained <= 0) return ""
        val left = ((100 - pct) / (gained / mins)).roundToInt()
        return when {
            left < 1 -> "Almost there"
            left < 60 -> "About $left min to full"
            else -> "About ${left / 60} h ${left % 60} min to full"
        }
    }

    private fun show(pct: Int, full: Boolean, paused: Boolean, status: String, detail: String) {
        view.percent = pct
        view.level = pct / 100f
        when {
            full -> {
                view.status = "FULLY CHARGED"
                view.detail = "Unplug whenever you're ready"
                view.full = true
            }
            paused -> {
                view.status = "CHARGE LIMIT REACHED"
                view.detail = "Battery protection is holding it here"
                view.full = true
            }
            else -> {
                view.status = status
                view.detail = detail
                view.full = false
            }
        }
        if (demo) return
        if (view.full && !sleepPosted) {
            sleepPosted = true
            handler.postDelayed(allowSleep, 60_000)
        } else if (!view.full && sleepPosted) {
            sleepPosted = false
            handler.removeCallbacks(allowSleep)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Preview: climbs quickly from 55% to 100% so you can see the finish. */
    private fun startDemo() {
        val step = object : Runnable {
            override fun run() {
                demoPct = min(100, demoPct + 1)
                show(demoPct, demoPct >= 100, false, "PREVIEW", "Speeding up so you can see the finish")
                if (demoPct < 100) handler.postDelayed(this, 300)
            }
        }
        show(demoPct, false, false, "PREVIEW", "Speeding up so you can see the finish")
        handler.postDelayed(step, 1500)
    }

    companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_SCENE = "scene"
    }
}

