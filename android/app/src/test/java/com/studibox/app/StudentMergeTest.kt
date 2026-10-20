package com.studibox.app

import com.studibox.app.data.local.LessonEntity
import com.studibox.app.ui.common.mergeTeachersForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentMergeTest {

    private fun lesson(
        id: String,
        teacher: String,
        subgroup: String = "",
        subject: String = "Математика",
        start: String = "09:00",
        end: String = "10:30",
        classroom: String = "305"
    ) = LessonEntity(
        id = id,
        groupName = "КИТ-ИСИП-24-1",
        date = "2026-09-18",
        dayOfWeek = 5,
        number = 1,
        startTime = start,
        endTime = end,
        subject = subject,
        teacher = teacher,
        classroom = classroom,
        weekType = "",
        subgroup = subgroup
    )

    @Test
    fun twoTeachersFromOneGluedCellMergeIntoOneCard() {
        // Ровно ситуация из отчёта: одна ячейка на два ФИО, ApiMapper развёл
        // их на две отдельные записи (для режима преподавателя), а студенту
        // нужна ОДНА карточка с обоими преподавателями.
        val lessons = listOf(
            lesson(id = "a|t0", teacher = "Андреева А.С."),
            lesson(id = "a|t1", teacher = "Степанова К.Н.")
        )

        val merged = mergeTeachersForDisplay(lessons)

        assertEquals(1, merged.size)
        assertEquals("Андреева А.С., Степанова К.Н.", merged[0].teacher)
    }

    @Test
    fun lessonsWithDifferentSubgroupsDoNotMerge() {
        // Разные подгруппы одного и того же времени/предмета — это разные
        // пары для студента (у него выбрана конкретная подгруппа), сливать
        // их в одну карточку с двумя учителями было бы неверно.
        val lessons = listOf(
            lesson(id = "a", teacher = "Андреева А.С.", subgroup = "1"),
            lesson(id = "b", teacher = "Степанов К.Н.", subgroup = "2")
        )

        val merged = mergeTeachersForDisplay(lessons)

        assertEquals(2, merged.size)
    }

    @Test
    fun differentSubjectsAtSameTimeDoNotMerge() {
        val lessons = listOf(
            lesson(id = "a", teacher = "Андреева А.С.", subject = "Физика"),
            lesson(id = "b", teacher = "Степанов К.Н.", subject = "Химия")
        )

        val merged = mergeTeachersForDisplay(lessons)

        assertEquals(2, merged.size)
    }

    @Test
    fun singleLessonIsReturnedUnchanged() {
        val original = lesson(id = "a", teacher = "Андреева А.С.")
        val merged = mergeTeachersForDisplay(listOf(original))

        assertEquals(1, merged.size)
        assertEquals(original, merged[0])
    }

    @Test
    fun emptyListStaysEmpty() {
        assertTrue(mergeTeachersForDisplay(emptyList()).isEmpty())
    }

    @Test
    fun mergedIdIsStableRegardlessOfInputOrder() {
        val forward = mergeTeachersForDisplay(
            listOf(lesson(id = "a", teacher = "Андреева А.С."), lesson(id = "b", teacher = "Степанов К.Н."))
        )
        val backward = mergeTeachersForDisplay(
            listOf(lesson(id = "b", teacher = "Степанов К.Н."), lesson(id = "a", teacher = "Андреева А.С."))
        )

        assertEquals(forward[0].id, backward[0].id)
    }

    @Test
    fun threeTeachersOnOneSlotAllMergeTogether() {
        val lessons = listOf(
            lesson(id = "a", teacher = "Иванов И.И."),
            lesson(id = "b", teacher = "Петров П.П."),
            lesson(id = "c", teacher = "Сидоров С.С.")
        )

        val merged = mergeTeachersForDisplay(lessons)

        assertEquals(1, merged.size)
        assertEquals("Иванов И.И., Петров П.П., Сидоров С.С.", merged[0].teacher)
    }
}
