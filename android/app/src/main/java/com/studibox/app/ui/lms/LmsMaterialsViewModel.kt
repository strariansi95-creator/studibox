package com.studibox.app.ui.lms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.data.remote.LmsAssignment
import com.studibox.app.data.remote.LmsAuthException
import com.studibox.app.data.remote.LmsCourse
import com.studibox.app.data.remote.LmsFile
import com.studibox.app.data.remote.LmsModule
import com.studibox.app.data.remote.LmsSection
import com.studibox.app.data.repo.LectureStorage
import com.studibox.app.data.repo.LmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LmsMaterialsState(
    val loading: Boolean = true,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val course: LmsCourse? = null,
    val sections: List<LmsSection> = emptyList(),
    /** Задания курса: сначала с ближайшим сроком, прошедшие не показываются. */
    val assignments: List<LmsAssignment> = emptyList(),
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

/** Что открыть после нажатия: внешнюю ссылку или скачанный файл. */
sealed interface LmsOpenRequest {
    data class Link(val url: String) : LmsOpenRequest
    data class Pdf(val file: File, val title: String) : LmsOpenRequest
    data class OtherFile(val file: File, val mimeType: String) : LmsOpenRequest
    data class Message(val text: String) : LmsOpenRequest
}

class LmsMaterialsViewModel : ViewModel() {

    private val lms = AppGraph.lmsRepository
    private val storage = AppGraph.lectureStorage

    val loggedIn: StateFlow<Boolean?> = lms.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(LmsMaterialsState())
    val state: StateFlow<LmsMaterialsState> = _state.asStateFlow()

    /** Файлы, которые сейчас качаются (ключи lmsFileKey). */
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    /** Ключи уже скачанных файлов текущего курса. */
    val downloaded: StateFlow<Set<String>> = combine(storage.downloaded, _state) { _, current ->
        downloadedKeys(current)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _open = Channel<LmsOpenRequest>(Channel.BUFFERED)
    val openRequests: Flow<LmsOpenRequest> = _open.receiveAsFlow()

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

    /** Ссылка — в браузер; файл — скачать (если ещё нет) и открыть. */
    fun openFile(module: LmsModule, file: LmsFile) {
        if (file.isLink) {
            _open.trySend(LmsOpenRequest.Link(file.url))
            return
        }
        val course = _state.value.course ?: return
        val key = lmsFileKey(module, file)
        if (key in _downloading.value) return
        viewModelScope.launch {
            _downloading.update { it + key }
            storage.download(course, module, file)
                .onSuccess { saved ->
                    _open.send(
                        if (LectureStorage.isPdf(saved, file.mimeType)) {
                            LmsOpenRequest.Pdf(saved, module.name)
                        } else {
                            LmsOpenRequest.OtherFile(saved, file.mimeType)
                        }
                    )
                }
                .onFailure { _open.send(LmsOpenRequest.Message(it.message ?: "Не удалось скачать файл")) }
            _downloading.update { it - key }
        }
    }

    /** Скачивает для офлайна все файлы видимых сейчас материалов (по одному). */
    fun downloadAll() {
        val current = _state.value
        val course = current.course ?: return
        val pending = current.visibleSections
            .flatMap { section -> section.modules.flatMap { m -> m.files.filterNot { it.isLink }.map { m to it } } }
            .filterNot { (m, f) -> storage.isDownloaded(course.id, m, f) }
        if (pending.isEmpty()) {
            _open.trySend(LmsOpenRequest.Message("Все материалы уже скачаны"))
            return
        }
        viewModelScope.launch {
            var failed = 0
            for ((module, file) in pending) {
                val key = lmsFileKey(module, file)
                _downloading.update { it + key }
                if (storage.download(course, module, file).isFailure) failed++
                _downloading.update { it - key }
            }
            _open.send(
                LmsOpenRequest.Message(
                    if (failed == 0) "Скачано: ${pending.size}. Доступно без интернета"
                    else "Скачано ${pending.size - failed} из ${pending.size}"
                )
            )
        }
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
                // Задания — дополнительно: их ошибка не мешает показать лекции.
                val now = System.currentTimeMillis() / 1000
                val assignments = lms.assignments(force).getOrDefault(emptyList())
                    .filter { it.courseId == course.id && (it.dueDate == null || it.dueDate >= now) }
                    .sortedBy { it.dueDate ?: Long.MAX_VALUE }
                _state.value = _state.value.copy(assignments = assignments)
            }
            .onFailure(::fail)
    }

    private fun downloadedKeys(current: LmsMaterialsState): Set<String> {
        val course = current.course ?: return emptySet()
        return current.sections.flatMap { section ->
            section.modules.flatMap { module ->
                module.files.filter { storage.isDownloaded(course.id, module, it) }
                    .map { lmsFileKey(module, it) }
            }
        }.toSet()
    }

    private fun fail(e: Throwable) {
        _state.value = _state.value.copy(
            loading = false,
            needsLogin = e is LmsAuthException,
            error = e.message ?: "Ошибка СДО"
        )
    }
}
