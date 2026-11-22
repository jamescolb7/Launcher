package com.jamescolb.launcher.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.ui.components.CardShape
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.components.LauncherCard
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.ProgressTrack
import com.jamescolb.launcher.ui.theme.TextPrimary

/** A Continue Watching tile: placeholder artwork with a progress bar along the bottom. */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LauncherCard(onClick = onClick, onFocused = onFocused, modifier = modifier) {
        // Placeholder art: a soft diagonal wash of the item's colors with the title, kept dim so
        // the focus fill and the progress bar stay readable.
        Box(
            Modifier
                .fillMaxSize()
                .clip(CardShape)
                .background(
                    Brush.linearGradient(
                        item.artColors.map { it.copy(alpha = 0.35f) } + Color.Transparent,
                    ),
                ),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        ProgressBar(
            progress = item.progress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(ProgressTrack),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(shape)
                .background(LocalAccent.current),
        )
    }
}

/**
 * An installed app. If the app ships a 16:9 TV banner it fills the card; otherwise the card shows
 * the app's name in its brand color. The square launcher icon is never used.
 */
@Composable
fun AppCard(
    app: InstalledApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    LauncherCard(onClick = onClick, onFocused = onFocused, modifier = modifier) {
        val banner = app.banner
        if (banner != null) {
            Image(
                bitmap = banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleLarge,
                color = app.brandColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp),
            )
        }
    }
}

/** The All Apps / Settings tiles at the end of the Apps row: an icon over a label. */
@Composable
fun UtilityCard(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LauncherCard(onClick = onClick, onFocused = onFocused, modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DrawableIcon(icon, tint = TextPrimary, size = 24.dp)
            Text(label, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
    }
}
