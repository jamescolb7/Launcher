package com.jamescolb.launcher.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Current accent color, animated when the preset changes. Read via [LocalAccent].current. */
val LocalAccent = compositionLocalOf { AccentPresets.default.accent }

/** Current preset, for components that need more than the accent (e.g. the background). */
val LocalPreset = staticCompositionLocalOf { AccentPresets.default }

@Composable
fun LauncherTheme(
    preset: AccentPreset = AccentPresets.default,
    content: @Composable () -> Unit,
) {
    val accent by animateColorAsState(preset.accent, tween(600), label = "accent")
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF16131F),
        secondary = accent,
        surface = CardSurface,
        onSurface = TextPrimary,
        surfaceVariant = CardSurfaceFocused,
        onSurfaceVariant = TextSecondary,
        background = preset.base,
        onBackground = TextPrimary,
        border = OutlineIdle,
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalPreset provides preset) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
