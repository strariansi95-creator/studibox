package com.studibox.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.local.LessonEntity
import com.studibox.app.data.local.NoteEntity
import com.studibox.app.data.remote.LmsAssignment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel : ViewModel() {

    private val scheduleRepo = AppGraph.scheduleRepository
    private val notesRepo = AppGraph.notesRepository

    /** Дата вычисляется при каждом обращении — приложение переживает смену суток. */
    val today: LocalDate get() = LocalDate.now()

    val group: StateFlow<String?> = AppGraph.settings.settings
        .map { it.selectedGroup }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedSubgroup: StateFlow<String?> = AppGraph.settings.settings
        .map { it.selectedSubgroup }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val availableSubgroups: StateFlow<List<String>> = AppGraph.settings.settings
        .flatMapLatest { settings ->
            val selected = settings.selectedGroup
            if (selected == null) flowOf(emptyList()) else scheduleRepo.observeSubgroups(selected)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lessons: StateFlow<List<LessonEntity>> = AppGraph.settings.settings
        .flatMapLatest { settings ->
            val selected = settings.selectedGroup
            if (selected == null) flowOf(emptyList())
            else scheduleRepo.observeLessonsForDate(selected, LocalDate.now())
                .map { scheduleRepo.applySubgroupFilter(it, settings.selectedSubgroup) }
                .map { com.studibox.app.ui.common.mergeTeachersForDisplay(it) }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = notesRepo.observeForDate(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Задания СДО со сроком в ближайшую неделю. Пусто, если вход в СДО не
     * выполнен или нет сети — экран «Сегодня» от этого не зависит.
     */
    val deadlines: StateFlow<List<LmsAssignment>> = AppGraph.lmsRepository.session
        .mapLatest { session ->
            if (session == null) emptyList()
            else AppGraph.lmsRepository.upcomingDeadlines(days = 7).getOrDefault(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteNote(id: Long) {
        viewModelScope.launch { notesRepo.delete(id) }
    }

    fun selectSubgroup(subgroup: String?) {
        viewModelScope.launch { AppGraph.settings.setSelectedSubgroup(subgroup) }
    }

    // ---------------------------------------------------------- режим преподавателя

    val role: StateFlow<com.studibox.app.data.prefs.UserRole> = AppGraph.settings.settings
        .map { it.userRole }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.studibox.app.data.prefs.UserRole.STUDENT)

    val teacher: StateFlow<String?> = AppGraph.settings.settings
        .map { it.selectedTeacher }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val teacherSlots: StateFlow<List<com.studibox.app.ui.common.TeacherLessonSlot>> = AppGraph.settings.settings
        .flatMapLatest { settings ->
            val teacherName = settings.selectedTeacher
            if (teacherName == null) flowOf(emptyList())
            else scheduleRepo.observeLessonsForTeacherAndDate(teacherName, LocalDate.now())
                .map { com.studibox.app.ui.common.aggregateForTeacher(it) }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
