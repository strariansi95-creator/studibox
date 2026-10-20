package com.studibox.app.data.repo

import android.util.Log
import com.studibox.app.data.local.NoteDao
import com.studibox.app.data.local.NoteEntity
import com.studibox.app.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.time.LocalDate

/**
 * Заметки — исключительно локальные пользовательские данные.
 * Никакая синхронизация расписания их не удаляет и не изменяет.
 */
class NotesRepository(private val dao: NoteDao) {

    private val tag = "StudiboxNotes"

    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()
        .catch { e ->
            Log.e(tag, "Ошибка Room при чтении заметок", e)
            emit(emptyList())
        }

    fun observeForDate(date: LocalDate): Flow<List<NoteEntity>> =
        dao.observeForDate(DateUtils.iso(date), DateUtils.dayOfWeek(date))
            .catch { e ->
                Log.e(tag, "Ошибка Room при чтении заметок на дату", e)
                emit(emptyList())
            }

    suspend fun get(id: Long): NoteEntity? =
        runCatching { dao.getById(id) }.getOrNull()

    suspend fun save(
        id: Long,
        title: String,
        text: String,
        date: LocalDate?,
        dayOfWeek: Int?
    ): Boolean {
        val now = System.currentTimeMillis()
        val existing = if (id > 0) get(id) else null
        val note = NoteEntity(
            id = id,
            title = title.trim(),
            text = text.trim(),
            date = date?.let { DateUtils.iso(it) },
            dayOfWeek = if (date != null) null else dayOfWeek,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return runCatching { dao.upsert(note) }
            .onFailure { Log.e(tag, "Не удалось сохранить заметку", it) }
            .isSuccess
    }

    suspend fun delete(id: Long) {
        runCatching { dao.deleteById(id) }
            .onFailure { Log.e(tag, "Не удалось удалить заметку", it) }
    }
}
