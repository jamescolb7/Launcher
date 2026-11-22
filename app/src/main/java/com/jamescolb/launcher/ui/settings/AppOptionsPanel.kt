package com.jamescolb.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jamescolb.launcher.R
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.ui.components.SidePanel
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary

/**
 * Right-side options for one app, opened by long-pressing an app card or pressing the remote's
 * options/menu button on it.
 */
@Composable
fun AppOptionsPanel(
    app: InstalledApp?,
    hidden: Boolean,
    /** Whether "Move" is offered (only on the home row, where order matters). */
    canMove: Boolean,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    // Keep showing the last app while the panel animates out.
    var shown by remember { mutableStateOf(app) }
    if (app != null) shown = app
    val current = shown

    SidePanel(visible = app != null, onBack = onDismiss) {
        if (current == null) return@SidePanel
        val first = remember(current.packageName) { FocusRequester() }
        InitialFocus(first)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PagePadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        bitmap = current.icon,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            current.label,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            current.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (canMove && !hidden) {
                item {
                    SettingsRow(
                        icon = R.drawable.ic_swap_horiz,
                        title = "Move",
                        summary = "Use left and right, then OK to place it",
                        onClick = onMove,
                        modifier = Modifier.focusRequester(first),
                    )
                }
            }
            item {
                SettingsRow(
                    icon = R.drawable.ic_visibility,
                    title = "Show on home screen",
                    summary = if (hidden) "Hidden from home and All Apps" else "Shown",
                    onClick = { onHiddenChange(!hidden) },
                    trailing = { ToggleSwitch(checked = !hidden) },
                    modifier = if (!canMove || hidden) Modifier.focusRequester(first) else Modifier,
                )
            }
            item {
                SettingsRow(
                    icon = R.drawable.ic_info,
                    title = "App info",
                    summary = "Storage, permissions, force stop",
                    trailingIcon = R.drawable.ic_open_in_new,
                    onClick = onAppInfo,
                )
            }
            item {
                SettingsRow(
                    icon = R.drawable.ic_delete,
                    title = "Uninstall",
                    summary = if (current.isSystem) "System apps can't be uninstalled" else "Remove from this device",
                    onClick = { if (!current.isSystem) onUninstall() },
                )
            }
        }
    }
}
