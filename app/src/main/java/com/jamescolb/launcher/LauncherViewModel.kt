package com.jamescolb.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamescolb.launcher.data.AppRepository
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.data.JellyfinAccount
import com.jamescolb.launcher.data.JellyfinServer
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.data.SettingsRepository
import com.jamescolb.launcher.data.jellyfin.AuthResult
import com.jamescolb.launcher.data.jellyfin.ClientInfo
import com.jamescolb.launcher.data.jellyfin.HomeRows
import com.jamescolb.launcher.data.jellyfin.JellyfinClient
import com.jamescolb.launcher.data.jellyfin.JellyfinRepository
import com.jamescolb.launcher.data.jellyfin.UserDto
import com.jamescolb.launcher.ui.theme.AccentPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the hero area at the top of the home screen is describing. */
sealed interface HeroContent {
    data class Media(val item: MediaItem) : HeroContent
    data class App(val app: InstalledApp) : HeroContent
    data class Utility(val category: String, val title: String, val description: String) : HeroContent
}

data class HomeState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val rows: HomeRows = HomeRows(),
)

/** The multi-step add-server / add-user flow. */
sealed interface SignInStep {
    data class EnterServer(val url: String = "", val busy: Boolean = false, val error: String? = null) : SignInStep
    data class ChooseUser(
        val server: JellyfinServer,
        val version: String?,
        val publicUsers: List<UserDto>,
        val quickConnectEnabled: Boolean,
        val error: String? = null,
    ) : SignInStep
    data class Password(
        val server: JellyfinServer,
        val username: String,
        /** True when picked from the public user list (name is fixed). */
        val fixedUser: Boolean,
        val busy: Boolean = false,
        val error: String? = null,
    ) : SignInStep
    data class QuickConnect(val server: JellyfinServer, val code: String? = null, val error: String? = null) : SignInStep
    data object Done : SignInStep
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val settings = SettingsRepository(application)
    private val appRepository = AppRepository(application)
    private val packageReceiver: BroadcastReceiver

    val client = JellyfinClient(
        http = JellyfinClient.defaultHttp(),
        clientInfo = ClientInfo(
            clientName = "JC Launcher",
            deviceName = Build.MODEL ?: "Android TV",
            deviceId = settings.deviceId,
            version = runCatching {
                application.packageManager.getPackageInfo(application.packageName, 0).versionName
            }.getOrNull() ?: "1.0",
        ),
    )
    private val jellyfin = JellyfinRepository(client)

    // --- Apps ------------------------------------------------------------------------------------

    /** Every launchable app, including hidden ones (for the Hidden apps settings page). */
    val allApps: StateFlow<List<InstalledApp>> = appRepository.apps

    /**
     * Apps shown on the home row and in All Apps: hidden ones removed, in the user's arranged
     * order (apps never arranged follow alphabetically).
     */
    val apps: StateFlow<List<InstalledApp>> =
        combine(appRepository.apps, settings.hiddenApps, settings.appOrder) { apps, hidden, order ->
            val rank = order.withIndex().associate { (i, pkg) -> pkg to i }
            apps.filterNot { it.packageName in hidden }
                .sortedWith(compareBy<InstalledApp> { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Moves an app one place left (-1) or right (+1) in the home row and saves the order. */
    fun moveApp(packageName: String, delta: Int) {
        val current = apps.value.map { it.packageName }.toMutableList()
        val from = current.indexOf(packageName)
        val to = from + delta
        if (from < 0 || to !in current.indices) return
        current.add(to, current.removeAt(from))
        // Keep positions of hidden apps that were arranged before, after the visible ones.
        settings.setAppOrder(current + settings.appOrder.value.filterNot { it in current })
    }

    val hiddenApps: StateFlow<Set<String>> = settings.hiddenApps
    val preset: StateFlow<AccentPreset> = settings.preset

    // --- Accounts --------------------------------------------------------------------------------

    val servers: StateFlow<List<JellyfinServer>> = settings.servers
    val accounts: StateFlow<List<JellyfinAccount>> = settings.accounts

    val activeAccount: StateFlow<JellyfinAccount?> =
        combine(settings.accounts, settings.activeAccountKey) { accounts, key ->
            accounts.firstOrNull { it.key == key }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeServer: StateFlow<JellyfinServer?> =
        combine(settings.servers, activeAccount) { servers, account ->
            servers.firstOrNull { it.id == account?.serverId }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun userImageUrl(account: JellyfinAccount): String? {
        val server = servers.value.firstOrNull { it.id == account.serverId } ?: return null
        return account.imageTag?.let { client.userImageUrl(server.url, account.userId, it) }
    }

    val rememberUser: StateFlow<Boolean> = settings.rememberUser

    fun setRememberUser(remember: Boolean) = settings.setRememberUser(remember)

    /**
     * True while a user has to be chosen before the home screen is shown: "Remember user" is off
     * and the launcher just started or the device just woke up.
     */
    private val _userPickRequired = MutableStateFlow(
        !settings.rememberUser.value && settings.accounts.value.isNotEmpty(),
    )
    val userPickRequired: StateFlow<Boolean> = _userPickRequired.asStateFlow()

    /** Asks for a user again (if "Remember user" is off), hiding the previous user's rows. */
    fun requireUserPickIfNeeded() {
        if (settings.rememberUser.value || settings.accounts.value.isEmpty()) return
        _userPickRequired.value = true
        homeJob?.cancel()
        _home.value = HomeState()
        _hero.value = NOT_SIGNED_IN_HERO
    }

    fun switchAccount(key: String) {
        settings.setActiveAccount(key)
        _userPickRequired.value = false
        refreshHome()
    }

    fun setRewatching(enabled: Boolean) {
        activeAccount.value?.let { settings.setRewatching(it.key, enabled) }
        refreshHome()
    }

    fun signOut() {
        val account = activeAccount.value ?: return
        val server = activeServer.value
        settings.removeAccount(account.key)
        if (server != null) viewModelScope.launch { client.logout(server.url, account.token) }
    }

    // --- Home ------------------------------------------------------------------------------------

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _hero = MutableStateFlow<HeroContent>(NOT_SIGNED_IN_HERO)
    val hero: StateFlow<HeroContent> = _hero.asStateFlow()

    private var homeJob: Job? = null
    private var homeJobKey: Any? = null

    /**
     * Reloads the home rows. Called when the home screen is shown, when the app returns to the
     * foreground, and when the account or its rewatching option changes. A request identical to
     * one already in flight is ignored.
     */
    fun refreshHome() {
        // Nothing to load until someone picks a user.
        if (_userPickRequired.value) return
        val account = activeAccount.value
        val server = activeServer.value
        val key = Triple(account?.key, account?.rewatching, server?.url)
        if (homeJob?.isActive == true && homeJobKey == key) return
        homeJob?.cancel()
        homeJobKey = key
        if (account == null || server == null) {
            _home.value = HomeState(signedIn = false)
            if (_hero.value !is HeroContent.App) _hero.value = NOT_SIGNED_IN_HERO
            return
        }
        _home.value = _home.value.copy(signedIn = true, loading = true)
        homeJob = viewModelScope.launch {
            val rows = jellyfin.loadHome(server, account)
            _home.value = HomeState(signedIn = true, loading = false, rows = rows)
            // Keep the hero in sync with refreshed data (progress, new items).
            val all = rows.continueWatching.items + rows.latestShows.items + rows.latestMovies.items
            when (val current = _hero.value) {
                is HeroContent.Media -> all.firstOrNull { it.id == current.item.id }?.let {
                    _hero.value = HeroContent.Media(it)
                }
                is HeroContent.App -> Unit
                is HeroContent.Utility -> all.firstOrNull()?.let { _hero.value = HeroContent.Media(it) }
            }
        }
    }

    fun setHero(content: HeroContent) {
        _hero.value = content
    }

    // --- Sign-in flow ----------------------------------------------------------------------------

    private val _signIn = MutableStateFlow<SignInStep>(SignInStep.EnterServer())
    val signIn: StateFlow<SignInStep> = _signIn.asStateFlow()
    private var signInJob: Job? = null

    /** Starts at the server step, or at the user step for [server] when adding a user. */
    fun startSignIn(server: JellyfinServer? = null) {
        signInJob?.cancel()
        if (server == null) {
            _signIn.value = SignInStep.EnterServer(url = activeServer.value?.url.orEmpty())
        } else {
            _signIn.value = SignInStep.EnterServer(url = server.url, busy = true)
            connect(server.url)
        }
    }

    fun connect(input: String) {
        val url = JellyfinClient.normalizeUrl(input)
        if (input.isBlank()) return
        signInJob?.cancel()
        _signIn.value = SignInStep.EnterServer(url = input, busy = true)
        signInJob = viewModelScope.launch {
            try {
                val info = client.publicInfo(url)
                val server = JellyfinServer(id = info.id, name = info.serverName ?: url, url = url)
                val users = client.publicUsers(url)
                val qc = client.quickConnectEnabled(url)
                _signIn.value = SignInStep.ChooseUser(server, info.version, users, qc)
            } catch (e: Exception) {
                _signIn.value = SignInStep.EnterServer(url = input, error = e.message ?: "Couldn't connect.")
            }
        }
    }

    fun choosePublicUser(user: UserDto) {
        val step = _signIn.value as? SignInStep.ChooseUser ?: return
        if (!user.hasPassword) {
            _signIn.value = SignInStep.Password(step.server, user.name, fixedUser = true, busy = true)
            signInWithPassword(user.name, "")
        } else {
            _signIn.value = SignInStep.Password(step.server, user.name, fixedUser = true)
        }
    }

    fun chooseManualLogin() {
        val step = _signIn.value as? SignInStep.ChooseUser ?: return
        _signIn.value = SignInStep.Password(step.server, "", fixedUser = false)
    }

    fun signInWithPassword(username: String, password: String) {
        val step = _signIn.value as? SignInStep.Password ?: return
        if (username.isBlank()) {
            _signIn.value = step.copy(error = "Enter a username.")
            return
        }
        signInJob?.cancel()
        _signIn.value = step.copy(username = username, busy = true, error = null)
        signInJob = viewModelScope.launch {
            try {
                completeSignIn(step.server, client.authenticateByName(step.server.url, username, password))
            } catch (e: Exception) {
                _signIn.value = step.copy(username = username, busy = false, error = e.message)
            }
        }
    }

    fun startQuickConnect() {
        val server = when (val step = _signIn.value) {
            is SignInStep.ChooseUser -> step.server
            is SignInStep.QuickConnect -> step.server
            else -> return
        }
        signInJob?.cancel()
        _signIn.value = SignInStep.QuickConnect(server)
        signInJob = viewModelScope.launch {
            try {
                val started = client.quickConnectInitiate(server.url)
                _signIn.value = SignInStep.QuickConnect(server, code = started.code)
                val deadline = System.currentTimeMillis() + QUICK_CONNECT_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(QUICK_CONNECT_POLL_MS)
                    if (client.quickConnectState(server.url, started.secret).authenticated) {
                        completeSignIn(server, client.authenticateWithQuickConnect(server.url, started.secret))
                        return@launch
                    }
                }
                _signIn.value = SignInStep.QuickConnect(server, error = "The code expired. Try again.")
            } catch (e: Exception) {
                _signIn.value = SignInStep.QuickConnect(server, error = e.message)
            }
        }
    }

    /** Back within the flow. Returns false when already at the first step (caller closes it). */
    fun signInBack(): Boolean {
        signInJob?.cancel()
        return when (val step = _signIn.value) {
            is SignInStep.Password -> backToUsers(step.server)
            is SignInStep.QuickConnect -> backToUsers(step.server)
            is SignInStep.ChooseUser -> {
                _signIn.value = SignInStep.EnterServer(url = step.server.url)
                true
            }
            else -> false
        }
    }

    private fun backToUsers(server: JellyfinServer): Boolean {
        connect(server.url)
        return true
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        _signIn.value = SignInStep.EnterServer()
    }

    private fun completeSignIn(server: JellyfinServer, auth: AuthResult) {
        settings.saveServer(server.copy(id = auth.serverId))
        settings.saveAccount(
            JellyfinAccount(
                serverId = auth.serverId,
                userId = auth.user.id,
                name = auth.user.name,
                token = auth.accessToken,
                imageTag = auth.user.primaryImageTag,
            ),
        )
        // Signing in counts as choosing a user.
        _userPickRequired.value = false
        refreshHome()
        _signIn.value = SignInStep.Done
    }

    // --- Misc ------------------------------------------------------------------------------------

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = requireUserPickIfNeeded()
    }

    init {
        refreshApps()
        packageReceiver = appRepository.registerPackageReceiver { refreshApps() }
        // With "Remember user" off, ask who's watching again after the TV goes to sleep.
        ContextCompat.registerReceiver(
            application,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Reload the home rows whenever the account (or its rewatching option) changes.
        viewModelScope.launch {
            combine(activeAccount, activeServer) { account, server ->
                Triple(account?.key, account?.rewatching, server?.url)
            }.distinctUntilChanged().collect { refreshHome() }
        }
    }

    fun refreshApps() {
        viewModelScope.launch { appRepository.refresh() }
    }

    fun setPreset(preset: AccentPreset) = settings.setPreset(preset)
    fun setAppHidden(packageName: String, hidden: Boolean) = settings.setAppHidden(packageName, hidden)

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
        runCatching { getApplication<Application>().unregisterReceiver(screenOffReceiver) }
    }

    private companion object {
        const val QUICK_CONNECT_POLL_MS = 3_000L
        const val QUICK_CONNECT_TIMEOUT_MS = 5 * 60_000L

        val NOT_SIGNED_IN_HERO = HeroContent.Utility(
            category = "Jellyfin",
            title = "Welcome",
            description = "Connect a Jellyfin server to see Continue Watching and recently added shows and movies.",
        )
    }
}
