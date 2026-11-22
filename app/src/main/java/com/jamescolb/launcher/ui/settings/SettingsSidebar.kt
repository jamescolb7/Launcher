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
import com.jamescolb.launcher.data.JellyfinUser
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.components.focusedFill
import com.jamescolb.launcher.ui.theme.AccentPreset
import com.jamescolb.launcher.ui.theme.AccentPresets
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary

private enum class SettingsPage { Main, HiddenApps }

private val SidebarWidth = 380.dp
private val SidebarColor = Color(0xFF1B1C20)

/**
 * Settings as a panel sliding in from the right, over a dimmed home screen (like the stock
 * Google TV quick settings). Focus stays inside the panel; Back goes up a page, then closes.
 */
@Composable
fun SettingsSidebar(
    visible: Boolean,
    preset: AccentPreset,
    serverUrl: String,
    users: List<JellyfinUser>,
    activeUserId: String?,
    allApps: List<InstalledApp>,
    hiddenApps: Set<String>,
    onClose: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onPresetSelected: (AccentPreset) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onUserSelected: (String) -> Unit,
    onQuickConnectApproved: () -> Unit,
    onAppHiddenChange: (String, Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            var page by remember { mutableStateOf(SettingsPage.Main) }
            BackHandler(enabled = visible) {
                if (page != SettingsPage.Main) page = SettingsPage.Main else onClose()
            }
            Box(
                Modifier
                    .width(SidebarWidth)
                    .fillMaxHeight()
                    .background(SidebarColor)
                    // Keep D-pad focus inside the panel while it is open (but let focus go back to
                    // the home screen once it's closing).
                    .focusProperties { onExit = { if (visible) cancelFocusChange() } }
                    .focusGroup(),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "settingsPage",
                ) { current ->
                    when (current) {
                        SettingsPage.Main -> MainPage(
                            preset = preset,
                            serverUrl = serverUrl,
                            users = users,
                            activeUserId = activeUserId,
                            hiddenCount = allApps.count { it.packageName in hiddenApps },
                            onOpenSystemSettings = onOpenSystemSettings,
                            onPresetSelected = onPresetSelected,
                            onServerUrlChange = onServerUrlChange,
                            onUserSelected = onUserSelected,
                            onQuickConnectApproved = onQuickConnectApproved,
                            onOpenHiddenApps = { page = SettingsPage.HiddenApps },
                        )
                        SettingsPage.HiddenApps -> HiddenAppsPage(
                            apps = allApps,
                            hidden = hiddenApps,
                            onToggle = onAppHiddenChange,
                        )
                    }
                }
            }
        }
    }
}

/** Requests focus on [requester] once the page has laid out. */
@Composable
private fun InitialFocus(requester: FocusRequester) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

private val PagePadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp)

private fun LazyListScope.pageHeader(title: String, subtitle: String? = null) {
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
    serverUrl: String,
    users: List<JellyfinUser>,
    activeUserId: String?,
    hiddenCount: Int,
    onOpenSystemSettings: () -> Unit,
    onPresetSelected: (AccentPreset) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onUserSelected: (String) -> Unit,
    onQuickConnectApproved: () -> Unit,
    onOpenHiddenApps: () -> Unit,
) {
    var editingServer by remember { mutableStateOf(false) }
    var quickConnect by remember { mutableStateOf(false) }
    val firstItem = remember { FocusRequester() }
    InitialFocus(firstItem)

    val context = LocalContext.current
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
                icon = R.drawable.ic_grid,
                title = "Hidden apps",
                summary = if (hiddenCount == 1) "1 app hidden" else "$hiddenCount apps hidden",
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = onOpenHiddenApps,
            )
        }

        item { SectionTitle("Jellyfin") }
        item {
            SettingsRow(
                icon = R.drawable.ic_dns,
                title = "Server",
                summary = serverUrl.ifEmpty { "Not set" },
                trailingIcon = R.drawable.ic_chevron_right,
                onClick = { editingServer = true },
            )
        }

        item { SectionTitle("Users") }
        items(users, key = { it.id }) { user ->
            SettingsRow(
                icon = R.drawable.ic_person,
                title = user.name,
                summary = if (user.id == activeUserId) "Current user" else null,
                trailingIcon = if (user.id == activeUserId) R.drawable.ic_check else null,
                onClick = { onUserSelected(user.id) },
            )
        }
        item {
            SettingsRow(
                icon = R.drawable.ic_add,
                title = "Add user",
                summary = "Sign in with Quick Connect",
                trailingIcon = R.drawable.ic_link,
                onClick = { quickConnect = true },
            )
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

    if (editingServer) {
        ServerUrlDialog(
            current = serverUrl,
            onSave = {
                onServerUrlChange(it)
                editingServer = false
            },
            onDismiss = { editingServer = false },
        )
    }
    if (quickConnect) {
        QuickConnectDialog(
            serverUrl = serverUrl,
            onApproved = {
                onQuickConnectApproved()
                quickConnect = false
            },
            onDismiss = { quickConnect = false },
        )
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalAccent.current,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** A compact, full-width focusable settings entry. */
@Composable
private fun SettingsRow(
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
        colors = ClickableSurfaceDefaults.colors(
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
private fun ToggleSwitch(checked: Boolean) {
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
        colors = ClickableSurfaceDefaults.colors(
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
