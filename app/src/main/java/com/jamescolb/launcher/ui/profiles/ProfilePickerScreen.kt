package com.jamescolb.launcher.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.jamescolb.launcher.ui.components.launcherSurfaceColors
import com.jamescolb.launcher.ui.components.focusedFill
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.OutlineIdle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jamescolb.launcher.R
import com.jamescolb.launcher.data.JellyfinAccount
import com.jamescolb.launcher.data.JellyfinServer
import com.jamescolb.launcher.ui.components.AvatarTile
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary

/** "Who's watching?": switch between signed-in Jellyfin accounts, or add a user / server. */
@Composable
fun ProfilePickerScreen(
    accounts: List<JellyfinAccount>,
    servers: List<JellyfinServer>,
    activeKey: String?,
    avatarUrl: (JellyfinAccount) -> String?,
    onSelect: (String) -> Unit,
    onAddUser: (JellyfinServer?) -> Unit,
    onAddServer: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    val activeServer = servers.firstOrNull { s -> accounts.any { it.key == activeKey && it.serverId == s.id } }
    val serverNames = servers.associate { it.id to it.name }
    // Active server's accounts first, then the rest.
    val ordered = accounts.sortedBy { if (it.serverId == activeServer?.id) 0 else 1 }
    val focusKey = activeKey ?: ordered.firstOrNull()?.key

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Who's watching?",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp),
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = activeServer?.let { "${it.name}  •  ${it.url}" } ?: "No Jellyfin server connected",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(36.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            items(ordered, key = { it.key }) { account ->
                AvatarTile(
                    label = account.name,
                    caption = if (servers.size > 1) serverNames[account.serverId] else null,
                    imageUrl = avatarUrl(account),
                    selected = account.key == activeKey,
                    onClick = { onSelect(account.key) },
                    width = TileWidth,
                    modifier = if (account.key == focusKey) Modifier.focusRequester(focus) else Modifier,
                )
            }
            item(key = "add-user") {
                AvatarTile(
                    label = "Add user",
                    onClick = { onAddUser(activeServer) },
                    width = TileWidth,
                    modifier = if (focusKey == null) Modifier.focusRequester(focus) else Modifier,
                    icon = { DrawableIcon(R.drawable.ic_add, tint = TextPrimary, size = 36.dp) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        AddServerPill(onClick = onAddServer)
    }
}

/** Avatar (104dp) plus a little room for the name and the focused scale-up. */
private val TileWidth = 128.dp

/** Small, secondary pill button below the users. */
@Composable
private fun AddServerPill(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = launcherSurfaceColors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = TextSecondary,
            focusedContainerColor = focusedFill(accent),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, OutlineIdle), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawableIcon(R.drawable.ic_dns, size = 16.dp)
            Text("Add server", style = MaterialTheme.typography.labelLarge)
        }
    }
}
