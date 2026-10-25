package com.studibox.app.ui.common

import com.studibox.app.data.local.LessonEntity
import com.studibox.app.util.SubgroupFormat

/**
 * Одно время пары у студента. Обычно в нём одно занятие; если группа в это
 * время поделена на подгруппы (у 1-й одна пара, у 2-й другая) — несколько,
 * и они показываются ОДНОЙ карточкой с частями «1 подгруппа» / «2 подгруппа»,
 * а не двумя карточками с одинаковым временем подряд.
 */
data class StudentSlot(
    /** Стабильный ключ для LazyColumn — из id всех занятий слота. */
    val id: String,
    val lessons: List<LessonEntity>
) {
    val first: LessonEntity get() = lessons.first()
    val isSplit: Boolean get() = lessons.size > 1
}

private data class SlotTime(val date: String, val number: Int, val startTime: String, val endTime: String)

/**
 * Объединяет одновременные занятия разных подгрупп. Если у одновременных
 * занятий подгруппа не указана, это не деление на подгруппы, а что-то иное
 * (например, ошибка в исходной таблице) — такие занятия остаются отдельными.
 */
fun groupIntoSlots(lessons: List<LessonEntity>): List<StudentSlot> =
    lessons
        .groupBy { SlotTime(it.date, it.number, it.startTime, it.endTime) }
        .flatMap { (_, group) ->
            val bySubgroup = group.size > 1 && group.all { it.subgroup.isNotBlank() }
            if (bySubgroup) {
                val sorted = group.sortedBy { SubgroupFormat.shortLabel(it.subgroup) }
                listOf(StudentSlot(id = sorted.joinToString("+") { it.id }, lessons = sorted))
            } else {
                group.map { StudentSlot(id = it.id, lessons = listOf(it)) }
            }
        }
        .sortedWith(compareBy({ it.first.date }, { it.first.number }, { it.first.startTime }))
