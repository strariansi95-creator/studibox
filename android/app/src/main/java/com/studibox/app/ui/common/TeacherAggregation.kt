package com.studibox.app.ui.common

import com.studibox.app.data.local.LessonEntity
import com.studibox.app.data.local.TeacherSubgroupRow

/** Одна группа внутри объединённой пары преподавателя, с её аудиторией. */
data class GroupRoom(val group: String, val classroom: String)

/**
 * Одна пара преподавателя, возможно читаемая сразу нескольким группам.
 * [id] стабилен для одного и того же слота (дата+время+предмет+подгруппа) и
 * используется и как ключ в LazyColumn, и для определения «сейчас/следующая».
 *
 * [subgroup] — подгруппа ЭТОЙ КОНКРЕТНОЙ пары (может быть пустой, если в
 * исходных данных подгруппа не указана). Подгруппа — свойство пары, а не
 * преподавателя, поэтому у разных пар одного и того же преподавателя она
 * может отличаться, и это нормально — см. TeacherLessonCard.
 */
data class TeacherLessonSlot(
    val id: String,
    val date: String,
    val dayOfWeek: Int,
    val number: Int,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val type: String,
    val weekType: String,
    val subgroup: String,
    val groups: List<GroupRoom>
)

private data class SlotKey(
    val date: String,
    val startTime: String,
    val endTime: String,
    val subject: String,
    // Подгруппа — часть ключа: если один и тот же преподаватель в одно и то
    // же время ведёт подгруппу 1 отдельно от подгруппы 2, это ДВЕ разные
    // пары для него, и объединять их в один блок (теряя вторую подгруппу)
    // нельзя.
    val subgroup: String
)

/**
 * Список преподавателей для экрана выбора. ФИО уже нормализованы на уровне
 * ApiMapper/TeacherNameFormat (в том числе разведены, если в одной ячейке
 * расписания было указано несколько человек — см. TeacherNameFormat.kt),
 * поэтому здесь остаётся только собрать уникальные значения и отсортировать.
 *
 * Подгруппа сюда намеренно не попадает: она относится к конкретной паре,
 * а не к преподавателю в целом (см. TeacherLessonSlot.subgroup) — раньше
 * это приводило к ошибочным подписям вида "Абрамов А.Х. — 1 и 2 подгруппа"
 * в общем списке.
 */
fun aggregateTeacherNames(rows: List<TeacherSubgroupRow>): List<String> =
    rows.map { it.teacher }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

/**
 * Схлопывает список занятий (уже отфильтрованных по преподавателю и дате) так,
 * чтобы одна и та же пара у нескольких групп превратилась в ОДИН блок со списком
 * групп, а не в несколько визуально одинаковых карточек подряд.
 */
fun aggregateForTeacher(lessons: List<LessonEntity>): List<TeacherLessonSlot> {
    return lessons
        .groupBy { SlotKey(it.date, it.startTime, it.endTime, it.subject, it.subgroup) }
        .map { (key, group) ->
            val first = group.first()
            TeacherLessonSlot(
                id = "${key.date}|${key.startTime}|${key.endTime}|${key.subject}|${key.subgroup}",
                date = key.date,
                dayOfWeek = first.dayOfWeek,
                number = first.number,
                startTime = key.startTime,
                endTime = key.endTime,
                subject = key.subject,
                type = first.type,
                weekType = first.weekType,
                subgroup = key.subgroup,
                groups = group
                    .map { GroupRoom(it.groupName, it.classroom) }
                    .distinctBy { it.group }
                    .sortedBy { it.group }
            )
        }
        .sortedWith(compareBy({ it.date }, { it.number }, { it.startTime }))
}
