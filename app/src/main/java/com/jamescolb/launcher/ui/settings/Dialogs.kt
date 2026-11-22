package com.jamescolb.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.jamescolb.launcher.ui.theme.Inter
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.OutlineIdle
import com.jamescolb.launcher.ui.theme.TextMuted
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private val PanelShape = RoundedCornerShape(24.dp)

@Composable
private fun DialogPanel(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(androidx.compose.ui.graphics.Color(0xF2191A1F), PanelShape)
                .border(1.dp, OutlineIdle.copy(alpha = 0.3f), PanelShape)
                .padding(32.dp),
            content = content,
        )
    }
}

/** Edits the Jellyfin server address. Only stores it — nothing connects yet. */
@Composable
fun ServerUrlDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.ifEmpty { "https://" }) }
    val fieldFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val fieldFocused by interaction.collectIsFocusedAsState()
    val accent = LocalAccent.current
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    DialogPanel(onDismiss) {
        Text("Jellyfin server", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter the address of your Jellyfin server. Connecting isn't implemented yet; the address " +
                "is only saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            interactionSource = interaction,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                .border(
                    width = if (fieldFocused) 2.dp else 1.dp,
                    color = if (fieldFocused) accent else OutlineIdle.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Example: http://192.168.1.10:8096",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(text) }) { Text("Save") }
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

/**
 * Placeholder Quick Connect flow: shows a made-up code and a "waiting" state. "Simulate approval"
 * stands in for the server confirming the code.
 */
@Composable
fun QuickConnectDialog(serverUrl: String, onApproved: () -> Unit, onDismiss: () -> Unit) {
    val code = remember { (1..6).map { "0123456789".random() }.joinToString("") }
    val accent = LocalAccent.current
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dots = (dots + 1) % 4
        }
    }

    DialogPanel(onDismiss) {
        Text("Add user with Quick Connect", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "On a device already signed in to Jellyfin, open Settings → Quick Connect and enter this code.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(vertical = 20.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                text = code.chunked(3).joinToString("  "),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = 6.sp,
                ),
                color = accent,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Waiting for approval" + ".".repeat(dots),
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
        )
        Text(
            text = "Server: " + serverUrl.ifEmpty { "not set" } + "  •  placeholder, no network",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onApproved) { Text("Simulate approval") }
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}
