package com.jamescolb.launcher.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import com.jamescolb.launcher.ui.theme.CardSurface
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.OutlineIdle
import com.jamescolb.launcher.ui.theme.TextPrimary

val CardShape = RoundedCornerShape(16.dp)
val CardWidth = 176.dp
val CardHeight = 99.dp

/** The fill a focused card gets: the dark card surface pulled toward the accent. */
@Composable
fun focusedFill(accent: Color = LocalAccent.current): Color = lerp(CardSurface, accent, 0.32f)

/** Content color while OK is held down: still light, just a little dimmer than focused. */
val PressedContent = Color.White.copy(alpha = 0.8f)

/** A pressed fill: the focused fill, slightly darkened, so pressing reads as a "push". */
fun pressedFill(focused: Color): Color = lerp(focused, Color.Black, 0.18f)

/**
 * Clickable-surface colors for the launcher. tv-material's defaults turn the content dark while
 * a surface is pressed; here pressed keeps light content over a slightly dimmed focus fill.
 */
@Composable
fun launcherSurfaceColors(
    containerColor: Color = Color.Transparent,
    contentColor: Color = TextPrimary,
    focusedContainerColor: Color = focusedFill(),
    focusedContentColor: Color = Color.White,
) = ClickableSurfaceDefaults.colors(
    containerColor = containerColor,
    contentColor = contentColor,
    focusedContainerColor = focusedContainerColor,
    focusedContentColor = focusedContentColor,
    pressedContainerColor = pressedFill(focusedContainerColor),
    pressedContentColor = PressedContent,
)

/** Same idea for tv-material [androidx.tv.material3.Button]s. */
@Composable
fun launcherButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.1f),
    contentColor = TextPrimary,
    focusedContainerColor = focusedFill(),
    focusedContentColor = Color.White,
    pressedContainerColor = pressedFill(focusedFill()),
    pressedContentColor = PressedContent,
)

/** Same idea for tv-material [androidx.tv.material3.OutlinedButton]s. */
@Composable
fun launcherOutlinedButtonColors() = OutlinedButtonDefaults.colors(
    contentColor = TextPrimary,
    focusedContainerColor = focusedFill(),
    focusedContentColor = Color.White,
    pressedContainerColor = pressedFill(focusedFill()),
    pressedContentColor = PressedContent,
)

/**
 * The common focusable tile: a dark rounded card with a thin outline that, on focus, gains
 * an accent border, an accent-tinted fill and a small scale-up. [onFocused] fires when it gains focus.
 */
@Composable
fun LauncherCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = CardWidth,
    height: Dp = CardHeight,
    shape: Shape = CardShape,
    containerColor: Color = CardSurface,
    onFocused: () -> Unit = {},
    /** Long-press OK, or the remote's options/menu button (common on Fire TV remotes). */
    onOptions: (() -> Unit)? = null,
    /** Overrides the idle outline (e.g. to highlight a card being moved). */
    border: Border? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current
    Surface(
        onClick = onClick,
        onLongClick = onOptions,
        modifier = modifier
            .size(width, height)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .then(if (onOptions != null) Modifier.onMenuKey(onOptions) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = launcherSurfaceColors(
            containerColor = containerColor,
            focusedContainerColor = focusedFill(accent),
        ),
        border = ClickableSurfaceDefaults.border(
            border = border ?: Border(BorderStroke(1.dp, OutlineIdle), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
            pressedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(),
        content = content,
    )
}

/** Calls [action] when the remote's options/menu key is released. */
fun Modifier.onMenuKey(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Menu) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyUp) action()
    true
}

@Composable
fun DrawableIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
    )
}
