package com.jamescolb.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * Makes the remote's Home button land on this launcher on systems that won't let a third-party
 * app be the real home screen (Fire OS always sends Home to Amazon's launcher; some Google TV
 * builds do the same with theirs).
 *
 * It watches which window comes to the front. When it's a stock home screen (see [StockHomes]),
 * it starts [com.jamescolb.launcher.MainActivity] on top, so Home effectively opens this
 * launcher; the stock screen may flash briefly first. It reads no window content and sends
 * nothing anywhere.
 *
 * Some Fire OS builds (e.g. 6.7.x) never bind accessibility services that aren't signed by
 * Amazon; there [HomeWatcherService] does the same job using usage access instead.
 */
class HomeOverrideService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var stockHomeInFront = false

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Waking up usually shows the stock home screen; bring ours back once it settles.
            handler.postDelayed(
                { if (stockHomeInFront) HomeRedirect.redirect(this@HomeOverrideService, force = true) },
                WAKE_DELAY_MS,
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        // This does the job properly; the usage-access fallback isn't needed.
        HomeWatcherService.stop(this)
        // Must be registered at runtime; manifest receivers don't get SCREEN_ON.
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Only activity-level changes say what's in front; ignore popups and views.
        if (!cls.contains('.') || cls.startsWith("android.")) return

        stockHomeInFront = StockHomes.isHomeActivity(pkg, cls)
        if (stockHomeInFront) HomeRedirect.redirect(this, force = false)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        runCatching { unregisterReceiver(screenOnReceiver) }
        handler.removeCallbacksAndMessages(null)
        // Switched off or crashed: let the usage-access fallback take over if it can.
        LauncherSetup.syncHomeWatcher(this)
        super.onDestroy()
    }

    companion object {
        private const val WAKE_DELAY_MS = 600L

        /**
         * Whether the system has actually connected the service in this process. It can be listed
         * as enabled yet not running: after the app is updated or force-stopped, Android marks the
         * service as crashed and won't reconnect it until it is re-enabled, and some Fire OS builds
         * never connect third-party services at all.
         */
        @Volatile
        var isRunning = false
            private set
    }
}
