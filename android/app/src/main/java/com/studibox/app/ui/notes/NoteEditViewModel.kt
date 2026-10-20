package com.studibox.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studibox.app.AppGraph
import com.studibox.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class NoteEditState(
    val id: Long = 0,
    val title: String = "",
    val text: String = "",
    val date: LocalDate? = null,
    val dayOfWeek: Int? = null,
    val loading: Boolean = true,
    val error: String? = null
)

class NoteEditViewModel : ViewModel() {

    private val repo = AppGraph.notesRepository

    private val _state = MutableStateFlow(NoteEditState())
    val state: StateFlow<NoteEditState> = _state.asStateFlow()

    fun load(noteId: Long, presetDate: String?) {
        viewModelScope.launch {
            if (noteId > 0) {
                val note = repo.get(noteId)
                _state.value = if (note == null) {
                    NoteEditState(loading = false, error = "Заметка не найдена")
                } else {
                    NoteEditState(
                        id = note.id,
                        title = note.title,
                        text = note.text,
                        date = note.date?.let { DateUtils.parseDate(it) },
                        dayOfWeek = note.dayOfWeek,
                        loading = false
                    )
                }
            } else {
                _state.value = NoteEditState(
                    date = presetDate?.let { DateUtils.parseDate(it) },
                    loading = false
                )
            }
        }
    }

    fun setTitle(value: String) {
        _state.value = _state.value.copy(title = value)
    }

    fun setText(value: String) {
        _state.value = _state.value.copy(text = value)
    }

    fun setDate(date: LocalDate?) {
        _state.value = _state.value.copy(date = date, dayOfWeek = null)
    }

    fun setDayOfWeek(dayOfWeek: Int?) {
        _state.value = _state.value.copy(dayOfWeek = dayOfWeek, date = null)
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.title.isBlank() && current.text.isBlank()) {
            _state.value = current.copy(error = "Введите название или текст заметки")
            return
        }
        viewModelScope.launch {
            val ok = repo.save(
                id = current.id,
                title = current.title,
                text = current.text,
                date = current.date,
                dayOfWeek = current.dayOfWeek
            )
            if (ok) onSaved() else _state.value = current.copy(error = "Не удалось сохранить заметку")
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.id
        viewModelScope.launch {
            if (id > 0) repo.delete(id)
            onDeleted()
        }
    }
}
