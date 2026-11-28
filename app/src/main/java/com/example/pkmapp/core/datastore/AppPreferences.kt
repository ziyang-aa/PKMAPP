package com.example.pkmapp.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.pkmapp.BuildConfig
import com.example.pkmapp.core.database.PkmStorageBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class AppPreferencesSnapshot(
    val nickname: String = DEFAULT_NICKNAME,
    val checkedDates: Set<String> = emptySet(),
    val rememberLastCottage: Boolean = false,
    val lastCottage: String = "",
    val countdownMode: Boolean = false,
    val keepScreenOn: Boolean = false,
    val askedNotifications: Boolean = false,
    val todoRemindEnabled: Boolean = true,
    val todoDefaultRemindTime: String = "18:00",
    val activeOwnerId: String = DEFAULT_OWNER_ID,
    val encryptedAccessToken: String = "",
    val encryptedRefreshToken: String = "",
    val serverBaseUrl: String = ""
) {
    companion object {
        const val DEFAULT_NICKNAME = "森林守护员"
        const val DEFAULT_OWNER_ID = "local_guest"
    }
}

class AppPreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val defaultServerBaseUrl: String = ""
) {
    @Volatile
    private var cached: AppPreferencesSnapshot = runBlocking(Dispatchers.IO) {
        mapPreferences(dataStore.data.first(), defaultServerBaseUrl)
    }

    val snapshotFlow: Flow<AppPreferencesSnapshot> = dataStore.data.map { prefs ->
        mapPreferences(prefs, defaultServerBaseUrl).also { cached = it }
    }

    val current: AppPreferencesSnapshot
        get() = cached

    fun setNickname(nickname: String) {
        cached = cached.copy(nickname = nickname)
        ioScope.launch {
            dataStore.edit { it[KEY_NICKNAME] = nickname }
        }
    }

    fun setCheckedDates(dates: Set<String>) {
        val copy = HashSet(dates)
        cached = cached.copy(checkedDates = copy)
        ioScope.launch {
            dataStore.edit { it[KEY_CHECKED_DATES] = copy }
        }
    }

    fun clearProfileIdentity() {
        cached = cached.copy(
            nickname = AppPreferencesSnapshot.DEFAULT_NICKNAME,
            checkedDates = emptySet()
        )
        ioScope.launch {
            dataStore.edit {
                it.remove(KEY_NICKNAME)
                it.remove(KEY_CHECKED_DATES)
            }
        }
    }

    fun setRememberLastCottage(enabled: Boolean) {
        cached = cached.copy(rememberLastCottage = enabled)
        ioScope.launch {
            dataStore.edit { it[KEY_REMEMBER_LAST_COTTAGE] = enabled }
        }
    }

    fun setLastCottage(cottage: String) {
        cached = cached.copy(lastCottage = cottage)
        ioScope.launch {
            dataStore.edit { it[KEY_LAST_COTTAGE] = cottage }
        }
    }

    fun setCountdownMode(enabled: Boolean) {
        cached = cached.copy(countdownMode = enabled)
        ioScope.launch {
            dataStore.edit { it[KEY_COUNTDOWN_MODE] = enabled }
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        cached = cached.copy(keepScreenOn = enabled)
        ioScope.launch {
            dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
        }
    }

    fun setAskedNotifications(asked: Boolean) {
        cached = cached.copy(askedNotifications = asked)
        ioScope.launch {
            dataStore.edit { it[KEY_ASKED_NOTIFICATIONS] = asked }
        }
    }

    fun setTodoReminderConfig(enabled: Boolean, defaultTime: String) {
        cached = cached.copy(
            todoRemindEnabled = enabled,
            todoDefaultRemindTime = defaultTime
        )
        ioScope.launch {
            dataStore.edit {
                it[KEY_TODO_REMIND_ENABLED] = enabled
                it[KEY_TODO_DEFAULT_REMIND_TIME] = defaultTime
            }
        }
    }

    fun setSessionState(
        ownerId: String,
        encryptedAccessToken: String,
        encryptedRefreshToken: String
    ) {
        val safeOwner = ownerId.trim().ifEmpty { AppPreferencesSnapshot.DEFAULT_OWNER_ID }
        cached = cached.copy(
            activeOwnerId = safeOwner,
            encryptedAccessToken = encryptedAccessToken,
            encryptedRefreshToken = encryptedRefreshToken
        )
        runBlocking(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[KEY_ACTIVE_OWNER_ID] = safeOwner
                if (encryptedAccessToken.isEmpty()) {
                    prefs.remove(KEY_ENCRYPTED_ACCESS_TOKEN)
                } else {
                    prefs[KEY_ENCRYPTED_ACCESS_TOKEN] = encryptedAccessToken
                }
                if (encryptedRefreshToken.isEmpty()) {
                    prefs.remove(KEY_ENCRYPTED_REFRESH_TOKEN)
                } else {
                    prefs[KEY_ENCRYPTED_REFRESH_TOKEN] = encryptedRefreshToken
                }
            }
        }
    }

    fun setServerBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        cached = cached.copy(serverBaseUrl = clean)
        runBlocking(Dispatchers.IO) {
            dataStore.edit { prefs ->
                // Explicitly store empty string so a user clearing the URL stays offline
                // rather than falling back to defaultServerBaseUrl.
                prefs[KEY_SERVER_BASE_URL] = clean
            }
        }
    }

    fun writeMigratedBlocking(migrated: AppPreferencesSnapshot) {
        runBlocking(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[KEY_NICKNAME] = migrated.nickname
                prefs[KEY_CHECKED_DATES] = HashSet(migrated.checkedDates)
                prefs[KEY_REMEMBER_LAST_COTTAGE] = migrated.rememberLastCottage
                prefs[KEY_LAST_COTTAGE] = migrated.lastCottage
                prefs[KEY_COUNTDOWN_MODE] = migrated.countdownMode
                prefs[KEY_KEEP_SCREEN_ON] = migrated.keepScreenOn
                prefs[KEY_ASKED_NOTIFICATIONS] = migrated.askedNotifications
                prefs[KEY_TODO_REMIND_ENABLED] = migrated.todoRemindEnabled
                prefs[KEY_TODO_DEFAULT_REMIND_TIME] = migrated.todoDefaultRemindTime
                prefs[KEY_ACTIVE_OWNER_ID] = migrated.activeOwnerId
                if (migrated.encryptedAccessToken.isEmpty()) {
                    prefs.remove(KEY_ENCRYPTED_ACCESS_TOKEN)
                } else {
                    prefs[KEY_ENCRYPTED_ACCESS_TOKEN] = migrated.encryptedAccessToken
                }
                if (migrated.encryptedRefreshToken.isEmpty()) {
                    prefs.remove(KEY_ENCRYPTED_REFRESH_TOKEN)
                } else {
                    prefs[KEY_ENCRYPTED_REFRESH_TOKEN] = migrated.encryptedRefreshToken
                }
                if (migrated.serverBaseUrl.isNotEmpty()) {
                    prefs[KEY_SERVER_BASE_URL] = migrated.serverBaseUrl
                }
            }
            cached = mapPreferences(dataStore.data.first(), defaultServerBaseUrl)
        }
    }

    companion object {
        const val DATASTORE_FILE_NAME = "pkm_preferences"

        private val KEY_NICKNAME = stringPreferencesKey("nickname")
        private val KEY_CHECKED_DATES = stringSetPreferencesKey("checked_dates")
        private val KEY_REMEMBER_LAST_COTTAGE = booleanPreferencesKey("remember_last_cottage")
        private val KEY_LAST_COTTAGE = stringPreferencesKey("last_cottage")
        private val KEY_COUNTDOWN_MODE = booleanPreferencesKey("countdown_mode")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val KEY_ASKED_NOTIFICATIONS = booleanPreferencesKey("asked_notifications")
        private val KEY_TODO_REMIND_ENABLED = booleanPreferencesKey("todo_remind_enabled")
        private val KEY_TODO_DEFAULT_REMIND_TIME = stringPreferencesKey("todo_default_remind_time")
        private val KEY_ACTIVE_OWNER_ID = stringPreferencesKey("active_owner_id")
        private val KEY_ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("encrypted_access_token")
        private val KEY_ENCRYPTED_REFRESH_TOKEN = stringPreferencesKey("encrypted_refresh_token")
        private val KEY_SERVER_BASE_URL = stringPreferencesKey("server_base_url")

        @Volatile
        private var instance: AppPreferences? = null

        @JvmStatic
        fun getRawInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val store = PreferenceDataStoreFactory.create(
                        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                        produceFile = { appContext.preferencesDataStoreFile(DATASTORE_FILE_NAME) }
                    )
                    AppPreferences(
                        dataStore = store,
                        defaultServerBaseUrl = BuildConfig.DEFAULT_SERVER_BASE_URL
                    ).also { instance = it }
                }
            }
        }

        @JvmStatic
        fun getInstance(context: Context): AppPreferences {
            PkmStorageBootstrap.ensureInitialized(context)
            return getRawInstance(context)
        }

        internal fun mapPreferences(
            prefs: Preferences,
            defaultServerBaseUrl: String = ""
        ): AppPreferencesSnapshot =
            AppPreferencesSnapshot(
                nickname = prefs[KEY_NICKNAME] ?: AppPreferencesSnapshot.DEFAULT_NICKNAME,
                checkedDates = HashSet(prefs[KEY_CHECKED_DATES] ?: emptySet()),
                rememberLastCottage = prefs[KEY_REMEMBER_LAST_COTTAGE] ?: false,
                lastCottage = prefs[KEY_LAST_COTTAGE] ?: "",
                countdownMode = prefs[KEY_COUNTDOWN_MODE] ?: false,
                keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
                askedNotifications = prefs[KEY_ASKED_NOTIFICATIONS] ?: false,
                todoRemindEnabled = prefs[KEY_TODO_REMIND_ENABLED] ?: true,
                todoDefaultRemindTime = prefs[KEY_TODO_DEFAULT_REMIND_TIME] ?: "18:00",
                activeOwnerId = prefs[KEY_ACTIVE_OWNER_ID]
                    ?.takeIf { it.isNotBlank() }
                    ?: AppPreferencesSnapshot.DEFAULT_OWNER_ID,
                encryptedAccessToken = prefs[KEY_ENCRYPTED_ACCESS_TOKEN] ?: "",
                encryptedRefreshToken = prefs[KEY_ENCRYPTED_REFRESH_TOKEN] ?: "",
                serverBaseUrl = prefs[KEY_SERVER_BASE_URL] ?: defaultServerBaseUrl
            )
    }
}
