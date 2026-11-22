package com.jamescolb.launcher.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.jamescolb.launcher.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun inter(weight: FontWeight, size: Int, line: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

/** Hero title: the large bold name in the middle of the screen. */
val HeroTitle = inter(FontWeight.Bold, 56, 64, -1f)

/** Small accent label above the hero title ("TV Show", "App"). */
val HeroCategory = inter(FontWeight.Medium, 18, 24, 0.2f)

/** Accent line under the title ("Episode • Season 1 • Episode 1"). */
val HeroSubtitle = inter(FontWeight.SemiBold, 18, 24)

/** Overview paragraph. */
val HeroBody = inter(FontWeight.Normal, 17, 26)

/** Row headers ("Continue Watching", "Apps"). */
val SectionHeader = inter(FontWeight.Medium, 18, 24)

val Typography = Typography(
    displayLarge = HeroTitle,
    headlineMedium = inter(FontWeight.Bold, 28, 36),
    headlineSmall = inter(FontWeight.SemiBold, 22, 28),
    titleLarge = inter(FontWeight.SemiBold, 20, 26),
    titleMedium = inter(FontWeight.Medium, 16, 22),
    titleSmall = inter(FontWeight.Medium, 14, 20),
    bodyLarge = inter(FontWeight.Normal, 16, 24),
    bodyMedium = inter(FontWeight.Normal, 14, 20),
    bodySmall = inter(FontWeight.Normal, 12, 16),
    labelLarge = inter(FontWeight.Medium, 15, 20),
    labelMedium = inter(FontWeight.Medium, 13, 18),
    labelSmall = inter(FontWeight.Medium, 11, 16, 0.3f),
)
