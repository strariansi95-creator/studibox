package com.studibox.app.ui.lms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.data.remote.LmsAssignment
import com.studibox.app.data.remote.LmsCourse
import com.studibox.app.data.repo.DownloadedLecture
import com.studibox.app.data.remote.LmsSession
import com.studibox.app.ui.common.TeacherLessonSlot
import com.studibox.app.ui.common.aggregateForTeacher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LmsCoursesState(
    val loading: Boolean = false,
    val error: String? = null,
    val courses: List<LmsCourse> = emptyList(),
    /** Задания со сроком в ближайшие две недели. */
    val deadlines: List<LmsAssignment> = emptyList()
)

/** Сколько дней вперёд показывать пары преподавателя на вкладке «СДО». */
private const val TEACHER_DAYS_AHEAD = 13L

@OptIn(ExperimentalCoroutinesApi::class)
class LmsHomeViewModel : ViewModel() {

    private val lms = AppGraph.lmsRepository

    /** null — ещё читаем DataStore, чтобы не мигать экраном входа. */
    val session: StateFlow<LmsSession?> = lms.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessionLoaded: StateFlow<Boolean> = lms.session
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val role: StateFlow<UserRole> = AppGraph.settings.settings
        .map { it.userRole }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserRole.STUDENT)

    val teacher: StateFlow<String?> = AppGraph.settings.settings
        .map { it.selectedTeacher }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Пары преподавателя на две недели вперёд, начиная с сегодняшней. */
    val teacherSlots: StateFlow<List<TeacherLessonSlot>> = AppGraph.settings.settings
        .map { it.selectedTeacher }
        .flatMapLatest { name ->
            if (name == null) {
                flowOf(emptyList())
            } else {
                val today = LocalDate.now()
                AppGraph.scheduleRepository
                    .observeLessonsForTeacherInRange(name, today, today.plusDays(TEACHER_DAYS_AHEAD))
                    .map { aggregateForTeacher(it) }
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _courses = MutableStateFlow(LmsCoursesState())
    val courses: StateFlow<LmsCoursesState> = _courses.asStateFlow()

    fun loadCourses(force: Boolean = false) {
        _courses.value = _courses.value.copy(loading = true, error = null)
        viewModelScope.launch {
            lms.courses(force)
                .onSuccess { list ->
                    _courses.value = LmsCoursesState(courses = list)
                    // Дедлайны — отдельным запросом: их ошибка не прячет список курсов.
                    val deadlines = lms.upcomingDeadlines(days = 14, force = force).getOrDefault(emptyList())
                    _courses.value = _courses.value.copy(deadlines = deadlines)
                }
                .onFailure { _courses.value = LmsCoursesState(error = it.message ?: "Ошибка СДО") }
        }
    }

    /** Скачанные лекции — показываются и без входа, и без интернета. */
    val downloads: StateFlow<List<DownloadedLecture>> = AppGraph.lectureStorage.downloaded

    fun deleteDownload(lecture: DownloadedLecture) {
        viewModelScope.launch { AppGraph.lectureStorage.delete(lecture) }
    }

    fun logout() {
        viewModelScope.launch {
            lms.logout()
            _courses.value = LmsCoursesState()
        }
    }
}
