package com.jamescolb.launcher.system

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

/**
 * A plain full-screen window drawn over the stock home screen while the launcher is on its way.
 *
 * Right after a Home press, Android holds back activity starts from apps like this one for up to
 * 5 s, so on Fire OS Amazon's home screen would sit there until the launcher finally opens.
 * Overlay windows aren't held back, so [HomeWatcherService] puts this up the moment it sees the
 * stock home screen, and [com.jamescolb.launcher.MainActivity] takes it down once it's showing.
 *
 * It swallows remote keys so nothing gets clicked on the hidden home screen underneath. Needs
 * "display over other apps" (`adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow`); without it
 * this does nothing.
 */
object HomeCover {
    /** Safety net: never leave the screen covered if the launcher doesn't show up. */
    private const val MAX_SHOW_MS = 7_000L

    /** Lets the launcher draw its first frame before the cover goes, so nothing flashes. */
    private const val HIDE_DELAY_MS = 150L

    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val hideNow = Runnable { remove() }

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Main thread only. */
    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context) {
        handler.removeCallbacks(hideNow)
        handler.postDelayed(hideNow, MAX_SHOW_MS)
        if (view != null || !canShow(context)) return

        val app = context.applicationContext
        val cover = object : View(app) {
            // Eat every key (Home can't be caught by apps anyway, so it still works).
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = true
        }.apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        @Suppress("DEPRECATION") // TYPE_PHONE is the overlay type before Android 8.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE,
        ).apply { windowAnimations = 0 }
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (runCatching { wm.addView(cover, params) }.isSuccess) {
            view = cover
            cover.requestFocus()
        }
    }

    /** Takes the cover down shortly (call once the launcher is in front). Main thread only. */
    fun hide() {
        if (view == null) return
        handler.removeCallbacks(hideNow)
        handler.postDelayed(hideNow, HIDE_DELAY_MS)
    }

    private fun remove() {
        handler.removeCallbacks(hideNow)
        val cover = view ?: return
        view = null
        val wm = cover.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeView(cover) }
    }
}
