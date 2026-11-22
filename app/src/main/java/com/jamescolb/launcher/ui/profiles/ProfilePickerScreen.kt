package com.jamescolb.launcher.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jamescolb.launcher.R
import com.jamescolb.launcher.data.JellyfinUser
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.settings.QuickConnectDialog
import com.jamescolb.launcher.ui.settings.ServerUrlDialog
import com.jamescolb.launcher.ui.theme.CardSurface
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.TextMuted
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary

/** "Who's watching?" — switch Jellyfin user, add one, or change server. Placeholder data only. */
@Composable
fun ProfilePickerScreen(
    users: List<JellyfinUser>,
    activeUserId: String?,
    serverUrl: String,
    onUserSelected: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onQuickConnectApproved: () -> Unit,
) {
    var editingServer by remember { mutableStateOf(false) }
    var quickConnect by remember { mutableStateOf(false) }
    val activeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { activeFocus.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Who's watching?", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp), color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = serverUrl.ifEmpty { "No Jellyfin server set" },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(40.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            // Room for the focused tile's scale-up so it isn't clipped at the row's edges.
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            items(users, key = { it.id }) { user ->
                val active = user.id == activeUserId
                ProfileTile(
                    label = user.name,
                    selected = active,
                    onClick = { onUserSelected(user.id) },
                    modifier = if (active) Modifier.focusRequester(activeFocus) else Modifier,
                ) {
                    Text(
                        text = user.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                    )
                }
            }
            item(key = "add") {
                ProfileTile(label = "Add user", onClick = { quickConnect = true }) {
                    DrawableIcon(R.drawable.ic_add, tint = TextPrimary, size = 36.dp)
                }
            }
            item(key = "server") {
                ProfileTile(label = "Change server", onClick = { editingServer = true }) {
                    DrawableIcon(R.drawable.ic_dns, tint = TextPrimary, size = 32.dp)
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Text(
            "Placeholder: users are local only until Jellyfin sign-in is implemented.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
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
private fun ProfileTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val accent = LocalAccent.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(112.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (selected) lerp(CardSurface, accent, 0.25f) else CardSurface,
                focusedContainerColor = lerp(CardSurface, accent, 0.4f),
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.4f)),
                    shape = CircleShape,
                ),
                focusedBorder = Border(BorderStroke(3.dp, accent), shape = CircleShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) accent else TextSecondary,
        )
    }
}
