package com.vexorter.onyx.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vexorter.onyx.domain.NotificationSettings
import com.vexorter.onyx.domain.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Акцент интерфейса. Меняется в скрытом режиме «Веселье». */
enum class AccentColor { MINT, AMBER, VIOLET, CORAL }

/** Профиль пользователя и настройки оформления. Выбирается один раз и живёт между запусками. */
class UserPrefs(private val context: Context) {

    private object Keys {
        val BRANCH_GUID = stringPreferencesKey("branch_guid")
        val BRANCH_NAME = stringPreferencesKey("branch_name")
        val YEAR_GUID = stringPreferencesKey("year_guid")
        val YEAR_NAME = stringPreferencesKey("year_name")
        val GROUP_GUID = stringPreferencesKey("group_guid")
        val GROUP_NAME = stringPreferencesKey("group_name")
        val THEME = stringPreferencesKey("theme_mode")

        val NOTIFY_MORNING = booleanPreferencesKey("notify_morning")
        val NOTIFY_MORNING_AT = intPreferencesKey("notify_morning_at")
        val NOTIFY_BEFORE = booleanPreferencesKey("notify_before_lesson")
        val NOTIFY_BEFORE_MIN = intPreferencesKey("notify_before_minutes")
        val NOTIFY_CHANGES = booleanPreferencesKey("notify_changes")
        val NOTIFY_EVENING = booleanPreferencesKey("notify_evening")
        val NOTIFY_EVENING_AT = intPreferencesKey("notify_evening_at")

        val FUN_UNLOCKED = booleanPreferencesKey("fun_unlocked")
        val ACCENT = stringPreferencesKey("accent_color")
    }

    val profile: Flow<Profile> = context.dataStore.data.map { prefs ->
        Profile(
            branchGuid = prefs[Keys.BRANCH_GUID].orEmpty(),
            branchName = prefs[Keys.BRANCH_NAME].orEmpty(),
            yearGuid = prefs[Keys.YEAR_GUID].orEmpty(),
            yearName = prefs[Keys.YEAR_NAME].orEmpty(),
            groupGuid = prefs[Keys.GROUP_GUID].orEmpty(),
            groupName = prefs[Keys.GROUP_NAME].orEmpty(),
        )
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
    }

    suspend fun setBranch(guid: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BRANCH_GUID] = guid
            prefs[Keys.BRANCH_NAME] = name
            // филиал сменился — год и группа больше не действительны
            prefs.remove(Keys.YEAR_GUID)
            prefs.remove(Keys.YEAR_NAME)
            prefs.remove(Keys.GROUP_GUID)
            prefs.remove(Keys.GROUP_NAME)
        }
    }

    suspend fun setYear(guid: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.YEAR_GUID] = guid
            prefs[Keys.YEAR_NAME] = name
            prefs.remove(Keys.GROUP_GUID)
            prefs.remove(Keys.GROUP_NAME)
        }
    }

    suspend fun setGroup(guid: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GROUP_GUID] = guid
            prefs[Keys.GROUP_NAME] = name
        }
    }

    val notifications: Flow<NotificationSettings> = context.dataStore.data.map { prefs ->
        val defaults = NotificationSettings()
        NotificationSettings(
            morningSummary = prefs[Keys.NOTIFY_MORNING] ?: defaults.morningSummary,
            morningAtMinutes = prefs[Keys.NOTIFY_MORNING_AT] ?: defaults.morningAtMinutes,
            beforeLesson = prefs[Keys.NOTIFY_BEFORE] ?: defaults.beforeLesson,
            beforeLessonMinutes = prefs[Keys.NOTIFY_BEFORE_MIN] ?: defaults.beforeLessonMinutes,
            scheduleChanges = prefs[Keys.NOTIFY_CHANGES] ?: defaults.scheduleChanges,
            eveningPreview = prefs[Keys.NOTIFY_EVENING] ?: defaults.eveningPreview,
            eveningAtMinutes = prefs[Keys.NOTIFY_EVENING_AT] ?: defaults.eveningAtMinutes,
        )
    }

    suspend fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) {
        context.dataStore.edit { prefs ->
            val current = NotificationSettings(
                morningSummary = prefs[Keys.NOTIFY_MORNING] ?: true,
                morningAtMinutes = prefs[Keys.NOTIFY_MORNING_AT] ?: (7 * 60 + 30),
                beforeLesson = prefs[Keys.NOTIFY_BEFORE] ?: true,
                beforeLessonMinutes = prefs[Keys.NOTIFY_BEFORE_MIN] ?: 15,
                scheduleChanges = prefs[Keys.NOTIFY_CHANGES] ?: true,
                eveningPreview = prefs[Keys.NOTIFY_EVENING] ?: true,
                eveningAtMinutes = prefs[Keys.NOTIFY_EVENING_AT] ?: (20 * 60),
            )
            val updated = transform(current)
            prefs[Keys.NOTIFY_MORNING] = updated.morningSummary
            prefs[Keys.NOTIFY_MORNING_AT] = updated.morningAtMinutes
            prefs[Keys.NOTIFY_BEFORE] = updated.beforeLesson
            prefs[Keys.NOTIFY_BEFORE_MIN] = updated.beforeLessonMinutes
            prefs[Keys.NOTIFY_CHANGES] = updated.scheduleChanges
            prefs[Keys.NOTIFY_EVENING] = updated.eveningPreview
            prefs[Keys.NOTIFY_EVENING_AT] = updated.eveningAtMinutes
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME] = mode.name }
    }

    val funUnlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FUN_UNLOCKED] ?: false
    }

    suspend fun unlockFun() {
        context.dataStore.edit { prefs -> prefs[Keys.FUN_UNLOCKED] = true }
    }

    val accent: Flow<AccentColor> = context.dataStore.data.map { prefs ->
        runCatching { AccentColor.valueOf(prefs[Keys.ACCENT] ?: AccentColor.MINT.name) }
            .getOrDefault(AccentColor.MINT)
    }

    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit { prefs -> prefs[Keys.ACCENT] = accent.name }
    }

    suspend fun clearProfile() {
        context.dataStore.edit { prefs ->
            listOf(
                Keys.BRANCH_GUID, Keys.BRANCH_NAME,
                Keys.YEAR_GUID, Keys.YEAR_NAME,
                Keys.GROUP_GUID, Keys.GROUP_NAME,
            ).forEach(prefs::remove)
        }
    }
}
