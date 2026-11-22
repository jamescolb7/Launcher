package com.jamescolb.launcher

import android.app.Application
import android.content.BroadcastReceiver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamescolb.launcher.data.AppRepository
import com.jamescolb.launcher.data.InstalledApp
import com.jamescolb.launcher.data.JellyfinUser
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.data.PlaceholderMedia
import com.jamescolb.launcher.data.SettingsRepository
import com.jamescolb.launcher.ui.theme.AccentPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the hero area at the top of the home screen is describing. */
sealed interface HeroContent {
    data class Media(val item: MediaItem) : HeroContent
    data class App(val app: InstalledApp) : HeroContent
    data class Utility(val category: String, val title: String, val description: String) : HeroContent
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val settings = SettingsRepository(application)
    private val appRepository = AppRepository(application)
    private val packageReceiver: BroadcastReceiver

    /** Every launchable app, including hidden ones (for the Hidden apps settings page). */
    val allApps: StateFlow<List<InstalledApp>> = appRepository.apps

    /** Apps shown on the home row and in All Apps. */
    val apps: StateFlow<List<InstalledApp>> =
        combine(appRepository.apps, settings.hiddenApps) { apps, hidden ->
            apps.filterNot { it.packageName in hidden }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<Set<String>> = settings.hiddenApps
    val continueWatching: List<MediaItem> = PlaceholderMedia.continueWatching

    val preset: StateFlow<AccentPreset> = settings.preset
    val serverUrl: StateFlow<String> = settings.serverUrl
    val users: StateFlow<List<JellyfinUser>> = settings.users

    val activeUser: StateFlow<JellyfinUser?> =
        combine(settings.users, settings.activeUserId) { users, id ->
            users.firstOrNull { it.id == id } ?: users.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _hero = MutableStateFlow<HeroContent>(HeroContent.Media(continueWatching.first()))
    val hero: StateFlow<HeroContent> = _hero.asStateFlow()

    init {
        refreshApps()
        packageReceiver = appRepository.registerPackageReceiver { refreshApps() }
    }

    fun refreshApps() {
        viewModelScope.launch { appRepository.refresh() }
    }

    fun setHero(content: HeroContent) {
        _hero.value = content
    }

    fun setPreset(preset: AccentPreset) = settings.setPreset(preset)
    fun setServerUrl(url: String) = settings.setServerUrl(url)
    fun setActiveUser(id: String) = settings.setActiveUser(id)
    fun addUser(name: String) = settings.addUser(name)
    fun setAppHidden(packageName: String, hidden: Boolean) = settings.setAppHidden(packageName, hidden)

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
    }
}
