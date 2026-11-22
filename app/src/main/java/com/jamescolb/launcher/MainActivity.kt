package com.jamescolb.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.ui.settings.AppOptionsPanel
import com.jamescolb.launcher.ui.settings.JellyfinSettings
import com.jamescolb.launcher.ui.settings.SettingsSidebar
import com.jamescolb.launcher.ui.signin.SignInScreen
import com.jamescolb.launcher.data.JellyfinServer
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.data.WholphinLauncher
import com.jamescolb.launcher.ui.theme.LauncherTheme

enum class Screen { Home, AllApps, Profiles, SignIn }

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    /** Bumped when the Home button re-delivers our intent, to return to the home screen. */
    private var homeRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(viewModel, homeRequests, ::launch, ::openInWholphin) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeRequests++
    }

    override fun onResume() {
        super.onResume()
        // Package broadcasts aren't delivered while we're stopped; catch up on return.
        viewModel.refreshApps()
        // Pick up watch progress changed in Wholphin (or elsewhere).
        viewModel.refreshHome()
    }

    private fun openInWholphin(item: MediaItem) {
        val account = viewModel.activeAccount.value ?: return
        if (!WholphinLauncher.isInstalled(this)) {
            Toast.makeText(this, "Wholphin isn't installed", Toast.LENGTH_SHORT).show()
            return
        }
        launch(WholphinLauncher.detailsIntent(item.id, account))
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
private fun LauncherApp(
    viewModel: LauncherViewModel,
    homeRequests: Int,
    launch: (Intent) -> Unit,
    openMedia: (MediaItem) -> Unit,
) {
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val hero by viewModel.hero.collectAsStateWithLifecycle()
    val home by viewModel.home.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val hiddenApps by viewModel.hiddenApps.collectAsStateWithLifecycle()
    val rememberUser by viewModel.rememberUser.collectAsStateWithLifecycle()
    val userPickRequired by viewModel.userPickRequired.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val settingsTileFocus = remember { FocusRequester() }

    // App options panel (long-press / options key on an app) and home-row move mode.
    var optionsApp by remember { mutableStateOf<InstalledApp?>(null) }
    var optionsCanMove by remember { mutableStateOf(false) }
    var optionsReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var movingPackage by remember { mutableStateOf<String?>(null) }

    // "Remember user" off: the picker stands in for everything except sign-in until someone is
    // chosen (Back / Home can't skip past it).
    val shownScreen = if (userPickRequired && screen != Screen.SignIn) Screen.Profiles else screen
    LaunchedEffect(userPickRequired) {
        if (userPickRequired) {
            settingsOpen = false
            optionsApp = null
            movingPackage = null
        }
    }

    LaunchedEffect(homeRequests) {
        if (screen == Screen.SignIn) viewModel.cancelSignIn()
        screen = Screen.Home
        settingsOpen = false
        optionsApp = null
        movingPackage = null
    }

    // Reload the Jellyfin rows whenever the home screen comes (back) into view.
    LaunchedEffect(screen, settingsOpen) {
        if (screen == Screen.Home && !settingsOpen) viewModel.refreshHome()
    }

    // When the options panel closes, return focus to the app card it was opened from.
    var optionsWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(optionsApp) {
        if (optionsWasOpen && optionsApp == null) {
            withFrameNanos { }
            runCatching { optionsReturnFocus?.requestFocus() }
        }
        optionsWasOpen = optionsApp != null
    }
    val openOptions = { app: InstalledApp, returnFocus: FocusRequester, canMove: Boolean ->
        optionsReturnFocus = returnFocus
        optionsCanMove = canMove
        optionsApp = app
    }
    fun packageUri(pkg: String) = Uri.fromParts("package", pkg, null)
    // A launcher never exits on Back; it only returns to the home screen. (The sidebar and the
    // sign-in flow have their own, higher-priority handlers.)
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

    val openSignIn = { server: JellyfinServer? ->
        viewModel.startSignIn(server)
        settingsOpen = false
        screen = Screen.SignIn
    }

    LauncherTheme(preset = preset) {
        Box(Modifier.fillMaxSize()) {
            AnimatedGradientBackground(preset)
            AnimatedContent(
                targetState = shownScreen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { current ->
                when (current) {
                    Screen.Home -> HomeScreen(
                        hero = hero,
                        userName = activeAccount?.name ?: "Sign in",
                        home = home,
                        apps = apps,
                        onHeroChange = viewModel::setHero,
                        onMediaClick = openMedia,
                        onAppClick = { launch(it.launchIntent) },
                        onAllAppsClick = { screen = Screen.AllApps },
                        onSettingsClick = { settingsOpen = true },
                        onProfileClick = {
                            if (accounts.isEmpty()) openSignIn(null) else screen = Screen.Profiles
                        },
                        onConnectClick = { openSignIn(null) },
                        onAppOptions = { app, focus -> openOptions(app, focus, true) },
                        movingPackage = movingPackage,
                        onMoveApp = viewModel::moveApp,
                        onMoveDone = { movingPackage = null },
                        settingsTileFocus = settingsTileFocus,
                    )
                    Screen.AllApps -> AllAppsScreen(
                        apps = apps,
                        onAppClick = { launch(it.launchIntent) },
                        onAppOptions = { app, focus -> openOptions(app, focus, false) },
                    )
                    Screen.Profiles -> ProfilePickerScreen(
                        accounts = accounts,
                        servers = servers,
                        activeKey = activeAccount?.key,
                        avatarUrl = viewModel::userImageUrl,
                        onSelect = {
                            viewModel.switchAccount(it)
                            screen = Screen.Home
                        },
                        onAddUser = { openSignIn(it) },
                        onAddServer = { openSignIn(null) },
                    )
                    Screen.SignIn -> SignInScreen(viewModel = viewModel, onClose = { screen = Screen.Home })
                }
            }
            SettingsSidebar(
                visible = settingsOpen,
                preset = preset,
                jellyfin = JellyfinSettings(
                    server = activeServer,
                    accounts = accounts.filter { it.serverId == activeServer?.id },
                    activeAccount = activeAccount,
                    onServerClick = { openSignIn(null) },
                    onAccountSelected = viewModel::switchAccount,
                    onAddUser = { openSignIn(activeServer) },
                    onSignOut = viewModel::signOut,
                    onRewatchingChange = viewModel::setRewatching,
                    rememberUser = rememberUser,
                    onRememberUserChange = viewModel::setRememberUser,
                ),
                allApps = allApps,
                hiddenApps = hiddenApps,
                onClose = { settingsOpen = false },
                onOpenSystemSettings = {
                    launch(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onPresetSelected = viewModel::setPreset,
                onAppHiddenChange = viewModel::setAppHidden,
            )
            AppOptionsPanel(
                app = optionsApp,
                hidden = optionsApp?.packageName in hiddenApps,
                canMove = optionsCanMove,
                onDismiss = { optionsApp = null },
                onMove = {
                    movingPackage = optionsApp?.packageName
                    optionsApp = null
                },
                onHiddenChange = { hide -> optionsApp?.let { viewModel.setAppHidden(it.packageName, hide) } },
                onAppInfo = {
                    optionsApp?.let {
                        launch(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(it.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                onUninstall = {
                    optionsApp?.let {
                        launch(Intent(Intent.ACTION_DELETE, packageUri(it.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    optionsApp = null
                },
            )
        }
    }
}
