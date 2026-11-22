package com.jamescolb.launcher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.jamescolb.launcher.HeroContent
import com.jamescolb.launcher.data.MediaItem

/**
 * Backdrop art for the focused media item, placed top-right and faded out toward the left and
 * the bottom so it blends into the animated background (the old Plex look). Apps and utility
 * tiles show no backdrop. Until Jellyfin images exist, [MediaItem.backdrop] is null and a
 * placeholder built from the item's colors is drawn instead.
 */
@Composable
fun HeroBackdrop(hero: HeroContent, modifier: Modifier = Modifier) {
    val item = (hero as? HeroContent.Media)?.item
    Crossfade(
        targetState = item,
        animationSpec = tween(450),
        modifier = modifier.fadeLeftAndBottom(),
        label = "backdrop",
    ) { media ->
        if (media == null) {
            Box(Modifier.fillMaxSize())
        } else {
            val image = media.backdrop
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlaceholderArt(media.artColors)
            }
        }
    }
}

/** Soft, out-of-focus color blobs standing in for real artwork. */
@Composable
private fun PlaceholderArt(colors: List<Color>) {
    val a = colors.getOrElse(0) { Color.Gray }
    val b = colors.getOrElse(1) { a }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(b.copy(alpha = 0.55f), a.copy(alpha = 0.7f))))
        drawRect(
            Brush.radialGradient(
                listOf(a.copy(alpha = 0.8f), Color.Transparent),
                center = Offset(size.width * 0.62f, size.height * 0.38f),
                radius = size.minDimension * 0.55f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(b.copy(alpha = 0.7f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.8f),
                radius = size.minDimension * 0.5f,
            ),
        )
    }
}

/** Masks content so it is fully visible at the top-right and fades to nothing left and down. */
private fun Modifier.fadeLeftAndBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.25f to Color.Black.copy(alpha = 0.25f),
                0.6f to Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black,
                0.5f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
