package com.jamescolb.launcher.system

import android.Manifest
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** What kind of TV OS we're on; it decides how the launcher takes over the Home button. */
enum class TvPlatform(val label: String) {
    FireOs("Fire TV (Fire OS)"),
    GoogleTv("Google TV"),
    AndroidTv("Android TV"),
    Other("Android"),
}

/**
 * Everything about becoming the device's home screen:
 *
 * - **Default home app** (Android TV, some Google TV builds): the system routes Home to us. On
 *   Android 10+ we can ask for the HOME role; otherwise the Home-app settings or ADB.
 * - **Home button override** ([HomeOverrideService]): an accessibility service that notices the
 *   stock home screen coming to the front and brings this launcher back. This is the only option
 *   on Fire OS, which always sends Home to Amazon's launcher, and a fallback for Google TV.
 * - **Usage-access fallback** ([HomeWatcherService]): does the same as the override using usage
 *   access, for Fire OS builds that refuse to run third-party accessibility services.
 */
object LauncherSetup {

    fun platform(context: Context): TvPlatform {
        val pm = context.packageManager
        return when {
            pm.hasSystemFeature("amazon.hardware.fire_tv") ||
                Build.MANUFACTURER.equals("Amazon", ignoreCase = true) -> TvPlatform.FireOs
            isInstalled(context, StockHomes.GOOGLE_TV) -> TvPlatform.GoogleTv
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> TvPlatform.AndroidTv
            else -> TvPlatform.Other
        }
    }

    // --- Default home app -------------------------------------------------------------------------

    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * The best in-app way to become the default home app on this device, or null if there is
     * none (then only ADB `cmd package set-home-activity` works).
     */
    fun defaultHomeIntent(context: Context): Intent? {
        if (platform(context) == TvPlatform.FireOs) return null // Fire OS ignores the choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        val settings = Intent(Settings.ACTION_HOME_SETTINGS)
        return settings.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    // --- Home button override (accessibility service) --------------------------------------------

    fun overrideComponent(context: Context) = ComponentName(context, HomeOverrideService::class.java)

    fun isOverrideEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = overrideComponent(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** True once `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` was run. */
    fun canWriteSecureSettings(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Turns the override on or off directly (needs [canWriteSecureSettings]). Keeps any other
     * accessibility services the user has enabled. Returns false if it couldn't be changed.
     */
    fun setOverrideEnabled(context: Context, enabled: Boolean): Boolean {
        setOverrideWanted(context, enabled)
        return writeOverrideSetting(context, enabled)
    }

    private fun writeOverrideSetting(context: Context, enabled: Boolean): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val me = overrideComponent(context).flattenToString()
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.filter { it.isNotBlank() && it != "null" && ComponentName.unflattenFromString(it) != overrideComponent(context) }
                ?: emptyList()
            val updated = if (enabled) current + me else current
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated.joinToString(":"))
            if (enabled) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }

    /**
     * After the app is updated or force-stopped, Android stops the override and marks it as
     * crashed (Fire OS may also clear it from the enabled list). If the user wants the override
     * and we're allowed to, bring it back: add it if it's missing, or, if it's listed but not
     * connected, remove and re-add it, which is what makes Android connect it again. Then start
     * or stop the usage-access fallback to match.
     */
    fun restoreOverrideIfWanted(context: Context) {
        val app = context.applicationContext
        if (!isOverrideWanted(app)) {
            HomeWatcherService.stop(app)
            return
        }
        val main = Handler(Looper.getMainLooper())
        // Give a just-(re)started service a moment to connect before deciding it's stuck.
        main.postDelayed({
            if (!HomeOverrideService.isRunning && canWriteSecureSettings(app)) {
                if (!isOverrideEnabled(app)) {
                    writeOverrideSetting(app, true)
                } else if (SystemClock.elapsedRealtime() > BOOT_SETTLE_MS && !restartTried) {
                    // Listed but not connected = marked crashed (after an update or force-stop).
                    // Right after boot it's just not connected *yet*, and a reboot already clears
                    // the crashed state, so restarting it then would only cause a gap. Only try
                    // once per app start: on Fire OS builds that never connect it, retrying on
                    // every resume would just churn the setting.
                    restartTried = true
                    writeOverrideSetting(app, false)
                    main.postDelayed({ writeOverrideSetting(app, true) }, RESTART_GAP_MS)
                }
            }
            syncHomeWatcher(app)
        }, CONNECT_GRACE_MS)
    }

    @Volatile
    private var restartTried = false

    private const val CONNECT_GRACE_MS = 1_200L
    private const val BOOT_SETTLE_MS = 3 * 60_000L
    private const val RESTART_GAP_MS = 600L

    // --- Usage-access fallback (HomeWatcherService) ---------------------------------------------

    /** True once `adb shell appops set <pkg> GET_USAGE_STATS allow` was run (or granted in Settings). */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION") // unsafeCheckOpNoThrow is API 29+.
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * Whether the fallback can bring the launcher forward here at all. From Android 10, apps may
     * not start activities from the background, so it only works on older systems (Fire OS 5-7).
     */
    fun isWatcherSupported(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Whether [HomeWatcherService] should be running right now. */
    fun shouldRunHomeWatcher(context: Context): Boolean =
        isWatcherSupported() &&
            isOverrideWanted(context) &&
            !HomeOverrideService.isRunning &&
            hasUsageAccess(context) &&
            !isDefaultHome(context)

    /** Starts or stops [HomeWatcherService] to match [shouldRunHomeWatcher]. */
    fun syncHomeWatcher(context: Context) {
        if (shouldRunHomeWatcher(context)) {
            if (!HomeWatcherService.isRunning) HomeWatcherService.start(context)
        } else {
            HomeWatcherService.stop(context)
        }
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun prefs(context: Context) = context.getSharedPreferences("launcher_setup", Context.MODE_PRIVATE)

    fun isOverrideWanted(context: Context): Boolean = prefs(context).getBoolean(KEY_OVERRIDE_WANTED, false)

    fun setOverrideWanted(context: Context, wanted: Boolean) {
        prefs(context).edit { putBoolean(KEY_OVERRIDE_WANTED, wanted) }
    }

    /** One step on the setup page: what it does, and the command to run from a computer. */
    data class AdbStep(val title: String, val command: String)

    /** ADB steps shown on the setup page, tailored to the platform. */
    fun adbSteps(context: Context): List<AdbStep> {
        val pkg = context.packageName
        val service = overrideComponent(context).flattenToShortString()
        val grant = AdbStep(
            "1. Allow the launcher to turn the override on itself (then use the switch above)",
            "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
        )
        return when (platform(context)) {
            TvPlatform.FireOs -> listOf(
                grant,
                AdbStep(
                    "Or turn the override on directly",
                    "adb shell settings put secure enabled_accessibility_services $service",
                ),
                AdbStep("…and enable accessibility", "adb shell settings put secure accessibility_enabled 1"),
                AdbStep(
                    "2. Let the launcher see which app is in front. Needed on Fire OS builds that " +
                        "block the override (the switch above says so)",
                    "adb shell appops set $pkg GET_USAGE_STATS allow",
                ),
                AdbStep(
                    "3. Optional, with step 2: Fire OS holds the launcher back for a few seconds after " +
                        "Home; this covers Amazon's home screen until it opens",
                    "adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow",
                ),
            )
            else -> {
                val stock = if (platform(context) == TvPlatform.GoogleTv) StockHomes.GOOGLE_TV else StockHomes.ANDROID_TV
                listOf(
                    grant,
                    AdbStep(
                        "2. Optional: disable the built-in home so Home comes here directly, without a " +
                            "flash (undo with: pm enable $stock)",
                        "adb shell pm disable-user --user 0 $stock",
                    ),
                )
            }
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private const val KEY_OVERRIDE_WANTED = "override_wanted"
}

/** Stock TV home screens that the override replaces. */
object StockHomes {
    const val FIRE_TV = "com.amazon.tv.launcher"
    const val ANDROID_TV = "com.google.android.tvlauncher"
    const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"
    const val LEANBACK = "com.google.android.leanbacklauncher"

    /**
     * Whether an activity is a stock *home screen* (not one of the stock launcher's other screens,
     * like Fire TV's app library, which must keep working).
     */
    fun isHomeActivity(packageName: String, className: String): Boolean = when (packageName) {
        // Fire OS 7/8: HomeActivity_vNext; older Fire OS: HomeActivity.
        FIRE_TV -> className == "$FIRE_TV.ui.HomeActivity_vNext" || className == "$FIRE_TV.ui.HomeActivity"
        ANDROID_TV, LEANBACK -> className == "$packageName.MainActivity"
        GOOGLE_TV -> className == "$GOOGLE_TV.MainActivity" || className.endsWith(".HomeActivity")
        else -> false
    }
}
