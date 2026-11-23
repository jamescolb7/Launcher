package com.jamescolb.launcher.data

import android.content.Context
import androidx.core.content.edit
import com.jamescolb.launcher.ui.theme.AccentPreset
import com.jamescolb.launcher.ui.theme.AccentPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class JellyfinServer(val id: String, val name: String, val url: String)

/** A signed-in Jellyfin user on one server. */
@Serializable
data class JellyfinAccount(
    val serverId: String,
    val userId: String,
    val name: String,
    val token: String,
    val imageTag: String? = null,
    /** Jellyfin's "rewatching in Next Up" option, kept per user. */
    val rewatching: Boolean = false,
) {
    val key: String get() = "$serverId:$userId"
}

/**
 * SharedPreferences-backed launcher settings and Jellyfin accounts.
 *
 * Note: access tokens are stored in plain app-private preferences for now.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Stable per-install id sent to Jellyfin so sessions show up as one device. */
    val deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString(KEY_DEVICE_ID, it) }
    }

    private val _preset = MutableStateFlow(AccentPresets.byId(prefs.getString(KEY_PRESET, null)))
    val preset: StateFlow<AccentPreset> = _preset.asStateFlow()

    private val _servers = MutableStateFlow(load<List<JellyfinServer>>(KEY_SERVERS) ?: emptyList())
    val servers: StateFlow<List<JellyfinServer>> = _servers.asStateFlow()

    private val _accounts = MutableStateFlow(load<List<JellyfinAccount>>(KEY_ACCOUNTS) ?: emptyList())
    val accounts: StateFlow<List<JellyfinAccount>> = _accounts.asStateFlow()

    private val _activeAccountKey = MutableStateFlow(prefs.getString(KEY_ACTIVE_ACCOUNT, null))
    val activeAccountKey: StateFlow<String?> = _activeAccountKey.asStateFlow()

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

    /** User-arranged order of apps (package names). Apps not listed follow, alphabetically. */
    private val _appOrder = MutableStateFlow(
        prefs.getString(KEY_APP_ORDER, null)?.split(ORDER_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList(),
    )
    val appOrder: StateFlow<List<String>> = _appOrder.asStateFlow()

    fun setAppOrder(order: List<String>) {
        _appOrder.value = order
        prefs.edit { putString(KEY_APP_ORDER, order.joinToString(ORDER_SEPARATOR)) }
    }

    fun setPreset(preset: AccentPreset) {
        _preset.value = preset
        prefs.edit { putString(KEY_PRESET, preset.id) }
    }

    /** Adds or updates a server (matched by id; a changed address replaces the old one). */
    fun saveServer(server: JellyfinServer) {
        _servers.value = _servers.value.filterNot { it.id == server.id } + server
        save(KEY_SERVERS, _servers.value)
    }

    /** Adds or replaces an account and makes it the active one. */
    fun saveAccount(account: JellyfinAccount) {
        val existing = _accounts.value.firstOrNull { it.key == account.key }
        val merged = account.copy(rewatching = existing?.rewatching ?: account.rewatching)
        _accounts.value = _accounts.value.filterNot { it.key == account.key } + merged
        save(KEY_ACCOUNTS, _accounts.value)
        setActiveAccount(merged.key)
    }

    fun setActiveAccount(key: String?) {
        _activeAccountKey.value = key
        prefs.edit { putString(KEY_ACTIVE_ACCOUNT, key) }
    }

    /** Show the Apps row above the Jellyfin rows on the home screen. */
    private val _appsFirst = MutableStateFlow(prefs.getBoolean(KEY_APPS_FIRST, false))
    val appsFirst: StateFlow<Boolean> = _appsFirst.asStateFlow()

    fun setAppsFirst(first: Boolean) {
        _appsFirst.value = first
        prefs.edit { putBoolean(KEY_APPS_FIRST, first) }
    }

    /** When false, the user picker is shown at start-up / wake instead of reusing the last user. */
    private val _rememberUser = MutableStateFlow(prefs.getBoolean(KEY_REMEMBER_USER, true))
    val rememberUser: StateFlow<Boolean> = _rememberUser.asStateFlow()

    fun setRememberUser(remember: Boolean) {
        _rememberUser.value = remember
        prefs.edit { putBoolean(KEY_REMEMBER_USER, remember) }
    }

    fun setRewatching(key: String, enabled: Boolean) {
        _accounts.value = _accounts.value.map { if (it.key == key) it.copy(rewatching = enabled) else it }
        save(KEY_ACCOUNTS, _accounts.value)
    }

    /** Removes an account; if it was active, switches to another on the same server if any. */
    fun removeAccount(key: String) {
        val removed = _accounts.value.firstOrNull { it.key == key } ?: return
        _accounts.value = _accounts.value.filterNot { it.key == key }
        save(KEY_ACCOUNTS, _accounts.value)
        if (_activeAccountKey.value == key) {
            val next = _accounts.value.firstOrNull { it.serverId == removed.serverId } ?: _accounts.value.firstOrNull()
            setActiveAccount(next?.key)
        }
    }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> save(key: String, value: T) {
        prefs.edit { putString(key, json.encodeToString(value)) }
    }

    private companion object {
        const val KEY_PRESET = "accent_preset"
        const val KEY_SERVERS = "jellyfin_servers"
        const val KEY_ACCOUNTS = "jellyfin_accounts"
        const val KEY_ACTIVE_ACCOUNT = "jellyfin_active_account"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_HIDDEN_APPS = "hidden_apps"
        const val KEY_APP_ORDER = "app_order"
        const val KEY_REMEMBER_USER = "remember_user"
        const val KEY_APPS_FIRST = "apps_first"
        const val ORDER_SEPARATOR = ","

        /** Android TV / Google TV / phone system settings; reachable via "More Settings" instead. */
        val DEFAULT_HIDDEN_APPS = setOf(
            "com.android.tv.settings",
            "com.google.android.tv.settings",
            "com.android.settings",
            "com.amazon.tv.settings.v2", // Fire TV Settings
        )
    }
}
