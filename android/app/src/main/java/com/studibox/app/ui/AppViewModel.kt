package com.studibox.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.prefs.ThemeMode
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.data.prefs.UserSettings
import com.studibox.app.data.remote.LmsSession
import com.studibox.app.data.repo.SyncResult
import com.studibox.app.notifications.LessonNotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val loading: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null
)

/** Состояние, общее для всех экранов: настройки, тема, статус синхронизации. */
class AppViewModel : ViewModel() {

    private val repo = AppGraph.scheduleRepository
    private val settingsStore = AppGraph.settings

    val settings: StateFlow<UserSettings?> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val version: StateFlow<String?> = repo.observeVersion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Вход в СДО СВФУ — показывается в настройках. */
    val lmsSession: StateFlow<LmsSession?> = AppGraph.lmsRepository.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun lmsLogout() {
        viewModelScope.launch { AppGraph.lmsRepository.logout() }
    }

    private val _sync = MutableStateFlow(SyncUiState())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()

    fun refresh(force: Boolean = true) {
        if (_sync.value.loading) return
        viewModelScope.launch {
            _sync.value = SyncUiState(loading = true)
            val isTeacher = settings.value?.userRole == UserRole.TEACHER
            val result = if (isTeacher) repo.refreshAllGroupsForTeacherMode(force) else repo.sync(force = force)
            _sync.value = when (result) {
                is SyncResult.Success ->
                    SyncUiState(message = "Обновлено: занятий ${result.lessons}")
                SyncResult.UpToDate ->
                    SyncUiState(message = "Расписание актуально")
                is SyncResult.Failed ->
                    SyncUiState(offline = result.offline, message = result.reason)
            }
            // После ручного обновления (не только фонового, как раньше)
            // напоминания переставляются на случай, если время пар
            // изменилось — иначе колокольчик показывал бы корректные
            // данные, а реальные уведомления пришли бы по старому времени.
            if (result is SyncResult.Success) {
                LessonNotificationScheduler.rescheduleToday(AppGraph.appContext)
            }
        }
    }

    fun clearMessage() {
        _sync.value = _sync.value.copy(message = null)
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setReminderMinutes(minutes: Int) {
        viewModelScope.launch { settingsStore.setReminderMinutes(minutes) }
    }

    fun clearGroup() {
        viewModelScope.launch { settingsStore.setSelectedGroup(null) }
    }

    fun setUserRole(role: UserRole) {
        viewModelScope.launch { settingsStore.setUserRole(role) }
    }

    fun clearTeacher() {
        viewModelScope.launch { settingsStore.setSelectedTeacher(null) }
    }
}
