package com.vexorter.onyx.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vexorter.onyx.domain.NotificationSettings
import com.vexorter.onyx.domain.Profile
import com.vexorter.onyx.domain.SetupDraft
import com.vexorter.onyx.domain.ScheduleKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Акцент интерфейса. Меняется в скрытом разделе SICRETO. */
enum class AccentColor { MINT, AMBER, VIOLET, CORAL }

/** Профиль пользователя и настройки оформления. Выбирается один раз и живёт между запусками. */
class UserPrefs(private val context: Context) {

    private object Keys {
        val BRANCH_GUID = stringPreferencesKey("branch_guid")
        val BRANCH_NAME = stringPreferencesKey("branch_name")
        val YEAR_GUID = stringPreferencesKey("year_guid")
        val YEAR_NAME = stringPreferencesKey("year_name")
        val OWNER_GUID = stringPreferencesKey("group_guid")
        val OWNER_NAME = stringPreferencesKey("group_name")
        val KIND = stringPreferencesKey("profile_kind")

        // Черновик мастера: пока группа не выбрана, активный профиль трогать нельзя.
        val DRAFT_BRANCH_GUID = stringPreferencesKey("draft_branch_guid")
        val DRAFT_BRANCH_NAME = stringPreferencesKey("draft_branch_name")
        val DRAFT_YEAR_GUID = stringPreferencesKey("draft_year_guid")
        val DRAFT_YEAR_NAME = stringPreferencesKey("draft_year_name")
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
        val CELEBRATE = booleanPreferencesKey("celebrate_lesson_end")
        val AMOLED = booleanPreferencesKey("amoled")
        val COUNTDOWN = booleanPreferencesKey("countdown")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }

    val profile: Flow<Profile> = context.dataStore.data.map { prefs ->
        Profile(
            kind = runCatching {
                ScheduleKind.valueOf(prefs[Keys.KIND] ?: ScheduleKind.GROUP.name)
            }.getOrDefault(ScheduleKind.GROUP),
            branchGuid = prefs[Keys.BRANCH_GUID].orEmpty(),
            branchName = prefs[Keys.BRANCH_NAME].orEmpty(),
            yearGuid = prefs[Keys.YEAR_GUID].orEmpty(),
            yearName = prefs[Keys.YEAR_NAME].orEmpty(),
            ownerGuid = prefs[Keys.OWNER_GUID].orEmpty(),
            ownerName = prefs[Keys.OWNER_NAME].orEmpty(),
        )
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
    }

    /** Черновик мастера выбора: филиал и год, пока не выбран владелец расписания. */
    val draft: Flow<SetupDraft> = context.dataStore.data.map { prefs ->
        SetupDraft(
            branchGuid = prefs[Keys.DRAFT_BRANCH_GUID].orEmpty(),
            branchName = prefs[Keys.DRAFT_BRANCH_NAME].orEmpty(),
            yearGuid = prefs[Keys.DRAFT_YEAR_GUID].orEmpty(),
            yearName = prefs[Keys.DRAFT_YEAR_NAME].orEmpty(),
        )
    }

    suspend fun setBranch(guid: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DRAFT_BRANCH_GUID] = guid
            prefs[Keys.DRAFT_BRANCH_NAME] = name
            // филиал сменился — выбранный ранее год больше не действителен
            prefs.remove(Keys.DRAFT_YEAR_GUID)
            prefs.remove(Keys.DRAFT_YEAR_NAME)
        }
    }

    suspend fun setKind(kind: ScheduleKind) {
        context.dataStore.edit { prefs -> prefs[Keys.KIND] = kind.name }
    }

    /** Целиком подменяет активный профиль — используется при переключении. */
    suspend fun setActiveProfile(profile: Profile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KIND] = profile.kind.name
            prefs[Keys.BRANCH_GUID] = profile.branchGuid
            prefs[Keys.BRANCH_NAME] = profile.branchName
            prefs[Keys.YEAR_GUID] = profile.yearGuid
            prefs[Keys.YEAR_NAME] = profile.yearName
            prefs[Keys.OWNER_GUID] = profile.ownerGuid
            prefs[Keys.OWNER_NAME] = profile.ownerName
        }
    }

    suspend fun setYear(guid: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DRAFT_YEAR_GUID] = guid
            prefs[Keys.DRAFT_YEAR_NAME] = name
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

    val sicretoUnlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FUN_UNLOCKED] ?: false
    }

    suspend fun unlockSicreto() {
        context.dataStore.edit { prefs -> prefs[Keys.FUN_UNLOCKED] = true }
    }

    val accent: Flow<AccentColor> = context.dataStore.data.map { prefs ->
        runCatching { AccentColor.valueOf(prefs[Keys.ACCENT] ?: AccentColor.MINT.name) }
            .getOrDefault(AccentColor.MINT)
    }

    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit { prefs -> prefs[Keys.ACCENT] = accent.name }
    }

    /** Салют в конце пары. Живёт в скрытом разделе, поэтому по умолчанию включён. */
    val celebrateLessonEnd: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.CELEBRATE] ?: true
    }

    suspend fun setCelebrateLessonEnd(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.CELEBRATE] = enabled }
    }

    /** Чистый чёрный вместо графитового — экономит батарею на OLED. */
    val amoled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AMOLED] ?: false
    }

    suspend fun setAmoled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AMOLED] = enabled }
    }

    val countdown: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.COUNTDOWN] ?: false
    }

    suspend fun setCountdown(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.COUNTDOWN] = enabled }
    }

    /** Отметка живёт на диске: в памяти она обнулялась вместе с процессом. */
    suspend fun lastUpdateCheck(): Long =
        context.dataStore.data.first()[Keys.LAST_UPDATE_CHECK] ?: 0L

    suspend fun setLastUpdateCheck(millis: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_UPDATE_CHECK] = millis }
    }

    suspend fun clearProfile() {
        context.dataStore.edit { prefs ->
            listOf(
                Keys.BRANCH_GUID, Keys.BRANCH_NAME,
                Keys.YEAR_GUID, Keys.YEAR_NAME,
                Keys.OWNER_GUID, Keys.OWNER_NAME, Keys.KIND,
            ).forEach(prefs::remove)
        }
    }
}
