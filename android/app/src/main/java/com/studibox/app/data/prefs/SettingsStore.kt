package com.studibox.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Кто пользуется приложением — от этого зависит, что фильтрует расписание. */
enum class UserRole { STUDENT, TEACHER }

data class UserSettings(
    val userRole: UserRole = UserRole.STUDENT,
    val selectedGroup: String?,
    val themeMode: ThemeMode,
    val notificationsEnabled: Boolean,
    val reminderMinutes: Int,
    /** null = «Все». Это только фильтр отображения, в Room и на сервере ничего не меняет. */
    val selectedSubgroup: String? = null,
    /** ФИО преподавателя — используется только при userRole == TEACHER. */
    val selectedTeacher: String? = null,
    /**
     * true, если пользователь явно проходил экран выбора роли.
     * У существующих пользователей (уже выбрана группа) остаётся false —
     * им экран выбора роли не показывается, они и так студенты.
     */
    val roleChosen: Boolean = false
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studibox_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val GROUP = stringPreferencesKey("selected_group")
        val THEME = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val SUBGROUP = stringPreferencesKey("selected_subgroup")
        val ROLE = stringPreferencesKey("user_role")
        val TEACHER = stringPreferencesKey("selected_teacher")
        val ROLE_CHOSEN = booleanPreferencesKey("role_chosen")
    }

    val settings: Flow<UserSettings> = context.dataStore.data
        .catch { e ->
            // Повреждённый файл настроек не должен ронять приложение.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            UserSettings(
                userRole = runCatching {
                    UserRole.valueOf(prefs[Keys.ROLE] ?: UserRole.STUDENT.name)
                }.getOrDefault(UserRole.STUDENT),
                selectedGroup = prefs[Keys.GROUP]?.takeIf { it.isNotBlank() },
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.SYSTEM.name)
                }.getOrDefault(ThemeMode.SYSTEM),
                notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
                reminderMinutes = prefs[Keys.REMINDER_MINUTES] ?: 10,
                selectedSubgroup = prefs[Keys.SUBGROUP]?.takeIf { it.isNotBlank() },
                selectedTeacher = prefs[Keys.TEACHER]?.takeIf { it.isNotBlank() },
                roleChosen = prefs[Keys.ROLE_CHOSEN] ?: false
            )
        }

    suspend fun setSelectedSubgroup(subgroup: String?) {
        context.dataStore.edit { prefs ->
            if (subgroup.isNullOrBlank()) prefs.remove(Keys.SUBGROUP) else prefs[Keys.SUBGROUP] = subgroup
        }
    }

    /**
     * Переключение роли сбрасывает выбор с другой стороны: студент → преподаватель
     * не должен унаследовать старую группу, и наоборот. UI сам предложит выбрать
     * заново нужное (группу или ФИО) — см. навигацию.
     */
    suspend fun setUserRole(role: UserRole) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROLE] = role.name
            prefs[Keys.ROLE_CHOSEN] = true
            when (role) {
                UserRole.TEACHER -> prefs.remove(Keys.GROUP)
                UserRole.STUDENT -> prefs.remove(Keys.TEACHER)
            }
        }
    }

    suspend fun setSelectedTeacher(teacher: String?) {
        context.dataStore.edit { prefs ->
            if (teacher.isNullOrBlank()) {
                prefs.remove(Keys.TEACHER)
            } else {
                prefs[Keys.TEACHER] = teacher
                prefs[Keys.ROLE_CHOSEN] = true
                if (prefs[Keys.ROLE] == null) prefs[Keys.ROLE] = UserRole.TEACHER.name
            }
        }
    }

    /**
     * Как только у пользователя реально появляется группа, считаем, что роль
     * выбрана окончательно — даже если он попал сюда напрямую, минуя
     * RolePickerScreen (например, обновился со старой версии приложения).
     * Это гарантирует, что если группа позже пропадёт с сервера, пользователя
     * вернёт на выбор ГРУППЫ, а не заново на выбор роли.
     */
    suspend fun setSelectedGroup(group: String?) {
        context.dataStore.edit { prefs ->
            if (group.isNullOrBlank()) {
                prefs.remove(Keys.GROUP)
            } else {
                prefs[Keys.GROUP] = group
                prefs[Keys.ROLE_CHOSEN] = true
                if (prefs[Keys.ROLE] == null) prefs[Keys.ROLE] = UserRole.STUDENT.name
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setReminderMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.REMINDER_MINUTES] = minutes.coerceIn(1, 60) }
    }
}
