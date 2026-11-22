package com.jamescolb.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Opens Jellyfin items in Wholphin (https://github.com/damontecres/Wholphin) through its intent
 * API (documented in Wholphin's Intents.md; marked experimental there, so it lives only here).
 *
 * VIEW + extras `itemId` / `serverId` / `userId` opens the item's details page. Wholphin
 * switches to that server/user only when both ids are sent and the profile exists in Wholphin.
 */
object WholphinLauncher {
    const val PACKAGE = "com.github.damontecres.wholphin"
    private const val ACTIVITY = "$PACKAGE.MainActivity"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun detailsIntent(itemId: String, account: JellyfinAccount): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(PACKAGE, ACTIVITY)
            putExtra("itemId", itemId)
            putExtra("serverId", account.serverId)
            putExtra("userId", account.userId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
