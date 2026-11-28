package com.jamescolb.launcher.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jamescolb.launcher.R

/**
 * Fallback for the Home button override when [HomeOverrideService] can't run. Fire OS 6.7.x (and
 * likely others) only binds accessibility services signed by Amazon, and it sends its Home-press
 * broadcasts only to Amazon apps, so the one signal left is usage access: the system's record of
 * which activity came to the front. This polls it a few times a second while the screen is on and
 * brings the launcher back when a stock home screen shows up.
 *
 * Needs `adb shell appops set <pkg> GET_USAGE_STATS allow` once. Runs as a foreground service so
 * Fire OS doesn't kill it while another app is playing. With "display over other apps" also
 * granted, [HomeCover] hides the stock home screen while the launcher is on its way.
 */
class HomeWatcherService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usage: UsageStatsManager
    private val event = UsageEvents.Event()

    /** Timestamp of the newest foreground event already handled. */
    private var lastSeen = 0L
    private var stockHomeInFront = false
    private var polling = false

    private val poll = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> setPolling(false)
                Intent.ACTION_SCREEN_ON -> {
                    setPolling(true)
                    // Waking up usually shows the stock home screen; bring ours back once it settles.
                    handler.postDelayed({ if (stockHomeInFront) bringLauncherBack(force = true) }, WAKE_DELAY_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        lastSeen = System.currentTimeMillis()
        isRunning = true
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setPolling(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        // Restarted by the system after being killed: only keep going if still needed.
        if (!LauncherSetup.shouldRunHomeWatcher(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun setPolling(on: Boolean) {
        if (on == polling) return
        polling = on
        handler.removeCallbacks(poll)
        if (on) handler.post(poll)
    }

    private fun check() {
        val now = System.currentTimeMillis()
        // Look back a little: an event can be stamped just before our previous query yet only be
        // recorded after it. Events already handled are skipped by timestamp.
        val events = runCatching { usage.queryEvents(lastSeen - LOOKBACK_MS, now + 1) }.getOrNull() ?: return
        var front: Pair<String, String>? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION") // Same value as ACTIVITY_RESUMED (API 29+).
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            if (event.timeStamp <= lastSeen) continue
            lastSeen = event.timeStamp
            val cls = event.className ?: continue
            front = event.packageName to cls
        }
        val (pkg, cls) = front ?: return
        stockHomeInFront = StockHomes.isHomeActivity(pkg, cls)
        if (stockHomeInFront) bringLauncherBack(force = false)
    }

    private fun bringLauncherBack(force: Boolean) {
        // The launcher itself can take up to 5 s to appear after a Home press (see HomeCover);
        // cover the stock home screen until it does.
        if (HomeRedirect.redirect(this, force)) HomeCover.show(this)
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.home_watcher_channel),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { setShowBadge(false) },
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_grid)
            .setContentTitle(getString(R.string.home_watcher_title))
            .setContentText(getString(R.string.home_watcher_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val POLL_MS = 300L
        private const val LOOKBACK_MS = 2_000L
        private const val WAKE_DELAY_MS = 600L
        private const val CHANNEL_ID = "home_watcher"
        private const val NOTIFICATION_ID = 1

        /** Whether the watcher is running in this process. */
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            // Can be refused when the app is in the background on Android 12+; it's retried on the
            // next resume of the launcher.
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, HomeWatcherService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, HomeWatcherService::class.java)) }
        }
    }
}
