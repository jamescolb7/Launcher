package com.jamescolb.launcher.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.jamescolb.launcher.HeroContent
import com.jamescolb.launcher.R
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.ui.components.CardHeight
import com.jamescolb.launcher.ui.theme.SectionHeader
import com.jamescolb.launcher.ui.theme.TextPrimary

/** How many apps the home row shows before the All Apps / Settings tiles. */
private const val HOME_APP_COUNT = 8

val ScreenPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp)
private val RowSpacing = Arrangement.spacedBy(20.dp)

/** Side padding inside the rows, so focused (scaled-up) cards are never clipped at the edges. */
private val RowContentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    hero: HeroContent,
    userName: String,
    continueWatching: List<MediaItem>,
    apps: List<InstalledApp>,
    onHeroChange: (HeroContent) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onAppClick: (InstalledApp) -> Unit,
    onAllAppsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    settingsTileFocus: FocusRequester = remember { FocusRequester() },
) {
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(
            hero = hero,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.78f),
        )
        Column(Modifier.fillMaxSize()) {
            TopBar(
                userName = userName,
                onProfileClick = onProfileClick,
                modifier = Modifier.padding(ScreenPadding),
            )

            // Like the mockup: the hero takes its natural height and the rows follow it. A media hero
            // pushes the Apps row partly off-screen; focusing an app shrinks the hero and lifts it up.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentHeight(Alignment.Top, unbounded = true),
            ) {
                Spacer(Modifier.height(20.dp))
                HeroSection(
                    content = hero,
                    modifier = Modifier
                        .padding(horizontal = 52.dp)
                        .animateContentSize(tween(260)),
                )
                Spacer(Modifier.height(24.dp))

                RowHeader("Continue Watching")
                LazyRow(
                    modifier = Modifier.focusRestorer(firstCard),
                    contentPadding = RowContentPadding,
                    horizontalArrangement = RowSpacing,
                ) {
                    items(continueWatching, key = { it.id }) { item ->
                        MediaCard(
                            item = item,
                            onClick = { onMediaClick(item) },
                            onFocused = { onHeroChange(HeroContent.Media(item)) },
                            modifier = if (item == continueWatching.first()) {
                                Modifier.focusRequester(firstCard)
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                RowHeader("Apps")
                // New state once apps first arrive, so the row starts at the first app rather than
                // keeping the divider (the only keyed item before loading) anchored at the start.
                val appsRowState = remember(apps.isEmpty()) { LazyListState() }
                LazyRow(
                    state = appsRowState,
                    modifier = Modifier.focusRestorer(),
                    contentPadding = RowContentPadding,
                    horizontalArrangement = RowSpacing,
                ) {
                    items(apps.take(HOME_APP_COUNT), key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            onClick = { onAppClick(app) },
                            onFocused = { onHeroChange(HeroContent.App(app)) },
                        )
                    }
                    item(key = "divider") { RowDivider() }
                    item(key = "all-apps") {
                        UtilityCard(
                            label = "All Apps",
                            icon = R.drawable.ic_grid,
                            onClick = onAllAppsClick,
                            onFocused = {
                                onHeroChange(
                                    HeroContent.Utility(
                                        category = "Launcher",
                                        title = "All Apps",
                                        description = "Browse every app installed on this device.",
                                    ),
                                )
                            },
                        )
                    }
                    item(key = "settings") {
                        UtilityCard(
                            label = "Settings",
                            icon = R.drawable.ic_settings,
                            onClick = onSettingsClick,
                            modifier = Modifier.focusRequester(settingsTileFocus),
                            onFocused = {
                                onHeroChange(
                                    HeroContent.Utility(
                                        category = "Launcher",
                                        title = "Settings",
                                        description = "Open Launcher and system settings.",
                                    ),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RowHeader(text: String) {
    Text(
        text = text,
        style = SectionHeader,
        color = TextPrimary,
        modifier = Modifier.padding(start = 52.dp, bottom = 2.dp),
    )
}

@Composable
private fun RowDivider() {
    Box(Modifier.height(CardHeight), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(1.dp)
                .height(CardHeight * 0.7f)
                .background(Color.White.copy(alpha = 0.35f)),
        )
    }
}
