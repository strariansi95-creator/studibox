package com.studibox.app.ui.lms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.remote.LmsAuthException
import com.studibox.app.data.remote.LmsCourse
import com.studibox.app.data.remote.LmsSection
import com.studibox.app.data.repo.LmsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LmsMaterialsState(
    val loading: Boolean = true,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val course: LmsCourse? = null,
    val sections: List<LmsSection> = emptyList(),
    /** Курс для предмета не найден автоматически или пользователь хочет сменить его. */
    val showPicker: Boolean = false,
    val allCourses: List<LmsCourse> = emptyList(),
    val lecturesOnly: Boolean = true
) {
    /** Разделы, в которых остались только лекции (или всё, если лекций нет). */
    val visibleSections: List<LmsSection>
        get() = if (!lecturesOnly || !hasLectures) sections else lectureSections

    val hasLectures: Boolean get() = lectureSections.isNotEmpty()

    private val lectureSections: List<LmsSection>
        get() = sections.mapNotNull { section ->
            val modules = section.modules.filter { LmsRepository.isLecture(section.name, it.name) }
            if (modules.isEmpty()) null else section.copy(modules = modules)
        }
}

class LmsMaterialsViewModel : ViewModel() {

    private val lms = AppGraph.lmsRepository

    val loggedIn: StateFlow<Boolean?> = lms.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(LmsMaterialsState())
    val state: StateFlow<LmsMaterialsState> = _state.asStateFlow()

    private var subject: String? = null
    private var job: Job? = null

    /** [subject] — предмет из расписания, либо [courseId] — конкретный курс из списка. */
    fun load(subject: String?, courseId: Int?, force: Boolean = false) {
        this.subject = subject
        job?.cancel()
        _state.value = LmsMaterialsState(lecturesOnly = _state.value.lecturesOnly)
        job = viewModelScope.launch {
            val course = if (courseId != null) {
                lms.courses(force).map { list -> list.firstOrNull { it.id == courseId } }
            } else {
                lms.courseForSubject(subject.orEmpty())
            }
            course
                .onSuccess { found ->
                    if (found == null) showPicker(noMatch = true) else loadContents(found, force)
                }
                .onFailure(::fail)
        }
    }

    fun changeCourse() {
        viewModelScope.launch { showPicker(noMatch = false) }
    }

    fun chooseCourse(course: LmsCourse) {
        job?.cancel()
        job = viewModelScope.launch {
            subject?.let { lms.rememberCourseForSubject(it, course.id) }
            _state.value = _state.value.copy(showPicker = false, loading = true, error = null)
            loadContents(course, force = false)
        }
    }

    fun setLecturesOnly(value: Boolean) {
        _state.value = _state.value.copy(lecturesOnly = value)
    }

    private suspend fun showPicker(noMatch: Boolean) {
        lms.courses()
            .onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    showPicker = true,
                    allCourses = list,
                    course = if (noMatch) null else _state.value.course
                )
            }
            .onFailure(::fail)
    }

    private suspend fun loadContents(course: LmsCourse, force: Boolean) {
        lms.contents(course.id, force)
            .onSuccess { sections ->
                _state.value = _state.value.copy(
                    loading = false,
                    course = course,
                    sections = sections,
                    showPicker = false
                )
            }
            .onFailure(::fail)
    }

    private fun fail(e: Throwable) {
        _state.value = _state.value.copy(
            loading = false,
            needsLogin = e is LmsAuthException,
            error = e.message ?: "Ошибка СДО"
        )
    }
}
