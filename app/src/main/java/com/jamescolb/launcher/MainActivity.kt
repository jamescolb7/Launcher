package com.jamescolb.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jamescolb.launcher.ui.allapps.AllAppsScreen
import com.jamescolb.launcher.ui.background.AnimatedGradientBackground
import com.jamescolb.launcher.ui.home.HomeScreen
import com.jamescolb.launcher.ui.profiles.ProfilePickerScreen
import com.jamescolb.launcher.ui.settings.SettingsSidebar
import com.jamescolb.launcher.ui.theme.LauncherTheme

enum class Screen { Home, AllApps, Profiles }

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    /** Bumped when the Home button re-delivers our intent, to return to the home screen. */
    private var homeRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(viewModel, homeRequests, ::launch) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeRequests++
    }

    override fun onResume() {
        super.onResume()
        // Package broadcasts aren't delivered while we're stopped; catch up on return.
        viewModel.refreshApps()
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Couldn't open that", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Couldn't open that", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun LauncherApp(viewModel: LauncherViewModel, homeRequests: Int, launch: (Intent) -> Unit) {
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val hero by viewModel.hero.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val hiddenApps by viewModel.hiddenApps.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val settingsTileFocus = remember { FocusRequester() }
    LaunchedEffect(homeRequests) {
        screen = Screen.Home
        settingsOpen = false
    }
    // A launcher never exits on Back; it only returns to the home screen. (The sidebar has its
    // own, higher-priority handler while open.)
    BackHandler { screen = Screen.Home }

    // When the sidebar closes, put focus back on the Settings tile it was opened from.
    var sidebarWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settingsOpen) {
        if (sidebarWasOpen && !settingsOpen) {
            withFrameNanos { }
            runCatching { settingsTileFocus.requestFocus() }
        }
        sidebarWasOpen = settingsOpen
    }

    val quickConnectApproved = {
        val user = viewModel.addUser("New user ${users.size + 1}")
        viewModel.setActiveUser(user.id)
    }

    LauncherTheme(preset = preset) {
        Box(Modifier.fillMaxSize()) {
            AnimatedGradientBackground(preset)
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { current ->
                when (current) {
                    Screen.Home -> HomeScreen(
                        hero = hero,
                        userName = activeUser?.name ?: "Sign in",
                        continueWatching = viewModel.continueWatching,
                        apps = apps,
                        onHeroChange = viewModel::setHero,
                        onMediaClick = { /* Playback comes with the Jellyfin integration. */ },
                        onAppClick = { launch(it.launchIntent) },
                        onAllAppsClick = { screen = Screen.AllApps },
                        onSettingsClick = { settingsOpen = true },
                        onProfileClick = { screen = Screen.Profiles },
                        settingsTileFocus = settingsTileFocus,
                    )
                    Screen.AllApps -> AllAppsScreen(apps = apps, onAppClick = { launch(it.launchIntent) })
                    Screen.Profiles -> ProfilePickerScreen(
                        users = users,
                        activeUserId = activeUser?.id,
                        serverUrl = serverUrl,
                        onUserSelected = {
                            viewModel.setActiveUser(it)
                            screen = Screen.Home
                        },
                        onServerUrlChange = viewModel::setServerUrl,
                        onQuickConnectApproved = quickConnectApproved,
                    )
                }
            }
            SettingsSidebar(
                visible = settingsOpen,
                preset = preset,
                serverUrl = serverUrl,
                users = users,
                activeUserId = activeUser?.id,
                allApps = allApps,
                hiddenApps = hiddenApps,
                onClose = { settingsOpen = false },
                onOpenSystemSettings = {
                    launch(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onPresetSelected = viewModel::setPreset,
                onServerUrlChange = viewModel::setServerUrl,
                onUserSelected = viewModel::setActiveUser,
                onQuickConnectApproved = quickConnectApproved,
                onAppHiddenChange = viewModel::setAppHidden,
            )
        }
    }
}
