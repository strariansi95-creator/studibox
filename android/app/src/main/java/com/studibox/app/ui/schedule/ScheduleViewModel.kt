package com.studibox.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.local.LessonEntity
import com.studibox.app.data.local.NoteEntity
import com.studibox.app.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel : ViewModel() {

    private val scheduleRepo = AppGraph.scheduleRepository
    private val notesRepo = AppGraph.notesRepository

    /**
     * В воскресенье учебная неделя уже закончилась — открываем следующую
     * (так же, как сайт). В остальные дни — текущую.
     */
    private fun studyDate(): LocalDate =
        LocalDate.now().let { if (DateUtils.dayOfWeek(it) == 7) it.plusDays(1) else it }

    /** Понедельник отображаемой недели. */
    private val _weekStart = MutableStateFlow(DateUtils.weekStart(studyDate()))
    val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

    /** Выбранный день недели: 1=ПН ... 6=СБ. Воскресенье не выбирается вручную. */
    private val _selectedDayOfWeek = MutableStateFlow(DateUtils.dayOfWeek(studyDate()))
    val selectedDayOfWeek: StateFlow<Int> = _selectedDayOfWeek.asStateFlow()

    /**
     * Реальная дата выбранного дня. Именно она — единственный критерий выборки
     * занятий, поэтому занятия разных дат физически не могут перемешаться.
     */
    val selectedDate: StateFlow<LocalDate> =
        combine(_weekStart, _selectedDayOfWeek) { start, dow ->
            start.plusDays((dow - 1).toLong())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            studyDate()
        )

    val group: StateFlow<String?> = AppGraph.settings.settings
        .map { it.selectedGroup }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedSubgroup: StateFlow<String?> = AppGraph.settings.settings
        .map { it.selectedSubgroup }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Пусто — переключатель подгрупп в UI не показывается. */
    val availableSubgroups: StateFlow<List<String>> = AppGraph.settings.settings
        .flatMapLatest { settings ->
            val selected = settings.selectedGroup
            if (selected == null) flowOf(emptyList()) else scheduleRepo.observeSubgroups(selected)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lessons: StateFlow<List<LessonEntity>> =
        combine(AppGraph.settings.settings, selectedDate) { settings, date ->
            Triple(settings.selectedGroup, date, settings.selectedSubgroup)
        }.flatMapLatest { (selectedGroup, date, subgroup) ->
            if (selectedGroup == null) flowOf(emptyList())
            else scheduleRepo.observeLessonsForDate(selectedGroup, date)
                .map { scheduleRepo.applySubgroupFilter(it, subgroup) }
                .map { com.studibox.app.ui.common.mergeTeachersForDisplay(it) }
        }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSubgroup(subgroup: String?) {
        viewModelScope.launch { AppGraph.settings.setSelectedSubgroup(subgroup) }
    }

    // ---------------------------------------------------------- режим преподавателя
    // Отдельные поля, студенческие выше не изменялись и не переиспользуются.

    val role: StateFlow<com.studibox.app.data.prefs.UserRole> = AppGraph.settings.settings
        .map { it.userRole }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.studibox.app.data.prefs.UserRole.STUDENT)

    val teacher: StateFlow<String?> = AppGraph.settings.settings
        .map { it.selectedTeacher }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Пары преподавателя на выбранную дату, уже объединённые по группам. */
    val teacherSlots: StateFlow<List<com.studibox.app.ui.common.TeacherLessonSlot>> =
        combine(AppGraph.settings.settings, selectedDate) { settings, date -> settings.selectedTeacher to date }
            .flatMapLatest { (teacherName, date) ->
                if (teacherName == null) flowOf(emptyList())
                else scheduleRepo.observeLessonsForTeacherAndDate(teacherName, date)
                    .map { com.studibox.app.ui.common.aggregateForTeacher(it) }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val teacherAvailableDates: StateFlow<Set<LocalDate>> = AppGraph.settings.settings
        .flatMapLatest { settings ->
            val teacherName = settings.selectedTeacher
            if (teacherName == null) flowOf(emptyList()) else scheduleRepo.observeAvailableDatesForTeacher(teacherName)
        }
        .map { it.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val notes: StateFlow<List<NoteEntity>> = selectedDate
        .flatMapLatest { date -> notesRepo.observeForDate(date) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Даты, по которым вообще есть занятия — для подсветки дней с парами. */
    val availableDates: StateFlow<Set<LocalDate>> = AppGraph.settings.settings
        .flatMapLatest { settings ->
            val selected = settings.selectedGroup
            if (selected == null) flowOf(emptyList()) else scheduleRepo.observeAvailableDates(selected)
        }
        .map { it.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun selectDay(dayOfWeek: Int) {
        if (dayOfWeek in 1..7) _selectedDayOfWeek.value = dayOfWeek
    }

    fun previousWeek() {
        _weekStart.value = _weekStart.value.minusWeeks(1)
    }

    fun nextWeek() {
        _weekStart.value = _weekStart.value.plusWeeks(1)
    }

    fun goToToday() {
        val date = studyDate()
        _weekStart.value = DateUtils.weekStart(date)
        _selectedDayOfWeek.value = DateUtils.dayOfWeek(date)
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { notesRepo.delete(id) }
    }
}
