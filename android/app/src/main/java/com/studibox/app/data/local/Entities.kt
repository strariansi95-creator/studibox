package com.studibox.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val name: String,
    val serverId: Int?,
    val sortOrder: Int
)

@Entity(
    tableName = "lessons",
    indices = [
        Index(value = ["groupName", "date"]),
        Index(value = ["groupName", "dayOfWeek"])
    ]
)
data class LessonEntity(
    /** Стабильный ключ: группа|дата|номер|подгруппа — защищает от дублей при импорте. */
    @PrimaryKey val id: String,
    val groupName: String,
    /** ISO-дата YYYY-MM-DD. Главный идентификатор занятия. */
    val date: String,
    /** 1=ПН ... 7=ВС. Всегда вычислен из [date], а не взят из текста. */
    val dayOfWeek: Int,
    val number: Int,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val teacher: String,
    val classroom: String,
    val weekType: String,
    val subgroup: String,
    /** Тип занятия: Лекция / Практика / Лабораторная и т.п. Может быть пустым. */
    val type: String = ""
)

/**
 * Проекция для запроса пар (преподаватель, подгруппа) — не отдельная таблица,
 * а просто результат SELECT DISTINCT teacher, subgroup FROM lessons.
 */
data class TeacherSubgroupRow(
    val teacher: String,
    val subgroup: String
)

/**
 * Пользовательские заметки. Полностью локальная таблица:
 * синхронизация расписания её НИКОГДА не очищает.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    /** ISO-дата или null, если заметка без даты. */
    val date: String?,
    /** 1..7 или null. Заметка «на день недели» без конкретной даты. */
    val dayOfWeek: Int?,
    val createdAt: Long,
    val updatedAt: Long
)

/** Служебные значения: версия расписания, время последней синхронизации и т.п. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String
)
