package com.jamescolb.launcher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.jamescolb.launcher.HeroContent

/**
 * Backdrop art for the focused media item, placed top-right and faded out toward the left and
 * the bottom so it blends into the animated background (the old Plex look). Apps and utility
 * tiles, and items without backdrop art, show none.
 */
@Composable
fun HeroBackdrop(hero: HeroContent, modifier: Modifier = Modifier) {
    val url = (hero as? HeroContent.Media)?.item?.backdropUrl
    Crossfade(
        targetState = url,
        animationSpec = tween(450),
        modifier = modifier.fadeLeftAndBottom(),
        label = "backdrop",
    ) { backdrop ->
        if (backdrop == null) {
            Box(Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
