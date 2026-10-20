package com.studibox.app.ui.common

import com.studibox.app.data.local.LessonEntity

/**
 * Если ApiMapper развёл несколько преподавателей одной ячейки на отдельные
 * записи Room (см. TeacherNameFormat.splitRawField — это нужно, чтобы режим
 * преподавателя видел каждого человека отдельно и не склеивал их ФИО), для
 * СТУДЕНТА это по-прежнему ОДНА пара. Показывать её двумя одинаковыми
 * карточками подряд, различающимися только преподавателем, не нужно —
 * здесь такие записи схлопываются обратно в одну карточку со списком
 * преподавателей через запятую.
 *
 * На режим преподавателя эта функция не влияет и не вызывается: там список
 * уже отфильтрован по одному конкретному ФИО (`WHERE teacher = :teacher`)
 * до того, как дело доходит до отображения, поэтому склеенных дублей там
 * просто не бывает.
 */
private data class MergeKey(
    val groupName: String,
    val date: String,
    val number: Int,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val classroom: String,
    val weekType: String,
    val subgroup: String,
    val type: String
)

fun mergeTeachersForDisplay(lessons: List<LessonEntity>): List<LessonEntity> {
    if (lessons.size <= 1) return lessons

    return lessons
        .groupBy {
            MergeKey(
                groupName = it.groupName,
                date = it.date,
                number = it.number,
                startTime = it.startTime,
                endTime = it.endTime,
                subject = it.subject,
                classroom = it.classroom,
                weekType = it.weekType,
                subgroup = it.subgroup,
                type = it.type
            )
        }
        .map { (_, group) ->
            if (group.size == 1) {
                group[0]
            } else {
                val teachers = group
                    .map { it.teacher }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                group[0].copy(
                    // Стабильный ключ для LazyColumn: объединяем исходные id,
                    // а не выбираем один из них — так карточка не "прыгает"
                    // между обновлениями, если порядок преподавателей в
                    // ответе API вдруг поменяется местами.
                    id = group.map { it.id }.sorted().joinToString("+"),
                    teacher = teachers.joinToString(", ")
                )
            }
        }
        .sortedWith(compareBy({ it.date }, { it.number }, { it.startTime }))
}
