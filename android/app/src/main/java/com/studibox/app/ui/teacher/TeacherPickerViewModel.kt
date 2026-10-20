package com.studibox.app.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.repo.SyncResult
import com.studibox.app.ui.common.aggregateTeacherNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Список преподавателей строится на стороне приложения из уже загруженного
 * расписания всех групп (см. ScheduleRepository.refreshAllGroupsForTeacherMode) —
 * отдельного backend-эндпоинта для этого не требуется, как и указано в задаче.
 */
class TeacherPickerViewModel : ViewModel() {

    private val repo = AppGraph.scheduleRepository

    /** Только нормализованные ФИО — без подгруппы рядом с именем. */
    val teachers: StateFlow<List<String>> = repo.observeTeacherSubgroupRows()
        .map { rows -> aggregateTeacherNames(rows) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun filtered(): List<String> {
        val q = _query.value.trim()
        return if (q.isEmpty()) teachers.value else teachers.value.filter { it.contains(q, ignoreCase = true) }
    }

    fun load() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = repo.refreshAllGroupsForTeacherMode()) {
                is SyncResult.Failed -> _error.value = result.reason
                else -> Unit
            }
            _loading.value = false
        }
    }

    fun selectTeacher(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            AppGraph.settings.setSelectedTeacher(name)
            onDone()
        }
    }
}
