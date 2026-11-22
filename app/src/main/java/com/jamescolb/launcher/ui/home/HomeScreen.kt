package com.jamescolb.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jamescolb.launcher.HeroContent
import com.jamescolb.launcher.HomeState
import com.jamescolb.launcher.R
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.data.jellyfin.MediaRow
import com.jamescolb.launcher.ui.components.CardHeight
import com.jamescolb.launcher.ui.components.CardShape
import com.jamescolb.launcher.ui.components.CardWidth
import com.jamescolb.launcher.ui.theme.CardSurface
import com.jamescolb.launcher.ui.theme.SectionHeader
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary


val ScreenPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp)
private val RowSpacing = Arrangement.spacedBy(16.dp)

/** Side padding inside the rows, so focused (scaled-up) cards are never clipped at the edges. */
private val RowContentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp)

/** Fixed hero height, so the rows below don't jump as focus moves. */
private val HeroHeight = 212.dp

/** Space kept above a focused card when its row scrolls up: the row header and padding. */
private val RowLeadIn = 40.dp

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    hero: HeroContent,
    userName: String,
    home: HomeState,
    apps: List<InstalledApp>,
    onHeroChange: (HeroContent) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onAppClick: (InstalledApp) -> Unit,
    onAllAppsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onConnectClick: () -> Unit,
    /** Long-press / options key on an app; the requester puts focus back on its card. */
    onAppOptions: (InstalledApp, FocusRequester) -> Unit,
    /** Package currently being repositioned in the Apps row, if any. */
    movingPackage: String?,
    onMoveApp: (String, Int) -> Unit,
    onMoveDone: () -> Unit,
    /** Show the Apps row above the Jellyfin rows instead of below them. */
    appsFirst: Boolean = false,
    settingsTileFocus: FocusRequester = remember { FocusRequester() },
) {
    // While moving an app, Back (like OK) just finishes the move.
    BackHandler(enabled = movingPackage != null, onBack = onMoveDone)
    val rows = home.rows
    val firstItem = remember { FocusRequester() }
    // Per-row scroll + last-focused card, kept here so rows remember them even after being
    // scrolled out of the list and recreated.
    val cwFocus = remember { RowFocusState() }
    val showsFocus = remember { RowFocusState() }
    val moviesFocus = remember { RowFocusState() }
    // Fresh state once apps first arrive, so the row starts at the first app.
    val appsFocus = remember(apps.isEmpty()) { RowFocusState() }

    // Focus the first card once there's something to focus: the first app when apps are on top;
    // otherwise the connect card when signed out, or the first media card once rows have loaded
    // (apps as a last resort).
    val firstTarget = when {
        appsFirst && apps.isNotEmpty() -> "apps"
        !home.signedIn -> "connect"
        rows.continueWatching.items.isNotEmpty() -> "cw"
        rows.latestShows.items.isNotEmpty() -> "shows"
        rows.latestMovies.items.isNotEmpty() -> "movies"
        home.loading -> null
        apps.isNotEmpty() -> "apps"
        else -> null
    }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(firstTarget) {
        if (firstTarget != null && !initialFocusDone) {
            withFrameNanos { }
            runCatching { firstItem.requestFocus() }
            initialFocusDone = true
        }
    }

    val rowsDefaultSpec = LocalBringIntoViewSpec.current
    val leadInPx = with(LocalDensity.current) { RowLeadIn.toPx() }
    // Vertical list: scroll so the focused row sits at the top, just under the hero.
    val pinToTop = remember(leadInPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                offset - leadInPx
        }
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(
            hero = hero,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.78f),
        )
        // Soft shadow behind the user / settings / Wi-Fi / clock so they stay readable over
        // bright backdrops. An ellipse anchored at the top-right corner that fits the box exactly,
        // so it fades to nothing at both the left and bottom edges (no hard cutoff).
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(width = 460.dp, height = 150.dp)
                .drawBehind {
                    val w = size.width
                    // Draw a circle of radius w in a w×w square, squashed vertically to w×h.
                    scale(scaleX = 1f, scaleY = size.height / w, pivot = Offset(w, 0f)) {
                        drawRect(
                            brush = Brush.radialGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.35f to Color.Black.copy(alpha = 0.42f),
                                0.65f to Color.Black.copy(alpha = 0.16f),
                                1f to Color.Transparent,
                                center = Offset(w, 0f),
                                radius = w,
                            ),
                            size = Size(w, w),
                        )
                    }
                },
        )
        Column(Modifier.fillMaxSize()) {
            TopBar(
                userName = userName,
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick,
                settingsFocus = settingsTileFocus,
                modifier = Modifier.padding(ScreenPadding),
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.height(HeroHeight).padding(horizontal = 52.dp)) {
                HeroSection(content = hero)
            }
            Spacer(Modifier.height(8.dp))

            CompositionLocalProvider(LocalBringIntoViewSpec provides pinToTop) {
                LazyColumn(
                    state = remember { LazyListState() },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    fun androidx.compose.foundation.lazy.LazyListScope.appsRowItem() {
                        item(key = "apps") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowsDefaultSpec) {
                                AppsRow(
                                    apps = apps,
                                    focus = appsFocus,
                                    firstItem = firstItem.takeIf { firstTarget == "apps" },
                                    movingPackage = movingPackage,
                                    onAppClick = onAppClick,
                                    onAppOptions = { app -> onAppOptions(app, appsFocus.requester(app.packageName)) },
                                    onMoveApp = onMoveApp,
                                    onMoveDone = onMoveDone,
                                    onAllAppsClick = onAllAppsClick,
                                    onHeroChange = onHeroChange,
                                )
                            }
                        }
                    }

                    if (appsFirst) appsRowItem()
                    item(key = "continue") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowsDefaultSpec) {
                            if (!home.signedIn) {
                                ConnectRow(
                                    onClick = onConnectClick,
                                    onHeroChange = onHeroChange,
                                    modifier = Modifier.focusRequester(firstItem),
                                )
                            } else {
                                MediaRowSection(
                                    title = "Continue Watching",
                                    row = rows.continueWatching,
                                    focus = cwFocus,
                                    loading = home.loading,
                                    wide = true,
                                    firstItem = firstItem.takeIf { firstTarget == "cw" },
                                    onClick = onMediaClick,
                                    onHeroChange = onHeroChange,
                                )
                            }
                        }
                    }
                    if (home.signedIn && (rows.latestShows.items.isNotEmpty() || rows.latestShows.error != null)) {
                        item(key = "shows") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowsDefaultSpec) {
                                MediaRowSection(
                                    title = "Recently Added Shows",
                                    row = rows.latestShows,
                                    focus = showsFocus,
                                    loading = home.loading,
                                    wide = false,
                                    firstItem = firstItem.takeIf { firstTarget == "shows" },
                                    onClick = onMediaClick,
                                    onHeroChange = onHeroChange,
                                )
                            }
                        }
                    }
                    if (home.signedIn && (rows.latestMovies.items.isNotEmpty() || rows.latestMovies.error != null)) {
                        item(key = "movies") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowsDefaultSpec) {
                                MediaRowSection(
                                    title = "Recently Added Movies",
                                    row = rows.latestMovies,
                                    focus = moviesFocus,
                                    loading = home.loading,
                                    wide = false,
                                    firstItem = firstItem.takeIf { firstTarget == "movies" },
                                    onClick = onMediaClick,
                                    onHeroChange = onHeroChange,
                                )
                            }
                        }
                    }
                    if (!appsFirst) appsRowItem()
                }
            }
        }
    }
}

@Composable
private fun MediaRowSection(
    title: String,
    row: MediaRow,
    focus: RowFocusState,
    loading: Boolean,
    wide: Boolean,
    firstItem: FocusRequester?,
    onClick: (MediaItem) -> Unit,
    onHeroChange: (HeroContent) -> Unit,
) {
    Column {
        RowHeader(title)
        when {
            row.items.isNotEmpty() -> LazyRow(
                state = focus.listState,
                modifier = Modifier.rowFocusGroup(focus, row.items.map { it.id }),
                contentPadding = RowContentPadding,
                horizontalArrangement = RowSpacing,
            ) {
                itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                    val modifier = Modifier
                        .rowFocusItem(focus, item.id)
                        .then(if (index == 0 && firstItem != null) Modifier.focusRequester(firstItem) else Modifier)
                    if (wide) {
                        MediaCard(
                            item = item,
                            onClick = { onClick(item) },
                            onFocused = { onHeroChange(HeroContent.Media(item)) },
                            modifier = modifier,
                        )
                    } else {
                        PosterCard(
                            item = item,
                            onClick = { onClick(item) },
                            onFocused = { onHeroChange(HeroContent.Media(item)) },
                            modifier = modifier,
                        )
                    }
                }
            }
            loading -> SkeletonRow(wide)
            row.error != null -> RowMessage("Couldn't load: ${row.error}")
            else -> RowMessage("Nothing here yet.")
        }
    }
}

/** Non-focusable placeholder cards shown while a row is loading for the first time. */
@Composable
private fun SkeletonRow(wide: Boolean) {
    Row(
        modifier = Modifier.padding(RowContentPadding),
        horizontalArrangement = RowSpacing,
    ) {
        repeat(if (wide) 4 else 7) {
            Box(
                Modifier
                    .size(if (wide) CardWidth else PosterWidth, if (wide) CardHeight else PosterHeight)
                    .background(CardSurface.copy(alpha = 0.6f), CardShape),
            )
        }
    }
}

@Composable
private fun RowMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(start = 52.dp, top = 10.dp, bottom = 18.dp),
    )
}

@Composable
private fun ConnectRow(
    onClick: () -> Unit,
    onHeroChange: (HeroContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column {
        RowHeader("Continue Watching")
        Row(Modifier.padding(RowContentPadding)) {
            UtilityCard(
                label = "Connect",
                icon = R.drawable.ic_dns,
                onClick = onClick,
                onFocused = {
                    onHeroChange(
                        HeroContent.Utility(
                            category = "Jellyfin",
                            title = "Connect a server",
                            description = "Sign in to your Jellyfin server with Quick Connect or a " +
                                "password. Items open in Wholphin.",
                        ),
                    )
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun AppsRow(
    apps: List<InstalledApp>,
    focus: RowFocusState,
    firstItem: FocusRequester?,
    movingPackage: String?,
    onAppClick: (InstalledApp) -> Unit,
    onAppOptions: (InstalledApp) -> Unit,
    onMoveApp: (String, Int) -> Unit,
    onMoveDone: () -> Unit,
    onAllAppsClick: () -> Unit,
    onHeroChange: (HeroContent) -> Unit,
) {
    Column {
        RowHeader(if (movingPackage != null) "Apps — moving: use left / right, then OK" else "Apps")
        LazyRow(
            state = focus.listState,
            modifier = Modifier.rowFocusGroup(focus, apps.map { it.packageName } + ALL_APPS_KEY),
            contentPadding = RowContentPadding,
            horizontalArrangement = RowSpacing,
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val moving = app.packageName == movingPackage
                // Keep the card on screen as it moves through the row.
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(moving, index) { if (moving) runCatching { bringIntoView.bringIntoView() } }
                AppCard(
                    app = app,
                    onClick = { onAppClick(app) },
                    onFocused = { onHeroChange(HeroContent.App(app)) },
                    onOptions = { onAppOptions(app) },
                    moving = moving,
                    modifier = Modifier
                        .bringIntoViewRequester(bringIntoView)
                        .then(if (moving) Modifier.moveKeys(app.packageName, onMoveApp, onMoveDone) else Modifier)
                        .rowFocusItem(focus, app.packageName)
                        .then(if (index == 0 && firstItem != null) Modifier.focusRequester(firstItem) else Modifier),
                )
            }
            item(key = "divider") { RowDivider() }
            item(key = ALL_APPS_KEY) {
                UtilityCard(
                    label = "All Apps",
                    icon = R.drawable.ic_grid,
                    onClick = onAllAppsClick,
                    modifier = Modifier.rowFocusItem(focus, ALL_APPS_KEY),
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
        }
    }
}

private const val ALL_APPS_KEY = "all-apps"

/**
 * Key handling while an app card is being moved: left/right reposition it, OK finishes, and
 * up/down are swallowed so focus stays on the card.
 */
private fun Modifier.moveKeys(packageName: String, onMove: (String, Int) -> Unit, onDone: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val down = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.DirectionLeft -> { if (down) onMove(packageName, -1); true }
            Key.DirectionRight -> { if (down) onMove(packageName, 1); true }
            Key.DirectionUp, Key.DirectionDown -> true
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (!down) onDone(); true }
            else -> false
        }
    }

@Composable
private fun RowHeader(text: String) {
    Text(
        text = text,
        style = SectionHeader,
        color = TextPrimary,
        modifier = Modifier.padding(start = 52.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun RowDivider(height: Dp = CardHeight) {
    Box(Modifier.height(height), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(1.dp)
                .height(height * 0.7f)
                .background(Color.White.copy(alpha = 0.35f)),
        )
    }
}
