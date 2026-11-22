package com.jamescolb.launcher.ui.theme

import androidx.compose.ui.graphics.Color

/** Neutral surfaces shared by every preset. */
val CardSurface = Color(0xFF1F2124)
val CardSurfaceFocused = Color(0xFF2A2C31)
val TextPrimary = Color(0xFFE6E6EA)
val TextSecondary = Color(0xB3E6E6EA)
val TextMuted = Color(0x80E6E6EA)
/** Idle card outline: a dim gray that only just separates cards from the background. */
val OutlineIdle = Color(0x38FFFFFF)
val ProgressTrack = Color(0xFFD9D9DE)

/**
 * A selectable color theme. [accent] drives text highlights, focus borders, focus fills and
 * progress bars; [blobs] are the soft color fields drifting in the animated background, painted
 * over [base].
 */
data class AccentPreset(
    val id: String,
    val name: String,
    val accent: Color,
    val base: Color,
    val blobs: List<Color>,
)

object AccentPresets {
    val Amethyst = AccentPreset(
        id = "amethyst",
        name = "Amethyst",
        accent = Color(0xFFB69CFF),
        base = Color(0xFF15131F),
        blobs = listOf(Color(0xFF6A5ACD), Color(0xFF8E44AD), Color(0xFF2C3E70), Color(0xFF3B2A6B)),
    )
    val Ocean = AccentPreset(
        id = "ocean",
        name = "Ocean",
        accent = Color(0xFF7FD4F5),
        base = Color(0xFF0E1620),
        blobs = listOf(Color(0xFF1E6FA8), Color(0xFF138A9E), Color(0xFF1F3A6E), Color(0xFF0F5560)),
    )
    val Ember = AccentPreset(
        id = "ember",
        name = "Ember",
        accent = Color(0xFFFFB27A),
        base = Color(0xFF1C1210),
        blobs = listOf(Color(0xFFC0482B), Color(0xFFD9822B), Color(0xFF6E2335), Color(0xFF8A3A1C)),
    )
    val Forest = AccentPreset(
        id = "forest",
        name = "Forest",
        accent = Color(0xFF8EE3A8),
        base = Color(0xFF0F1914),
        blobs = listOf(Color(0xFF2E8B57), Color(0xFF1F6F6A), Color(0xFF3A5F2A), Color(0xFF1C4A3A)),
    )
    val Rose = AccentPreset(
        id = "rose",
        name = "Rose",
        accent = Color(0xFFFF9EC7),
        base = Color(0xFF1C1018),
        blobs = listOf(Color(0xFFC2337A), Color(0xFF8E3FA8), Color(0xFF6E2347), Color(0xFFB0476B)),
    )
    val Graphite = AccentPreset(
        id = "graphite",
        name = "Graphite",
        accent = Color(0xFFE8E8EC),
        base = Color(0xFF111214),
        blobs = listOf(Color(0xFF4A4E57), Color(0xFF30343B), Color(0xFF5B5F68), Color(0xFF25282D)),
    )

    val all = listOf(Amethyst, Ocean, Ember, Forest, Rose, Graphite)
    val default = Amethyst

    fun byId(id: String?): AccentPreset = all.firstOrNull { it.id == id } ?: default
}
