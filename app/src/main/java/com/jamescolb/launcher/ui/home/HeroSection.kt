package com.jamescolb.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.jamescolb.launcher.HeroContent
import com.jamescolb.launcher.ui.theme.HeroBody
import com.jamescolb.launcher.ui.theme.HeroCategory
import com.jamescolb.launcher.ui.theme.HeroSubtitle
import com.jamescolb.launcher.ui.theme.HeroTitle
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary

/** The large text block describing whatever card currently has focus. */
@Composable
fun HeroSection(content: HeroContent, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = content,
        modifier = modifier.fillMaxWidth(),
        contentKey = { it.key },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(260)) { it / 12 })
                .togetherWith(fadeOut(tween(140)))
                .using(SizeTransform(clip = false) { _, _ -> tween(260) })
        },
        label = "hero",
    ) { hero ->
        when (hero) {
            is HeroContent.Media -> HeroText(
                category = hero.item.type.label,
                title = hero.item.title,
                subtitle = hero.item.subtitle,
                description = hero.item.overview.orEmpty(),
            )
            is HeroContent.App -> HeroText(
                category = "App",
                title = hero.app.label,
                subtitle = null,
                description = "Open ${hero.app.label}.",
            )
            is HeroContent.Utility -> HeroText(
                category = hero.category,
                title = hero.title,
                subtitle = null,
                description = hero.description,
            )
        }
    }
}

private val HeroContent.key: String
    get() = when (this) {
        is HeroContent.Media -> "media:${item.id}"
        is HeroContent.App -> "app:${app.packageName}"
        is HeroContent.Utility -> "util:$title"
    }

/** About half of a 960dp-wide TV screen, after the left padding. */
private val HeroTextWidth = 440.dp

@Composable
private fun HeroText(category: String, title: String, subtitle: String?, description: String) {
    val accent = LocalAccent.current
    // The Box drops AnimatedContent's min-width so the column can stay narrower than the screen.
    // Text keeps to roughly the left half; the right half is left for the backdrop art.
    Box { Column(Modifier.widthIn(max = 640.dp)) {
        Text(category, style = HeroCategory, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = HeroTitle,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = HeroSubtitle,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = HeroTextWidth),
            )
        }
        if (description.isNotEmpty()) Spacer(Modifier.height(8.dp))
        if (description.isNotEmpty()) Text(
            text = description,
            style = HeroBody,
            color = TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = HeroTextWidth),
        )
    } }
}
