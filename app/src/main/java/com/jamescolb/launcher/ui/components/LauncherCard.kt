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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.jamescolb.launcher.ui.theme.CardSurface
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.OutlineIdle

val CardShape = RoundedCornerShape(16.dp)
val CardWidth = 176.dp
val CardHeight = 99.dp

/** The fill a focused card gets: the dark card surface pulled toward the accent. */
@Composable
fun focusedFill(accent: Color = LocalAccent.current): Color = lerp(CardSurface, accent, 0.32f)

/**
 * The common focusable tile: a dark rounded card with a thin light outline that, on focus, gains
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
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width, height)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = focusedFill(accent),
            pressedContainerColor = focusedFill(accent),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, OutlineIdle), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
            pressedBorder = Border(BorderStroke(2.dp, accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(),
        content = content,
    )
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
        tint = if (tint == Color.Unspecified) androidx.tv.material3.LocalContentColor.current else tint,
    )
}
