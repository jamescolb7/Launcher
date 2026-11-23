package com.jamescolb.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jamescolb.launcher.MainActivity

/**
 * At boot: re-enables the Home button override if Fire OS switched it off (or starts the
 * usage-access fallback), and opens the launcher so the device starts on it rather than on the
 * stock home screen. Starting an activity from here is allowed before Android 10, and on newer
 * systems while our accessibility service is enabled; otherwise it is silently refused.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // An update kills the process (and with it the override or the usage-access fallback);
            // bring them back without waiting for the launcher to be opened.
            LauncherSetup.restoreOverrideIfWanted(context)
            return
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        LauncherSetup.restoreOverrideIfWanted(context)
        if (LauncherSetup.isOverrideWanted(context) && !LauncherSetup.isDefaultHome(context)) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
