package com.studibox.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.ui.common.BreakInfo
import com.studibox.app.ui.common.aggregateForTeacher
import com.studibox.app.ui.common.computeBreaks
import com.studibox.app.ui.common.computeBreaksForTeacher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Данные для экрана, который открывается по колокольчику (п. 3 задачи):
 * список перемен сегодняшнего дня. Настройки самих напоминаний (вкл/выкл,
 * за сколько минут) не дублируются здесь отдельным состоянием — экран
 * получает appViewModel и использует его методы/StateFlow напрямую, как и
 * SettingsScreen, чтобы не было двух источников правды.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel : ViewModel() {

    private val scheduleRepo = AppGraph.scheduleRepository

    val todayBreaks: StateFlow<List<BreakInfo>> = AppGraph.settings.settings
        .flatMapLatest { settings ->
            when (settings.userRole) {
                UserRole.STUDENT -> {
                    val group = settings.selectedGroup
                    if (group == null) flowOf(emptyList())
                    else scheduleRepo.observeLessonsForDate(group, LocalDate.now())
                        .map { lessons -> computeBreaks(lessons) }
                }

                UserRole.TEACHER -> {
                    val teacher = settings.selectedTeacher
                    if (teacher == null) flowOf(emptyList())
                    else scheduleRepo.observeLessonsForTeacherAndDate(teacher, LocalDate.now())
                        .map { lessons -> computeBreaksForTeacher(aggregateForTeacher(lessons)) }
                }
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
