package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.motion.MotionModePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "obscura_settings")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val STEALTH_ENABLED = booleanPreferencesKey("stealth_enabled")
        val ALLOW_PRIVATE_NETWORK = booleanPreferencesKey("allow_private_network")
        val OBEY_ROBOTS = booleanPreferencesKey("obey_robots")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val SELECTED_PROFILE_INDEX = intPreferencesKey("selected_profile_index")
        val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")
        val PROXY_URL = stringPreferencesKey("proxy_url")
        val TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        val WAIT_UNTIL = stringPreferencesKey("wait_until")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val BRIDGE_PORT = intPreferencesKey("bridge_port")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val MOTION_MODE = stringPreferencesKey("motion_mode")
        val SPRING_STIFFNESS = floatPreferencesKey("spring_stiffness")
        val SPRING_DAMPING = floatPreferencesKey("spring_damping")
        val BOOKMARKS_JSON = stringPreferencesKey("bookmarks_json")
        val HISTORY_JSON = stringPreferencesKey("history_json")
        val SHORTCUTS_JSON = stringPreferencesKey("shortcuts_json")
        val DOWNLOADS_JSON = stringPreferencesKey("downloads_json")
    }

    companion object {
        val DEFAULT_SHORTCUTS = listOf(
            WebsiteShortcut("sc-1", "Wikipedia", "https://en.wikipedia.org", "W"),
            WebsiteShortcut("sc-2", "Hacker News", "https://news.ycombinator.com", "Y"),
            WebsiteShortcut("sc-3", "GitHub", "https://github.com", "GH"),
            WebsiteShortcut("sc-4", "MDN Docs", "https://developer.mozilla.org", "M"),
            WebsiteShortcut("sc-5", "Quotes", "https://quotes.toscrape.com", "Q"),
            WebsiteShortcut("sc-6", "Example", "https://example.com", "E"),
        )

        val DEFAULT_BOOKMARKS = listOf(
            BookmarkItem("bm-1", "Example Domain", "https://example.com"),
            BookmarkItem("bm-2", "Hacker News", "https://news.ycombinator.com"),
        )
    }

    val settingsFlow: Flow<ObscuraSettings> = context.dataStore.data.map { prefs ->
        val waitUntilName = prefs[Keys.WAIT_UNTIL] ?: WaitUntilMode.LOAD.name
        val waitUntil = runCatching { WaitUntilMode.valueOf(waitUntilName) }
            .getOrDefault(WaitUntilMode.LOAD)
        val searchName = prefs[Keys.SEARCH_ENGINE] ?: SearchEngineOption.DUCKDUCKGO.name
        val searchEngine = runCatching { SearchEngineOption.valueOf(searchName) }
            .getOrDefault(SearchEngineOption.DUCKDUCKGO)
        val motionModeName = prefs[Keys.MOTION_MODE] ?: MotionModePreference.AUTO.name
        val motionMode = runCatching { MotionModePreference.valueOf(motionModeName) }
            .getOrDefault(MotionModePreference.AUTO)

        ObscuraSettings(
            stealthEnabled = prefs[Keys.STEALTH_ENABLED] ?: true,
            allowPrivateNetwork = prefs[Keys.ALLOW_PRIVATE_NETWORK] ?: false,
            obeyRobots = prefs[Keys.OBEY_ROBOTS] ?: false,
            desktopMode = prefs[Keys.DESKTOP_MODE] ?: false,
            selectedProfileIndex = prefs[Keys.SELECTED_PROFILE_INDEX] ?: 0,
            customUserAgent = prefs[Keys.CUSTOM_USER_AGENT] ?: "",
            proxyUrl = prefs[Keys.PROXY_URL] ?: "",
            timeoutSeconds = prefs[Keys.TIMEOUT_SECONDS] ?: 30,
            waitUntil = waitUntil,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            bridgePort = prefs[Keys.BRIDGE_PORT] ?: 9222,
            searchEngine = searchEngine,
            motionMode = motionMode,
            springStiffness = (prefs[Keys.SPRING_STIFFNESS] ?: 1100f).coerceIn(200f, 3500f),
            springDamping = (prefs[Keys.SPRING_DAMPING] ?: 0.85f).coerceIn(0.45f, 1.0f),
        )
    }

    val bookmarksFlow: Flow<List<BookmarkItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.BOOKMARKS_JSON] ?: return@map DEFAULT_BOOKMARKS
        runCatching { json.decodeFromString<List<BookmarkItem>>(raw) }.getOrDefault(DEFAULT_BOOKMARKS)
    }

    val historyFlow: Flow<List<HistoryItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.HISTORY_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<HistoryItem>>(raw) }.getOrDefault(emptyList())
    }

    val shortcutsFlow: Flow<List<WebsiteShortcut>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SHORTCUTS_JSON] ?: return@map DEFAULT_SHORTCUTS
        runCatching { json.decodeFromString<List<WebsiteShortcut>>(raw) }.getOrDefault(DEFAULT_SHORTCUTS)
    }

    val downloadsFlow: Flow<List<DownloadItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.DOWNLOADS_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<DownloadItem>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun updateSettings(transform: (ObscuraSettings) -> ObscuraSettings) {
        context.dataStore.edit { prefs ->
            val current = ObscuraSettings(
                stealthEnabled = prefs[Keys.STEALTH_ENABLED] ?: true,
                allowPrivateNetwork = prefs[Keys.ALLOW_PRIVATE_NETWORK] ?: false,
                obeyRobots = prefs[Keys.OBEY_ROBOTS] ?: false,
                desktopMode = prefs[Keys.DESKTOP_MODE] ?: false,
                selectedProfileIndex = prefs[Keys.SELECTED_PROFILE_INDEX] ?: 0,
                customUserAgent = prefs[Keys.CUSTOM_USER_AGENT] ?: "",
                proxyUrl = prefs[Keys.PROXY_URL] ?: "",
                timeoutSeconds = prefs[Keys.TIMEOUT_SECONDS] ?: 30,
                waitUntil = runCatching {
                    WaitUntilMode.valueOf(prefs[Keys.WAIT_UNTIL] ?: WaitUntilMode.LOAD.name)
                }.getOrDefault(WaitUntilMode.LOAD),
                darkTheme = prefs[Keys.DARK_THEME] ?: true,
                bridgePort = prefs[Keys.BRIDGE_PORT] ?: 9222,
                searchEngine = runCatching {
                    SearchEngineOption.valueOf(prefs[Keys.SEARCH_ENGINE] ?: SearchEngineOption.DUCKDUCKGO.name)
                }.getOrDefault(SearchEngineOption.DUCKDUCKGO),
                motionMode = runCatching {
                    MotionModePreference.valueOf(prefs[Keys.MOTION_MODE] ?: MotionModePreference.AUTO.name)
                }.getOrDefault(MotionModePreference.AUTO),
                springStiffness = (prefs[Keys.SPRING_STIFFNESS] ?: 1100f).coerceIn(200f, 3500f),
                springDamping = (prefs[Keys.SPRING_DAMPING] ?: 0.85f).coerceIn(0.45f, 1.0f),
            )
            val next = transform(current)
            prefs[Keys.STEALTH_ENABLED] = next.stealthEnabled
            prefs[Keys.ALLOW_PRIVATE_NETWORK] = next.allowPrivateNetwork
            prefs[Keys.OBEY_ROBOTS] = next.obeyRobots
            prefs[Keys.DESKTOP_MODE] = next.desktopMode
            prefs[Keys.SELECTED_PROFILE_INDEX] = next.selectedProfileIndex
            prefs[Keys.CUSTOM_USER_AGENT] = next.customUserAgent
            prefs[Keys.PROXY_URL] = next.proxyUrl
            prefs[Keys.TIMEOUT_SECONDS] = next.timeoutSeconds
            prefs[Keys.WAIT_UNTIL] = next.waitUntil.name
            prefs[Keys.DARK_THEME] = next.darkTheme
            prefs[Keys.BRIDGE_PORT] = next.bridgePort
            prefs[Keys.SEARCH_ENGINE] = next.searchEngine.name
            prefs[Keys.MOTION_MODE] = next.motionMode.name
            prefs[Keys.SPRING_STIFFNESS] = next.springStiffness.coerceIn(200f, 3500f)
            prefs[Keys.SPRING_DAMPING] = next.springDamping.coerceIn(0.45f, 1.0f)
        }
    }

    suspend fun saveBookmarks(items: List<BookmarkItem>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BOOKMARKS_JSON] = json.encodeToString(items)
        }
    }

    suspend fun saveHistory(items: List<HistoryItem>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HISTORY_JSON] = json.encodeToString(items.take(150))
        }
    }

    suspend fun saveShortcuts(items: List<WebsiteShortcut>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHORTCUTS_JSON] = json.encodeToString(items)
        }
    }

    suspend fun saveDownloads(items: List<DownloadItem>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOADS_JSON] = json.encodeToString(items.take(100))
        }
    }
}
