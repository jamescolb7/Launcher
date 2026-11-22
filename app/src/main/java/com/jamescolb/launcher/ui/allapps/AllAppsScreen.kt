package com.jamescolb.launcher.ui.allapps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.ui.components.CardWidth
import com.jamescolb.launcher.ui.home.AppCard
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary

@Composable
fun AllAppsScreen(apps: List<InstalledApp>, onAppClick: (InstalledApp) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Text(
            text = "All Apps",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(start = 52.dp),
        )
        Text(
            text = "${apps.size} installed",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(start = 52.dp, top = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CardWidth + 4.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppCard(
                        app = app,
                        onClick = { onAppClick(app) },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(CardWidth).padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
