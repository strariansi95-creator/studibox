package com.studibox.app.ui.lms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.data.remote.LmsSession
import com.studibox.app.ui.common.aggregateTeacherNames
import com.studibox.app.util.TeacherNameFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LmsLoginState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

class LmsLoginViewModel : ViewModel() {

    private val lms = AppGraph.lmsRepository

    private val _state = MutableStateFlow(LmsLoginState())
    val state: StateFlow<LmsLoginState> = _state.asStateFlow()

    fun setUsername(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login(onSuccess: () -> Unit) {
        val current = _state.value
        if (current.loading) return
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(error = "Введите логин и пароль")
            return
        }
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            lms.login(current.username, current.password)
                .onSuccess { session ->
                    linkTeacher(session)
                    // Пароль в памяти больше не нужен.
                    _state.value = LmsLoginState()
                    onSuccess()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Не удалось войти")
                }
        }
    }

    /**
     * Преподаватель, вошедший в СДО, сразу получает свои пары: если его ФИО
     * из СДО однозначно совпадает с одним из преподавателей в расписании,
     * оно выбирается автоматически. Ручной выбор в настройках остаётся.
     */
    private suspend fun linkTeacher(session: LmsSession) {
        val settings = AppGraph.settings.settings.first()
        if (settings.userRole != UserRole.TEACHER || session.fullName.isBlank()) return
        val names = aggregateTeacherNames(AppGraph.scheduleRepository.observeTeacherSubgroupRows().first())
        val matches = names.filter { TeacherNameFormat.matchesFullName(it, session.fullName) }
        if (matches.size == 1) AppGraph.settings.setSelectedTeacher(matches.single())
    }
}
