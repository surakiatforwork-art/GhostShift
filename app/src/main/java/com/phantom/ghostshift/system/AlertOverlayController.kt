package com.phantom.ghostshift.system

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.phantom.ghostshift.MainActivity

/** Compact, user-dismissible alarm surface shown only after the user grants overlay access. */
object AlertOverlayController {
    private var windowManager: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var ringtone: Ringtone? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context, tag: String, remark: String?, soundUri: String?, repeatSound: Boolean, themeMode: String = "system") {
        if (!canShow(context)) return
        dismiss(context)
        val appContext = context.applicationContext
        val dark = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> (appContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val ink = if (dark) Color.rgb(234,240,247) else Color.rgb(25,37,48)
        val muted = if (dark) Color.rgb(172,184,199) else Color.rgb(82,97,113)
        val mint = if (dark) Color.rgb(36,84,71) else Color.rgb(158,233,203)
        val mintInk = if (dark) Color.rgb(177,248,219) else Color.rgb(16,75,55)
        val panel = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(appContext, 18), dp(appContext, 16), dp(appContext, 18), dp(appContext, 14))
            background = glass(if (dark) Color.rgb(32,39,48) else Color.rgb(250,251,252), dp(appContext, 14), dark)
            elevation = dp(appContext, 6).toFloat()
            isClickable = true
        }
        panel.addView(label(appContext, "ถึงเวลาดาวน์โหลด", 15, muted, Typeface.BOLD))
        panel.addView(label(appContext, "PHOTO READY", 22, ink, Typeface.BOLD).apply {
            setPadding(0, dp(appContext, 12), 0, 0)
        })
        panel.addView(label(appContext, tag + remark?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty(), 20, mintInk, Typeface.BOLD).apply {
            setPadding(0, dp(appContext, 6), 0, dp(appContext, 14))
        })
        fun action(text: String, fill: Int, fg: Int, primary: Boolean, click: () -> Unit) {
            val button = label(appContext, text, if (primary) 16 else 13, fg, Typeface.BOLD).apply {
                background = glass(fill, dp(appContext, 9), dark)
                minHeight = dp(appContext, 48)
                setPadding(dp(appContext, 12), 0, dp(appContext, 12), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { click() }
                accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Button"
                    }
                }
            }
            panel.addView(button, LinearLayout.LayoutParams(if (primary) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(appContext, 8)
                gravity = Gravity.CENTER
            })
        }
        fun openGhostShift() {
            acknowledge(appContext)
            appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        panel.setOnClickListener { openGhostShift() }
        panel.isFocusable = true
        action("เปิด GhostShift", mint, mintInk, true) { openGhostShift() }
        action("หยุดเสียง", if (dark) Color.rgb(52,63,75) else Color.rgb(232,237,242), ink, false) {
            acknowledge(appContext)
        }

        val params = WindowManager.LayoutParams(
            dp(appContext, 320), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(appContext, 56) }
        runCatching {
            windowManager = appContext.getSystemService(WindowManager::class.java)
            windowManager?.addView(panel, params)
            overlay = panel
            playSound(appContext, soundUri, repeatSound)
        }
    }

    fun dismiss(context: Context) {
        runCatching { overlay?.let { windowManager?.removeViewImmediate(it) } }
        overlay = null
        windowManager = null
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun acknowledge(context: Context) {
        dismiss(context)
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(1001)
    }

    private fun glass(color: Int, radius: Int, dark: Boolean) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        setStroke(1, if (dark) Color.rgb(82,97,109) else Color.WHITE)
    }

    private fun playSound(context: Context, soundUri: String?, repeatSound: Boolean) {
        val uri = soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(context, uri)?.also {
            runCatching { it.isLooping = repeatSound }
            it.play()
        }
    }

    private fun label(context: Context, text: String, sp: Int, color: Int, style: Int) = TextView(context).apply {
        this.text = text; textSize = sp.toFloat(); setTextColor(color); typeface = Typeface.create(Typeface.DEFAULT, style)
        gravity = Gravity.CENTER
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
