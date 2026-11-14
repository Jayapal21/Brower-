package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "obscura_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val STEALTH_ENABLED = booleanPreferencesKey("stealth_enabled")
        val ALLOW_PRIVATE_NETWORK = booleanPreferencesKey("allow_private_network")
        val OBEY_ROBOTS = booleanPreferencesKey("obey_robots")
        val SELECTED_PROFILE_INDEX = intPreferencesKey("selected_profile_index")
        val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")
        val PROXY_URL = stringPreferencesKey("proxy_url")
        val TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        val WAIT_UNTIL = stringPreferencesKey("wait_until")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val BRIDGE_PORT = intPreferencesKey("bridge_port")
    }

    val settingsFlow: Flow<ObscuraSettings> = context.dataStore.data.map { prefs ->
        val waitUntilName = prefs[Keys.WAIT_UNTIL] ?: WaitUntilMode.LOAD.name
        val waitUntil = runCatching { WaitUntilMode.valueOf(waitUntilName) }
            .getOrDefault(WaitUntilMode.LOAD)
        ObscuraSettings(
            stealthEnabled = prefs[Keys.STEALTH_ENABLED] ?: true,
            allowPrivateNetwork = prefs[Keys.ALLOW_PRIVATE_NETWORK] ?: false,
            obeyRobots = prefs[Keys.OBEY_ROBOTS] ?: false,
            selectedProfileIndex = prefs[Keys.SELECTED_PROFILE_INDEX] ?: 0,
            customUserAgent = prefs[Keys.CUSTOM_USER_AGENT] ?: "",
            proxyUrl = prefs[Keys.PROXY_URL] ?: "",
            timeoutSeconds = prefs[Keys.TIMEOUT_SECONDS] ?: 30,
            waitUntil = waitUntil,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            bridgePort = prefs[Keys.BRIDGE_PORT] ?: 9222,
        )
    }

    suspend fun updateSettings(transform: (ObscuraSettings) -> ObscuraSettings) {
        context.dataStore.edit { prefs ->
            val current = ObscuraSettings(
                stealthEnabled = prefs[Keys.STEALTH_ENABLED] ?: true,
                allowPrivateNetwork = prefs[Keys.ALLOW_PRIVATE_NETWORK] ?: false,
                obeyRobots = prefs[Keys.OBEY_ROBOTS] ?: false,
                selectedProfileIndex = prefs[Keys.SELECTED_PROFILE_INDEX] ?: 0,
                customUserAgent = prefs[Keys.CUSTOM_USER_AGENT] ?: "",
                proxyUrl = prefs[Keys.PROXY_URL] ?: "",
                timeoutSeconds = prefs[Keys.TIMEOUT_SECONDS] ?: 30,
                waitUntil = runCatching {
                    WaitUntilMode.valueOf(prefs[Keys.WAIT_UNTIL] ?: WaitUntilMode.LOAD.name)
                }.getOrDefault(WaitUntilMode.LOAD),
                darkTheme = prefs[Keys.DARK_THEME] ?: true,
                bridgePort = prefs[Keys.BRIDGE_PORT] ?: 9222,
            )
            val next = transform(current)
            prefs[Keys.STEALTH_ENABLED] = next.stealthEnabled
            prefs[Keys.ALLOW_PRIVATE_NETWORK] = next.allowPrivateNetwork
            prefs[Keys.OBEY_ROBOTS] = next.obeyRobots
            prefs[Keys.SELECTED_PROFILE_INDEX] = next.selectedProfileIndex
            prefs[Keys.CUSTOM_USER_AGENT] = next.customUserAgent
            prefs[Keys.PROXY_URL] = next.proxyUrl
            prefs[Keys.TIMEOUT_SECONDS] = next.timeoutSeconds
            prefs[Keys.WAIT_UNTIL] = next.waitUntil.name
            prefs[Keys.DARK_THEME] = next.darkTheme
            prefs[Keys.BRIDGE_PORT] = next.bridgePort
        }
    }
}
