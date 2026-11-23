package com.jamescolb.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jamescolb.launcher.R
import com.jamescolb.launcher.system.HomeOverrideService
import com.jamescolb.launcher.system.HomeRedirect
import com.jamescolb.launcher.system.LauncherSetup
import com.jamescolb.launcher.system.TvPlatform
import com.jamescolb.launcher.ui.components.focusedFill
import com.jamescolb.launcher.ui.components.launcherSurfaceColors
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.TextSecondary

/** Settings page for making this the device's home screen, per platform. */
@Composable
internal fun LauncherSetupPage() {
    val context = LocalContext.current
    // Re-read the system state whenever we come back (e.g. from the Home app picker or ADB).
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val requestHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    val platform = remember { LauncherSetup.platform(context) }
    val isDefault = remember(tick) { LauncherSetup.isDefaultHome(context) }
    val overrideOn = remember(tick) { LauncherSetup.isOverrideEnabled(context) }
    val wanted = remember(tick) { LauncherSetup.isOverrideWanted(context) }
    val a11yRunning = remember(tick) { HomeOverrideService.isRunning }
    val usageAccess = remember(tick) { LauncherSetup.hasUsageAccess(context) }
    val watcherWorks = usageAccess && LauncherSetup.isWatcherSupported()
    val switchedOn = wanted || overrideOn
    val canWrite = remember(tick) { LauncherSetup.canWriteSecureSettings(context) }
    val homeIntent = remember(tick) { LauncherSetup.defaultHomeIntent(context) }
    val steps = remember { LauncherSetup.adbSteps(context) }

    val first = remember { FocusRequester() }
    InitialFocus(first)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pageHeader("Launcher setup", platform.label)

        // The override is what actually works on Fire OS and on Google's TV launchers, so it
        // comes first.
        item {
            SettingsRow(
                icon = when {
                    isDefault || (switchedOn && (a11yRunning || watcherWorks)) -> R.drawable.ic_check
                    switchedOn -> R.drawable.ic_info
                    else -> R.drawable.ic_link
                },
                title = "Home button override",
                summary = when {
                    switchedOn && a11yRunning -> "On: the Home button opens this launcher"
                    switchedOn && watcherWorks -> "On (via usage access): the Home button opens this launcher"
                    switchedOn && platform == TvPlatform.FireOs ->
                        "On, but this Fire OS version blocks it. Run step 2 below"
                    switchedOn && overrideOn -> "On, but the system hasn't started it yet"
                    switchedOn -> "Waiting for \"Launcher Home button\" in Accessibility settings"
                    canWrite || platform == TvPlatform.FireOs -> "Off. Select to turn on"
                    else -> "Select, then turn on \"Launcher Home button\""
                },
                onClick = {
                    val turnOn = !switchedOn
                    when {
                        canWrite -> LauncherSetup.setOverrideEnabled(context, turnOn)
                        // Fire OS doesn't list the service in Accessibility settings; the usage-access
                        // fallback only needs the wish recorded.
                        platform == TvPlatform.FireOs && !overrideOn -> LauncherSetup.setOverrideWanted(context, turnOn)
                        else -> {
                            LauncherSetup.setOverrideWanted(context, turnOn)
                            HomeRedirect.suppressFor()
                            runCatching { context.startActivity(LauncherSetup.accessibilitySettingsIntent()) }
                        }
                    }
                    LauncherSetup.syncHomeWatcher(context)
                    tick++
                },
                trailing = { ToggleSwitch(checked = switchedOn) },
                modifier = Modifier.focusRequester(first),
            )
        }
        if (platform != TvPlatform.FireOs) {
            item {
                SettingsRow(
                    icon = if (isDefault) R.drawable.ic_check else R.drawable.ic_info,
                    title = "Default home app",
                    summary = when {
                        isDefault -> "Home goes straight to this launcher"
                        homeIntent != null -> "Not in effect. Select to choose this launcher"
                        else -> "Not in effect"
                    },
                    trailingIcon = if (!isDefault && homeIntent != null) R.drawable.ic_open_in_new else null,
                    onClick = {
                        if (!isDefault && homeIntent != null) {
                            HomeRedirect.suppressFor()
                            runCatching { requestHome.launch(homeIntent) }
                        }
                    },
                )
            }
        }

        item { SectionTitle("How it works here") }
        item {
            InfoText(
                when (platform) {
                    TvPlatform.FireOs ->
                        "Fire OS always sends Home to Amazon's home screen. With the override on, this " +
                            "launcher comes back right after it (Amazon's screen may flash briefly). Fire OS " +
                            "doesn't list the override in Accessibility settings, so run step 1 once from a " +
                            "computer (Settings → My Fire TV → Developer options → ADB debugging). Some Fire OS " +
                            "versions only run accessibility services made by Amazon; there, step 2 lets the " +
                            "launcher notice Amazon's home screen through usage access instead."
                    else ->
                        "Google's TV home screen keeps the Home button even when another app is chosen " +
                            "as the default home, so the override is what makes Home open this launcher " +
                            "(the built-in screen may flash briefly). Choosing the default home app still " +
                            "helps on devices that honor it, and disabling the built-in home (step 2) " +
                            "removes the flash."
                },
            )
        }

        item { SectionTitle("ADB setup") }
        if (canWrite) {
            item { InfoText("Step 1 is done: the override can be switched above.") }
        }
        if (usageAccess && platform == TvPlatform.FireOs) {
            item { InfoText("Step 2 is done: usage access is granted.") }
        }
        items(steps) { step ->
            InfoText(step.title)
            CommandRow(step.command)
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** A focusable (so it can be scrolled to with the D-pad) block showing one shell command. */
@Composable
private fun CommandRow(command: String) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = launcherSurfaceColors(
            containerColor = Color.Black.copy(alpha = 0.3f),
            focusedContainerColor = focusedFill(accent),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = command,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
