package com.jamescolb.launcher.system

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.jamescolb.launcher.MainActivity

/**
 * Brings this launcher to the front when a stock home screen shows up. Shared by both ways of
 * noticing that: [HomeOverrideService] (accessibility) and [HomeWatcherService] (usage access).
 */
object HomeRedirect {
    const val EXTRA_FROM_OVERRIDE = "from_home_override"
    private const val DEBOUNCE_MS = 800L

    /**
     * Until this time, a stock home screen showing up is left alone. Opening some screens
     * (e.g. Fire TV Settings) briefly passes through Amazon's home screen, which must not
     * bounce the user back to the launcher.
     */
    @Volatile
    private var suppressUntil = 0L

    @Volatile
    private var lastRedirect = 0L

    /** Call right before the launcher starts another app or a system screen. */
    fun suppressFor(ms: Long = 2_500L) {
        suppressUntil = SystemClock.elapsedRealtime() + ms
    }

    /** [force] ignores [suppressFor] (used on wake-up), but never the one-launch-per-press debounce. */
    fun redirect(context: Context, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now < suppressUntil) return // we just opened something on purpose
        if (now - lastRedirect < DEBOUNCE_MS) return // one launch per Home press
        lastRedirect = now
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            .putExtra(EXTRA_FROM_OVERRIDE, true)
        // Note: right after a Home press, Android holds back activity starts from apps without the
        // (system-only) STOP_APP_SWITCHES permission for up to 5 s, so on Fire OS the launcher can
        // arrive late. Sending the start through a PendingIntent used to get around that, but
        // current security patches (seen on Fire OS 6.7.1.1, 2025-11 patch) close that gap.
        runCatching { context.startActivity(intent) }
    }
}
