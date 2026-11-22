package com.jamescolb.launcher.data

import android.content.Context
import androidx.core.content.edit
import com.jamescolb.launcher.ui.theme.AccentPreset
import com.jamescolb.launcher.ui.theme.AccentPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A Jellyfin user. Placeholder only — no tokens or server binding yet. */
data class JellyfinUser(val id: String, val name: String)

/**
 * Small SharedPreferences-backed store for launcher settings. The Jellyfin fields are stored but
 * nothing connects to a server yet.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    private val _preset = MutableStateFlow(AccentPresets.byId(prefs.getString(KEY_PRESET, null)))
    val preset: StateFlow<AccentPreset> = _preset.asStateFlow()

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER, "") ?: "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _users = MutableStateFlow(loadUsers())
    val users: StateFlow<List<JellyfinUser>> = _users.asStateFlow()

    private val _activeUserId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_USER, null) ?: _users.value.first().id,
    )
    val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

    /** Packages left out of the Apps row and All Apps. System settings are hidden by default. */
    private val _hiddenApps = MutableStateFlow(
        prefs.getStringSet(KEY_HIDDEN_APPS, null)?.toSet() ?: DEFAULT_HIDDEN_APPS,
    )
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    fun setAppHidden(packageName: String, hidden: Boolean) {
        val updated = if (hidden) _hiddenApps.value + packageName else _hiddenApps.value - packageName
        _hiddenApps.value = updated
        prefs.edit { putStringSet(KEY_HIDDEN_APPS, updated) }
    }

    fun setPreset(preset: AccentPreset) {
        _preset.value = preset
        prefs.edit { putString(KEY_PRESET, preset.id) }
    }

    fun setServerUrl(url: String) {
        _serverUrl.value = url.trim()
        prefs.edit { putString(KEY_SERVER, url.trim()) }
    }

    fun setActiveUser(id: String) {
        _activeUserId.value = id
        prefs.edit { putString(KEY_ACTIVE_USER, id) }
    }

    /** Placeholder for a Quick Connect sign-in: just appends a user locally. */
    fun addUser(name: String): JellyfinUser {
        val user = JellyfinUser(id = "u${System.currentTimeMillis()}", name = name)
        _users.value = _users.value + user
        saveUsers()
        return user
    }

    private fun loadUsers(): List<JellyfinUser> {
        val raw = prefs.getString(KEY_USERS, null) ?: return DEFAULT_USERS
        val parsed = raw.split('\n').mapNotNull { line ->
            val idx = line.indexOf('|')
            if (idx <= 0) null else JellyfinUser(line.substring(0, idx), line.substring(idx + 1))
        }
        return parsed.ifEmpty { DEFAULT_USERS }
    }

    private fun saveUsers() {
        prefs.edit { putString(KEY_USERS, _users.value.joinToString("\n") { "${it.id}|${it.name}" }) }
    }

    private companion object {
        const val KEY_PRESET = "accent_preset"
        const val KEY_SERVER = "server_url"
        const val KEY_USERS = "users"
        const val KEY_ACTIVE_USER = "active_user"
        const val KEY_HIDDEN_APPS = "hidden_apps"

        /** Android TV / Google TV / phone system settings; reachable via "More Settings" instead. */
        val DEFAULT_HIDDEN_APPS = setOf(
            "com.android.tv.settings",
            "com.google.android.tv.settings",
            "com.android.settings",
        )

        val DEFAULT_USERS = listOf(
            JellyfinUser("james", "James"),
            JellyfinUser("guest", "Guest"),
        )
    }
}
