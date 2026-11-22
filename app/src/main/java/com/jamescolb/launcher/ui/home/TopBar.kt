package com.jamescolb.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jamescolb.launcher.R
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.components.focusedFill
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TopBar(
    userName: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_jc),
            contentDescription = "jc",
            modifier = Modifier.height(32.dp),
            alpha = 0.85f,
        )
        Spacer(Modifier.weight(1f))
        ProfileButton(userName = userName, onClick = onProfileClick)
        Spacer(Modifier.width(16.dp))
        DrawableIcon(R.drawable.ic_wifi, tint = TextPrimary, size = 22.dp, contentDescription = "Wi-Fi")
        Spacer(Modifier.width(14.dp))
        Clock()
    }
}

/** The username pill. Focusable; opens the user / server picker. */
@Composable
private fun ProfileButton(userName: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = focusedFill(accent),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawableIcon(R.drawable.ic_account_circle, size = 24.dp)
            Text(userName, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val time by produceState(initialValue = "") {
        while (true) {
            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            value = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
            // Wake at the start of the next minute.
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    Text(
        text = time,
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
    )
}
