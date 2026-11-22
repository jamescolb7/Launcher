package com.jamescolb.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val SidePanelColor = Color(0xFF1B1C20)

/**
 * A panel that slides in from the right over a dimmed screen, like the stock Google TV quick
 * settings. While [visible], D-pad focus is kept inside the panel and Back calls [onBack].
 */
@Composable
fun SidePanel(
    visible: Boolean,
    onBack: () -> Unit,
    width: Dp = 380.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            BackHandler(enabled = visible, onBack = onBack)
            Box(
                Modifier
                    .width(width)
                    .fillMaxHeight()
                    .background(SidePanelColor)
                    // Keep D-pad focus inside the panel while it is open (but let focus go back to
                    // the screen underneath once it's closing).
                    .focusProperties { onExit = { if (visible) cancelFocusChange() } }
                    .focusGroup(),
                content = content,
            )
        }
    }
}
