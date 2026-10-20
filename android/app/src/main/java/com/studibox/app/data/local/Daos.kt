package com.studibox.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM groups WHERE name = :name")
    suspend fun exists(name: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GroupEntity>)

    @Query("DELETE FROM groups")
    suspend fun deleteAll()

    /**
     * Полная замена списка групп одной транзакцией.
     * Вызывается только после успешного ответа API — при сетевой ошибке
     * старый список остаётся нетронутым.
     */
    @Transaction
    suspend fun replaceAll(items: List<GroupEntity>) {
        if (items.isEmpty()) return
        deleteAll()
        insertAll(items)
    }
}

@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons WHERE groupName = :group AND date = :date ORDER BY number ASC, startTime ASC")
    fun observeByDate(group: String, date: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE groupName = :group AND date = :date ORDER BY number ASC, startTime ASC")
    suspend fun getByDate(group: String, date: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE groupName = :group ORDER BY date ASC, number ASC")
    fun observeByGroup(group: String): Flow<List<LessonEntity>>

    @Query("SELECT DISTINCT date FROM lessons WHERE groupName = :group ORDER BY date ASC")
    fun observeDates(group: String): Flow<List<String>>

    /** Непустые подгруппы, встречающиеся у группы. Пусто — переключатель подгрупп не нужен. */
    @Query("SELECT DISTINCT subgroup FROM lessons WHERE groupName = :group AND subgroup != '' ORDER BY subgroup ASC")
    fun observeSubgroups(group: String): Flow<List<String>>

    // -------------------------------------------------------------------
    // Режим преподавателя. Использует ту же таблицу lessons: у каждой
    // группы свои строки (deleteGroup трогает только её), поэтому можно
    // безопасно накопить расписание сразу нескольких групп и искать по
    // преподавателю независимо от того, кто сейчас выбранная группа.
    // -------------------------------------------------------------------

    /**
     * Пары (преподаватель, подгруппа), реально встретившиеся в расписании.
     * Подгруппа берётся ТОЛЬКО из фактических данных занятия — если для
     * конкретной пары/лабораторной подгруппа не указана, здесь будет ''.
     * Подгруппа в список преподавателей НЕ попадает (это свойство пары,
     * а не человека) — см. TeacherAggregation.aggregateTeacherNames и
     * TeacherLessonSlot.subgroup.
     */
    @Query("SELECT DISTINCT teacher, subgroup FROM lessons WHERE teacher != '' ORDER BY teacher ASC")
    fun observeTeacherSubgroupRows(): Flow<List<TeacherSubgroupRow>>

    @Query("SELECT * FROM lessons WHERE teacher = :teacher AND date = :date ORDER BY number ASC, startTime ASC")
    fun observeByTeacherAndDate(teacher: String, date: String): Flow<List<LessonEntity>>

    @Query("SELECT DISTINCT date FROM lessons WHERE teacher = :teacher ORDER BY date ASC")
    fun observeDatesForTeacher(teacher: String): Flow<List<String>>

    @Query("SELECT * FROM lessons WHERE teacher = :teacher AND date = :date ORDER BY number ASC, startTime ASC")
    suspend fun getByTeacherAndDate(teacher: String, date: String): List<LessonEntity>

    /** Пары преподавателя в диапазоне дат [from, to] — для экрана «СДО». */
    @Query(
        "SELECT * FROM lessons WHERE teacher = :teacher AND date >= :fromIso AND date <= :toIso " +
            "ORDER BY date ASC, number ASC, startTime ASC"
    )
    fun observeByTeacherInRange(teacher: String, fromIso: String, toIso: String): Flow<List<LessonEntity>>

    @Query("SELECT COUNT(*) FROM lessons WHERE teacher = :teacher")
    suspend fun countForTeacher(teacher: String): Int

    @Query("SELECT COUNT(*) FROM lessons WHERE groupName = :group")
    suspend fun countForGroup(group: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LessonEntity>)

    /**
     * Вставка/обновление по стабильному id занятия (см. LessonEntity.id —
     * group|date|number|subgroup[...]), БЕЗ удаления того, что уже есть.
     * На этом держится вся защита истории расписания (п. 4 задачи):
     * частичный/усечённый ответ сервера может только дополнить или
     * обновить локальные данные, но никогда не может их стереть.
     */
    @Upsert
    suspend fun upsertAll(items: List<LessonEntity>)

    @Query("DELETE FROM lessons WHERE groupName = :group")
    suspend fun deleteGroup(group: String)

    /** Подчистка только устаревших (старше cutoffIso) записей ОДНОЙ группы. */
    @Query("DELETE FROM lessons WHERE groupName = :group AND date < :cutoffIso")
    suspend fun deletePastForGroup(group: String, cutoffIso: String)

    /**
     * То же самое, но сразу для ВСЕХ групп — используется в режиме
     * преподавателя, где в таблице одновременно лежит расписание многих
     * групп (см. ScheduleRepository.refreshAllGroupsForTeacherMode).
     */
    @Query("DELETE FROM lessons WHERE date < :cutoffIso")
    suspend fun deletePastAll(cutoffIso: String)

    /**
     * Применяет новую порцию расписания группы БЕЗ потери истории: каждое
     * занятие вставляется/обновляется по своему стабильному id, старая
     * таблица целиком не удаляется (раньше здесь было deleteGroup+insertAll —
     * именно это стирало прошлые дни при любом неполном ответе сервера).
     * Отдельно удаляются только записи этой группы старше [cutoffIso] —
     * так расписание прошлых дней остаётся доступно минимум несколько
     * дней, а совсем старые записи не копятся бесконечно.
     */
    @Transaction
    suspend fun replaceForGroup(group: String, items: List<LessonEntity>, cutoffIso: String) {
        if (items.isNotEmpty()) upsertAll(items)
        deletePastForGroup(group, cutoffIso)
    }
}

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY (date IS NULL), date DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE date = :date OR dayOfWeek = :dayOfWeek ORDER BY updatedAt DESC")
    fun observeForDate(date: String, dayOfWeek: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MetaDao {

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: MetaEntity)

    suspend fun put(key: String, value: String) = put(MetaEntity(key, value))
}
