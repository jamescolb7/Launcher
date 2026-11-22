package com.jamescolb.launcher.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jamescolb.launcher.R
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.ui.components.CardShape
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.components.LauncherCard
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.ProgressTrack
import com.jamescolb.launcher.ui.theme.TextPrimary

/** A Continue Watching tile: the item's 16:9 art with a progress bar along the bottom. No text. */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LauncherCard(onClick = onClick, onFocused = onFocused, modifier = modifier) {
        ArtImage(url = item.thumbUrl)
        ProgressOverlay(progress = item.progress)
    }
}

val PosterWidth = 112.dp
val PosterHeight = 168.dp

/** A tall 2:3 cover for shows and movies, with progress and a watched / unwatched badge. */
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LauncherCard(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier,
        width = PosterWidth,
        height = PosterHeight,
        shape = RoundedCornerShape(12.dp),
    ) {
        ArtImage(url = item.posterUrl, shape = RoundedCornerShape(11.dp))
        WatchBadge(item, Modifier.align(Alignment.TopEnd).padding(6.dp))
        ProgressOverlay(progress = item.progress)
    }
}

/** Image filling the card; while loading or when missing, the card's own surface shows. */
@Composable
private fun BoxScope.ArtImage(url: String?, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(15.dp)) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp)
                .clip(shape),
        )
    }
}

/** Bottom scrim with the progress bar, shown only for partly watched items. */
@Composable
private fun BoxScope.ProgressOverlay(progress: Float) {
    if (progress <= 0f) return
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
    )
    ProgressBar(
        progress = progress,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}

/** Accent check when watched; unwatched-episode count for series. */
@Composable
private fun WatchBadge(item: MediaItem, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    when {
        item.played -> Box(
            modifier
                .size(22.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DrawableIcon(R.drawable.ic_check, tint = Color(0xFF16131F), size = 15.dp)
        }
        (item.unplayedCount ?: 0) > 0 -> Box(
            modifier
                .height(22.dp)
                .background(accent, RoundedCornerShape(50))
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.unplayedCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF16131F),
            )
        }
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
    onOptions: (() -> Unit)? = null,
    /** Being repositioned: shows arrows on both sides. */
    moving: Boolean = false,
) {
    LauncherCard(onClick = onClick, onFocused = onFocused, onOptions = onOptions, modifier = modifier) {
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
        if (moving) {
            MoveArrow(Modifier.align(Alignment.CenterStart).padding(start = 6.dp), pointsLeft = true)
            MoveArrow(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), pointsLeft = false)
        }
    }
}

/** Accent chevron badge shown on the sides of an app card while it is being moved. */
@Composable
private fun MoveArrow(modifier: Modifier, pointsLeft: Boolean) {
    Box(
        modifier
            .size(26.dp)
            .background(LocalAccent.current, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DrawableIcon(
            R.drawable.ic_chevron_right,
            tint = Color(0xFF16131F),
            size = 20.dp,
            modifier = Modifier.graphicsLayer { rotationZ = if (pointsLeft) 180f else 0f },
        )
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
