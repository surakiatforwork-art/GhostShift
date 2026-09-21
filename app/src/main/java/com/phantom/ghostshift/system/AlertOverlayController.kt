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

    fun show(context: Context, tag: String, remark: String?, soundUri: String?) {
        if (!canShow(context)) return
        dismiss(context)
        val appContext = context.applicationContext
        val panel = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(appContext, 22), dp(appContext, 18), dp(appContext, 22), dp(appContext, 18))
            background = rounded(Color.rgb(244, 255, 250), dp(appContext, 26))
            elevation = dp(appContext, 12).toFloat()
            isClickable = true
            setOnClickListener {
                val wasVisible = MainActivity.isVisible
                dismiss(appContext)
                if (!wasVisible) {
                    appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }
            }
        }
        panel.addView(label(appContext, "👻  GhostShift", 20, Color.rgb(9, 68, 51), Typeface.BOLD))
        panel.addView(label(appContext, "⏰", 54, Color.rgb(244, 91, 80), Typeface.NORMAL).apply {
            setPadding(0, dp(appContext, 8), 0, 0)
        })
        panel.addView(label(appContext, "PHOTO READY", 24, Color.rgb(7, 62, 47), Typeface.BOLD))
        panel.addView(label(appContext, tag + remark?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty(), 16, Color.rgb(38, 111, 88), Typeface.BOLD))
        panel.addView(label(appContext, "แตะเพื่อเปิดแอปและหยุดเสียงแจ้งเตือน", 13, Color.rgb(83, 126, 113), Typeface.NORMAL).apply {
            setPadding(0, dp(appContext, 12), 0, 0)
        })

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
            playSound(appContext, soundUri)
        }
    }

    fun dismiss(context: Context) {
        runCatching { overlay?.let { windowManager?.removeViewImmediate(it) } }
        overlay = null
        windowManager = null
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun playSound(context: Context, soundUri: String?) {
        val uri = soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(context, uri)?.also {
            runCatching { it.isLooping = true }
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
