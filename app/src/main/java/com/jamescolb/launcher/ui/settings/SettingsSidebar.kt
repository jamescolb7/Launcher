package com.jamescolb.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jamescolb.launcher.R
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.data.JellyfinAccount
import com.jamescolb.launcher.data.JellyfinServer
import com.jamescolb.launcher.ui.components.launcherSurfaceColors
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.components.SidePanel
import com.jamescolb.launcher.system.LauncherSetup
import com.jamescolb.launcher.ui.components.focusedFill
import com.jamescolb.launcher.ui.theme.AccentPreset
import com.jamescolb.launcher.ui.theme.AccentPresets
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary

private enum class SettingsPage { Main, HiddenApps, LauncherSetup }

/** Jellyfin state and actions for the sidebar's Jellyfin section. */
class JellyfinSettings(
    val server: JellyfinServer?,
    /** Accounts on the active server. */
    val accounts: List<JellyfinAccount>,
    val activeAccount: JellyfinAccount?,
    val onServerClick: () -> Unit,
    val onAccountSelected: (String) -> Unit,
    val onAddUser: () -> Unit,
    val onSignOut: () -> Unit,
    val onRewatchingChange: (Boolean) -> Unit,
    /** Reuse the last user at start-up; when off, the user picker is shown every time. */
    val rememberUser: Boolean,
    val onRememberUserChange: (Boolean) -> Unit,
)


/**
 * Settings as a panel sliding in from the right, over a dimmed home screen (like the stock
 * Google TV quick settings). Focus stays inside the panel; Back goes up a page, then closes.
 */
@Composable
fun SettingsSidebar(
    visible: Boolean,
    preset: AccentPreset,
    jellyfin: JellyfinSettings,
    allApps: List<InstalledApp>,
    hiddenApps: Set<String>,
    onClose: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onPresetSelected: (AccentPreset) -> Unit,
    onAppHiddenChange: (String, Boolean) -> Unit,
    appsFirst: Boolean,
    onAppsFirstChange: (Boolean) -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.Main) }
    LaunchedEffect(visible) { if (visible) page = SettingsPage.Main }
    SidePanel(
        visible = visible,
        onBack = { if (page != SettingsPage.Main) page = SettingsPage.Main else onClose() },
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "settingsPage",
        ) { current ->
            when (current) {
                SettingsPage.Main -> MainPage(
                    preset = preset,
                    jellyfin = jellyfin,
                    hiddenCount = allApps.count { it.packageName in hiddenApps },
                    onOpenSystemSettings = onOpenSystemSettings,
                    onPresetSelected = onPresetSelected,
                    onOpenHiddenApps = { page = SettingsPage.HiddenApps },
                    onOpenLauncherSetup = { page = SettingsPage.LauncherSetup },
                    appsFirst = appsFirst,
                    onAppsFirstChange = onAppsFirstChange,
                )
                SettingsPage.HiddenApps -> HiddenAppsPage(
                    apps = allApps,
                    hidden = hiddenApps,
                    onToggle = onAppHiddenChange,
                )
                SettingsPage.LauncherSetup -> LauncherSetupPage()
            }
        }
    }
}

/** Requests focus on [requester] once the page has laid out. */
@Composable
internal fun InitialFocus(requester: FocusRequester) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

internal val PagePadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp)

internal fun LazyListScope.pageHeader(title: String, subtitle: String? = null) {
    item {
        Column(Modifier.padding(start = 12.dp, bottom = 12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun MainPage(
    preset: AccentPreset,
    jellyfin: JellyfinSettings,
    hiddenCount: Int,
    onOpenSystemSettings: () -> Unit,
    onPresetSelected: (AccentPreset) -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenLauncherSetup: () -> Unit,
    appsFirst: Boolean,
    onAppsFirstChange: (Boolean) -> Unit,
) {
    val firstItem = remember { FocusRequester() }
    InitialFocus(firstItem)

    val context = LocalContext.current
    val isHome = remember { LauncherSetup.isDefaultHome(context) || LauncherSetup.isOverrideEnabled(context) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pageHeader("Settings")
        item {
            SettingsRow(
                icon = R.drawable.ic_settings,
                title = "More Settings",
                summary = "Device system settings",
                trailingIcon = R.drawable.ic_open_in_new,
                onClick = onOpenSystemSettings,
                modifier = Modifier.focusRequester(firstItem),
            )
        }

        item { SectionTitle("Appearance") }
        item { PresetPicker(selected = preset, onSelected = onPresetSelected) }

        item { SectionTitle("Home screen") }
        item {
            SettingsRow(
                icon = if (isHome) R.drawable.ic_check else R.drawable.ic_info,
                title = "Launcher setup",
                summary = if (isHome) "Home opens this launcher" else "Make this the home screen",
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = onOpenLauncherSetup,
            )
        }
        item {
            SettingsRow(
                icon = R.drawable.ic_grid,
                title = "Hidden apps",
                summary = if (hiddenCount == 1) "1 app hidden" else "$hiddenCount apps hidden",
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = onOpenHiddenApps,
            )
        }
        item {
            SettingsRow(
                icon = R.drawable.ic_swap_horiz,
                title = "Apps above Jellyfin",
                summary = if (appsFirst) "Apps row first, then Jellyfin" else "Jellyfin rows first, then apps",
                onClick = { onAppsFirstChange(!appsFirst) },
                trailing = { ToggleSwitch(checked = appsFirst) },
            )
        }

        item { SectionTitle("Jellyfin") }
        item {
            SettingsRow(
                icon = R.drawable.ic_dns,
                title = jellyfin.server?.name ?: "Server",
                summary = jellyfin.server?.url ?: "Not connected",
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = jellyfin.onServerClick,
            )
        }
        val active = jellyfin.activeAccount
        items(jellyfin.accounts, key = { it.key }) { account ->
            SettingsRow(
                icon = R.drawable.ic_person,
                title = account.name,
                summary = if (account.key == active?.key) "Current user" else "Switch to this user",
                trailingIcon = if (account.key == active?.key) R.drawable.ic_check else null,
                onClick = { jellyfin.onAccountSelected(account.key) },
            )
        }
        if (active != null) {
            item(key = "rewatching") {
                SettingsRow(
                    icon = R.drawable.ic_link,
                    title = "Rewatching in Next Up",
                    summary = "Include shows you've already finished (${active.name})",
                    onClick = { jellyfin.onRewatchingChange(!active.rewatching) },
                    trailing = { ToggleSwitch(checked = active.rewatching) },
                )
            }
        }
        if (jellyfin.accounts.isNotEmpty()) {
            item(key = "remember-user") {
                SettingsRow(
                    icon = R.drawable.ic_account_circle,
                    title = "Remember user",
                    summary = if (jellyfin.rememberUser) {
                        "Start with the last user"
                    } else {
                        "Choose who's watching every time"
                    },
                    onClick = { jellyfin.onRememberUserChange(!jellyfin.rememberUser) },
                    trailing = { ToggleSwitch(checked = jellyfin.rememberUser) },
                )
            }
        }
        item {
            SettingsRow(
                icon = R.drawable.ic_add,
                title = "Add user",
                summary = "Quick Connect or password",
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = jellyfin.onAddUser,
            )
        }
        if (active != null) {
            item(key = "sign-out") {
                SettingsRow(
                    icon = R.drawable.ic_open_in_new,
                    title = "Sign out",
                    summary = "Remove ${active.name} from this device",
                    onClick = jellyfin.onSignOut,
                )
            }
        }

        item { SectionTitle("About") }
        item {
            SettingsRow(
                icon = R.drawable.ic_info,
                title = "Launcher $version",
                summary = "Replacement Android launcher",
                onClick = {},
            )
        }
    }
}

@Composable
private fun HiddenAppsPage(
    apps: List<InstalledApp>,
    hidden: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    val firstItem = remember { FocusRequester() }
    InitialFocus(firstItem)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pageHeader("Hidden apps", "Turned-off apps won't appear on the home screen or in All Apps.")
        items(apps, key = { it.packageName }) { app ->
            val shown = app.packageName !in hidden
            SettingsRow(
                title = app.label,
                summary = if (shown) "Shown" else "Hidden",
                onClick = { onToggle(app.packageName, shown) },
                modifier = if (app == apps.first()) Modifier.focusRequester(firstItem) else Modifier,
                leading = {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                    )
                },
                trailing = { ToggleSwitch(checked = shown) },
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalAccent.current,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** A compact, full-width focusable settings entry. */
@Composable
internal fun SettingsRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = launcherSurfaceColors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = focusedFill(accent),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> leading()
                icon != null -> DrawableIcon(icon, size = 20.dp)
            }
            if (leading != null || icon != null) Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                trailing != null -> {
                    Spacer(Modifier.width(10.dp))
                    trailing()
                }
                trailingIcon != null -> {
                    Spacer(Modifier.width(10.dp))
                    DrawableIcon(trailingIcon, size = 18.dp)
                }
            }
        }
    }
}

@Composable
internal fun ToggleSwitch(checked: Boolean) {
    val accent = LocalAccent.current
    val track by animateColorAsState(
        if (checked) accent else Color.White.copy(alpha = 0.22f),
        label = "track",
    )
    val knobOffset by animateDpAsState(if (checked) 16.dp else 0.dp, label = "knob")
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .background(track, CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(14.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** Compact row of round color swatches; the chosen one is named on the right. */
@Composable
private fun PresetPicker(selected: AccentPreset, onSelected: (AccentPreset) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Background color",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(selected.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentPresets.all.forEach { preset ->
                PresetSwatch(
                    preset = preset,
                    selected = preset.id == selected.id,
                    onClick = { onSelected(preset) },
                )
            }
        }
    }
}

@Composable
private fun PresetSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = launcherSurfaceColors(
            containerColor = preset.base,
            focusedContainerColor = preset.base,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) preset.accent else Color.White.copy(alpha = 0.25f),
                ),
                shape = CircleShape,
            ),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), inset = 2.dp, shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(preset.blobs[2], preset.blobs[0], preset.blobs[1])),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) DrawableIcon(R.drawable.ic_check, tint = Color.White, size = 16.dp)
        }
    }
}
