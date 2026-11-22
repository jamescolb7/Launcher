package com.jamescolb.launcher.ui.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jamescolb.launcher.ui.theme.AccentPreset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A slow-moving "mesh" gradient: a dark base with a few large, soft radial color fields that drift
 * around. Colors come from the [preset] and cross-fade when it changes. Every value is read inside
 * the draw lambda, so the animation only redraws and never recomposes.
 */
@Composable
fun AnimatedGradientBackground(preset: AccentPreset, modifier: Modifier = Modifier) {
    val base = animateColorAsState(preset.base, tween(COLOR_FADE_MS), label = "base")
    val blobColors: List<State<Color>> = preset.blobs.take(BLOBS.size).mapIndexed { i, c ->
        animateColorAsState(c, tween(COLOR_FADE_MS), label = "blob$i")
    }

    val transition = rememberInfiniteTransition(label = "background")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = time.value * 2f * PI.toFloat()
        drawRect(base.value)

        BLOBS.forEachIndexed { i, blob ->
            val color = blobColors.getOrNull(i)?.value ?: return@forEachIndexed
            // Integer frequencies keep the loop seamless when time wraps 1 -> 0.
            val center = Offset(
                x = w * (blob.x + blob.driftX * sin(t * blob.freq + blob.phase)),
                y = h * (blob.y + blob.driftY * cos(t * blob.freq + blob.phase * 1.3f)),
            )
            val radius = max(w, h) * blob.radius
            drawRect(
                Brush.radialGradient(
                    0f to color.copy(alpha = blob.alpha),
                    0.45f to color.copy(alpha = blob.alpha * 0.45f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
            )
        }

        // Darken the left side and bottom so text and cards stay readable.
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.55f to Color.Black.copy(alpha = 0.15f),
                1f to Color.Transparent,
            ),
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.25f),
                0.25f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.35f),
            ),
        )
    }
}

private class Blob(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val driftX: Float,
    val driftY: Float,
    val freq: Int,
    val phase: Float,
)

/** Placement roughly follows the mockup: bright top-right, warm bottom-center, dim elsewhere. */
private val BLOBS = listOf(
    Blob(x = 0.85f, y = 0.28f, radius = 0.42f, alpha = 0.85f, driftX = 0.06f, driftY = 0.08f, freq = 1, phase = 0f),
    Blob(x = 0.68f, y = 0.98f, radius = 0.40f, alpha = 0.80f, driftX = 0.10f, driftY = 0.05f, freq = 1, phase = 2.1f),
    Blob(x = 0.42f, y = 0.30f, radius = 0.45f, alpha = 0.55f, driftX = 0.08f, driftY = 0.10f, freq = 2, phase = 4.2f),
    Blob(x = 0.12f, y = 0.85f, radius = 0.38f, alpha = 0.45f, driftX = 0.06f, driftY = 0.06f, freq = 1, phase = 1.0f),
)

private const val LOOP_MS = 48_000
private const val COLOR_FADE_MS = 900
