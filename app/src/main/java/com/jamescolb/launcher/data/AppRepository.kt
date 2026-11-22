package com.jamescolb.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    /** The app's 16:9 TV banner, or null if it only ships a square icon. */
    val banner: ImageBitmap?,
    /** Approximate brand color, used to tint the app's name on the card. */
    val brandColor: Color,
    val launchIntent: Intent,
)

/** Reads the launchable apps from PackageManager and keeps the list up to date. */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    suspend fun refresh() {
        _apps.value = withContext(Dispatchers.IO) { loadApps() }
    }

    /** Registers for install/uninstall/update broadcasts; call [onChange] to re-query. */
    fun registerPackageReceiver(onChange: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return receiver
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun loadApps(): List<InstalledApp> {
        // TV (leanback) entries first so their banners win when an app has both.
        val resolved = query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER)
        return resolved
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { info -> runCatching { toInstalledApp(info) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    private fun toInstalledApp(info: ResolveInfo): InstalledApp? {
        val activity = info.activityInfo
        val pkg = activity.packageName
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return null
        val iconDrawable = info.loadIcon(pm)
        val iconBitmap = iconDrawable.toSafeBitmap(144, 144)
        val bannerDrawable = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        return InstalledApp(
            packageName = pkg,
            label = info.loadLabel(pm).toString(),
            icon = iconBitmap.asImageBitmap(),
            banner = bannerDrawable?.toSafeBitmap(320, 180)?.asImageBitmap(),
            brandColor = KNOWN_BRAND_COLORS[pkg] ?: paletteColor(iconBitmap),
            launchIntent = launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun Drawable.toSafeBitmap(fallbackW: Int, fallbackH: Int): Bitmap {
        val w = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(fallbackW * 2) else fallbackW
        val h = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(fallbackH * 2) else fallbackH
        return toBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    /** Picks a vivid color from the icon that stays readable on a dark card. */
    private fun paletteColor(icon: Bitmap): Color {
        val palette = Palette.from(icon).clearFilters().generate()
        val candidates = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.dominantSwatch,
            palette.mutedSwatch,
        ).map { Color(it.rgb) }
        return candidates.firstOrNull { it.luminance() in 0.12f..0.9f }
            ?: candidates.firstOrNull()?.let { lift(it) }
            ?: Color(0xFFE6E6EA)
    }

    /** Lightens very dark colors so they're visible on the dark card. */
    private fun lift(c: Color): Color =
        if (c.luminance() < 0.12f) Color(
            red = c.red + (1 - c.red) * 0.5f,
            green = c.green + (1 - c.green) * 0.5f,
            blue = c.blue + (1 - c.blue) * 0.5f,
        ) else c

    private companion object {
        val KNOWN_BRAND_COLORS = mapOf(
            "com.spotify.tv.android" to Color(0xFF1DB954),
            "com.spotify.music" to Color(0xFF1DB954),
            "com.google.android.youtube.tv" to Color(0xFFFF0033),
            "com.google.android.youtube" to Color(0xFFFF0033),
            "com.google.android.youtube.tvmusic" to Color(0xFFFF0033),
            "com.google.android.youtube.tvkids" to Color(0xFFFF3D3D),
            "com.netflix.ninja" to Color(0xFFE50914),
            "com.netflix.mediaclient" to Color(0xFFE50914),
            "com.plexapp.android" to Color(0xFFE5A00D),
            "tv.twitch.android.app" to Color(0xFF9146FF),
            "tv.twitch.android.viewer" to Color(0xFF9146FF),
            "com.amazon.amazonvideo.livingroom" to Color(0xFF00A8E1),
            "com.disney.disneyplus" to Color(0xFF3D7BFF),
            "com.hulu.livingroomplus" to Color(0xFF1CE783),
            "com.hbo.hbonow" to Color(0xFF9B5CFF),
            "com.wbd.stream" to Color(0xFF2E6BFF),
            "org.jellyfin.androidtv" to Color(0xFFAA5CC3),
            "org.jellyfin.mobile" to Color(0xFFAA5CC3),
            "com.github.damontecres.wholphin" to Color(0xFF6FB7E9),
            "org.videolan.vlc" to Color(0xFFFF8800),
            "com.android.vending" to Color(0xFF34A853),
            "com.google.android.tvlauncher" to Color(0xFF4285F4),
            "com.android.tv.settings" to Color(0xFFB0B0B8),
            "com.android.chrome" to Color(0xFF4285F4),
            "com.apple.atve.androidtv.appletv" to Color(0xFFE6E6EA),
            "com.crunchyroll.crunchyroid" to Color(0xFFF47521),
            "com.pandora.android.atv" to Color(0xFF3668FF),
            "com.soundcloud.android" to Color(0xFFFF5500),
        )
    }
}
