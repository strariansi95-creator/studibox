package com.studibox.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.local.GroupEntity
import com.studibox.app.data.repo.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel : ViewModel() {

    private val repo = AppGraph.scheduleRepository
    private val settings = AppGraph.settings

    val groups: StateFlow<List<GroupEntity>> = repo.observeGroups()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadGroups()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun loadGroups() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = repo.refreshGroups()) {
                is SyncResult.Failed -> _error.value = result.reason
                else -> Unit
            }
            _loading.value = false
        }
    }

    fun selectGroup(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setSelectedGroup(name)
            _loading.value = true
            repo.refreshSchedule(name)
            _loading.value = false
            onDone()
        }
    }

    fun filtered(): List<GroupEntity> {
        val q = _query.value.trim()
        if (q.isEmpty()) return groups.value
        return groups.value.filter { it.name.contains(q, ignoreCase = true) }
    }
}
