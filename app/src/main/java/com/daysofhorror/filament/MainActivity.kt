package com.daysofhorror.filament

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Pick your animation, preview it, and grant the few permissions it needs. */
class MainActivity : Activity() {

    private val bgColor = col(0xFF07090F)
    private val cardColor = col(0xFF121622)
    private val amber = col(0xFFFFB547)

    private val tiles = ArrayList<Triple<SceneInfo, FrameLayout, TextView>>()
    private lateinit var setupBox: LinearLayout
    private var shuffleSwitch: Switch? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        scroll.addView(column)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val sb = insets.getInsets(WindowInsets.Type.systemBars())
                top = sb.top; bottom = sb.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            column.setPadding(dp(20), dp(28) + top, dp(20), dp(32) + bottom)
            insets
        }

        // Header
        column.addView(text("Filament", 36f, col(0xFFFFFFFF), "sans-serif-light"))
        column.addView(text("Charging animations for your phone", 14f, col(0x99FFFFFF), "sans-serif").apply {
            setPadding(0, dp(2), 0, dp(20))
        })

        // Switches
        val card = card()
        card.addView(switchRow("Show when I plug in", "Opens on the lock screen and stops when you reach 100%", Prefs.enabled(this)) { on ->
            Prefs.setEnabled(this, on)
            if (on) ChargeService.start(this) else ChargeService.stop(this)
        })
        card.addView(divider())
        val surprise = switchRow("Surprise me", "Play a different animation each time", Prefs.shuffle(this)) { on ->
            Prefs.setShuffle(this, on)
            refreshTiles()
        }
        shuffleSwitch = surprise.findViewWithTag("switch")
        card.addView(surprise)
        column.addView(card)

        // Picker grid
        column.addView(sectionTitle("Choose your animation"))
        column.addView(text("Tap to choose  ·  long-press to preview", 12f, col(0x80FFFFFF), "sans-serif").apply {
            setPadding(0, 0, 0, dp(12))
        })
        val gap = dp(12)
        val tileW = (resources.displayMetrics.widthPixels - dp(40) - gap) / 2
        val tileH = (tileW * 1.55f).toInt()
        var row: LinearLayout? = null
        Scenes.all.forEachIndexed { i, info ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tileH).apply { bottomMargin = gap })
            }
            val tile = makeTile(info, i)
            row!!.addView(tile.second, LinearLayout.LayoutParams(0, tileH, 1f).apply {
                if (i % 2 == 0) marginEnd = gap / 2 else marginStart = gap / 2
            })
            tiles.add(Triple(info, tile.second, tile.first))
        }
        refreshTiles()

        // Preview button
        val preview = text("Preview the selected animation", 15f, col(0xFF1A1206), "sans-serif-medium").apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dpf(28f)
                setColor(amber)
            }
            setOnClickListener { openPreview(Scenes.byId(Prefs.sceneId(this@MainActivity))) }
        }
        column.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        // Setup
        column.addView(sectionTitle("Setup"))
        setupBox = card()
        column.addView(setupBox)

        setContentView(scroll)

        if (Prefs.enabled(this)) ChargeService.start(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
    }

    private fun openPreview(info: SceneInfo) {
        startActivity(Intent(this, ChargeActivity::class.java).apply {
            putExtra(ChargeActivity.EXTRA_DEMO, true)
            putExtra(ChargeActivity.EXTRA_SCENE, info.id)
        })
    }

    private fun makeTile(info: SceneInfo, index: Int): Pair<TextView, FrameLayout> {
        val frame = FrameLayout(this)
        frame.background = GradientDrawable().apply {
            cornerRadius = dpf(20f)
            setColor(col(0xFF101010))
        }
        frame.clipToOutline = true

        val cv = ChargeView(this, info, overlay = false).apply {
            preview = true
            previewOffset = index * 1.3f
        }
        frame.addView(cv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(28), dp(12), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(col(0xE6000000), col(0x00000000)))
            addView(text(info.title, 16f, col(0xFFFFFFFF), "sans-serif-medium"))
            addView(text(info.blurb, 11f, col(0xB3FFFFFF), "sans-serif"))
        }
        frame.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        val badge = text("✓", 14f, col(0xFF1A1206), "sans-serif-medium").apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(info.accent)
            }
        }
        frame.addView(badge, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(10), dp(10), 0)
        })

        frame.setOnClickListener {
            Prefs.setScene(this, info.id)
            if (Prefs.shuffle(this)) shuffleSwitch?.isChecked = false
            refreshTiles()
        }
        frame.setOnLongClickListener {
            openPreview(info)
            true
        }
        return badge to frame
    }

    private fun refreshTiles() {
        val chosen = Prefs.sceneId(this)
        val shuffle = Prefs.shuffle(this)
        for ((info, frame, badge) in tiles) {
            val selected = !shuffle && info.id == chosen
            badge.visibility = if (selected) View.VISIBLE else View.GONE
            frame.foreground = GradientDrawable().apply {
                cornerRadius = dpf(20f)
                setColor(0)
                if (selected) setStroke(dp(3), info.accent) else setStroke(dp(1), col(0x22FFFFFF))
            }
        }
    }

    private fun refreshSetup() {
        setupBox.removeAllViews()
        val overlayOk = Settings.canDrawOverlays(this)
        setupBox.addView(setupRow(
            "Display over other apps",
            "Lets the animation open by itself when you plug in.",
            overlayOk, "Allow"
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        setupBox.addView(divider())
        val pm = getSystemService(PowerManager::class.java)
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        setupBox.addView(setupRow(
            "Unrestricted battery",
            "Stops Samsung putting Filament to sleep. It uses almost nothing while waiting.",
            batteryOk, "Allow"
        ) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })
        if (Build.VERSION.SDK_INT >= 33) {
            setupBox.addView(divider())
            val notifOk = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            setupBox.addView(setupRow(
                "Notifications (optional)",
                "A backup way to open the animation if pop-ups are blocked.",
                notifOk, "Allow"
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshSetup()
    }

    // ---- small view helpers ----

    private fun text(s: String, sp: Float, color: Int, family: String) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = Typeface.create(family, Typeface.NORMAL)
    }

    private fun sectionTitle(s: String) = text(s, 18f, col(0xFFFFFFFF), "sans-serif-medium").apply {
        setPadding(0, dp(26), 0, dp(4))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(6), dp(16), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dpf(20f)
            setColor(cardColor)
        }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(col(0x14FFFFFF))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun switchRow(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 16f, col(0xFFFFFFFF), "sans-serif"))
            addView(text(body, 12f, col(0x8CFFFFFF), "sans-serif"))
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply {
            tag = "switch"
            isChecked = on
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    private fun setupRow(title: String, body: String, ok: Boolean, action: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 15f, col(0xFFFFFFFF), "sans-serif"))
            addView(text(body, 12f, col(0x8CFFFFFF), "sans-serif"))
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
        if (ok) {
            row.addView(text("✓ Done", 13f, col(0xFF7BE495), "sans-serif-medium"))
        } else {
            val b = text(action, 13f, col(0xFF1A1206), "sans-serif-medium").apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dpf(18f)
                    setColor(amber)
                }
                setOnClickListener { onClick() }
            }
            row.addView(b)
        }
        return row
    }
}
