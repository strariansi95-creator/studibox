package com.studibox.app

import com.studibox.app.data.local.LessonEntity
import com.studibox.app.data.local.TeacherSubgroupRow
import com.studibox.app.ui.common.aggregateForTeacher
import com.studibox.app.ui.common.aggregateTeacherNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherAggregationTest {

    private fun lesson(
        group: String,
        date: String = "2026-09-18",
        start: String = "10:40",
        end: String = "12:15",
        subject: String = "Математика",
        teacher: String = "Иванов И.И.",
        classroom: String = "",
        subgroup: String = ""
    ) = LessonEntity(
        id = "$group|$date|$start|$subgroup|$teacher",
        groupName = group,
        date = date,
        dayOfWeek = 5,
        number = 2,
        startTime = start,
        endTime = end,
        subject = subject,
        teacher = teacher,
        classroom = classroom,
        weekType = "",
        subgroup = subgroup
    )

    @Test
    fun sameLessonForMultipleGroupsBecomesOneSlot() {
        val lessons = listOf(
            lesson(group = "КИТ-ИСИП-24-1", classroom = "305"),
            lesson(group = "КИТ-ИСИП-24-2", classroom = "305")
        )

        val slots = aggregateForTeacher(lessons)

        assertEquals("Одна и та же пара не должна превращаться в две карточки", 1, slots.size)
        val slot = slots[0]
        assertEquals(2, slot.groups.size)
        assertTrue(slot.groups.any { it.group == "КИТ-ИСИП-24-1" })
        assertTrue(slot.groups.any { it.group == "КИТ-ИСИП-24-2" })
    }

    @Test
    fun differentTimesStayAsSeparateSlots() {
        val lessons = listOf(
            lesson(group = "КИТ-ИСИП-24-1", start = "08:30", end = "10:00", subject = "Физика"),
            lesson(group = "КИТ-ИСИП-24-1", start = "10:40", end = "12:15", subject = "Химия")
        )

        val slots = aggregateForTeacher(lessons)

        assertEquals(2, slots.size)
    }

    @Test
    fun differentSubjectsAtSameTimeStayAsSeparateSlots() {
        // Тот же слот времени, но разный предмет — это разные пары, не объединяем.
        val lessons = listOf(
            lesson(group = "КИТ-ИСИП-24-1", subject = "Математика"),
            lesson(group = "КИТ-ИСИП-25-1", subject = "Физика")
        )

        val slots = aggregateForTeacher(lessons)

        assertEquals(2, slots.size)
    }

    @Test
    fun groupsAreSortedAndDeduplicated() {
        val lessons = listOf(
            lesson(group = "Б"),
            lesson(group = "А"),
            lesson(group = "А") // дубль той же группы не должен размножить список
        )

        val slots = aggregateForTeacher(lessons)

        assertEquals(1, slots.size)
        assertEquals(listOf("А", "Б"), slots[0].groups.map { it.group })
    }

    // --------------------------------------------------- подгруппа = свойство пары

    @Test
    fun subgroupIsAttachedToTheSlotNotMergedAway() {
        val lessons = listOf(
            lesson(group = "КИТ-ИСИП-24-1", subgroup = "1", subject = "Лабораторная №1")
        )

        val slots = aggregateForTeacher(lessons)

        assertEquals(1, slots.size)
        assertEquals("1", slots[0].subgroup)
    }

    @Test
    fun sameTeacherDifferentSubgroupsAtSameTimeStayAsSeparatePairs() {
        // Абрамова ведёт лабораторную и для 1, и для 2 подгруппы в одно и то
        // же время у одной группы — это ДВЕ разные пары, обе должны остаться
        // видны, а не схлопнуться в одну с потерей второй подгруппы.
        val lessons = listOf(
            lesson(group = "КИТ-ИСИП-24-1", subgroup = "1", teacher = "Абрамова Т.Г."),
            lesson(group = "КИТ-ИСИП-24-1", subgroup = "2", teacher = "Абрамова Т.Г.")
        )

        val slots = aggregateForTeacher(lessons)

        assertEquals(2, slots.size)
        assertEquals(setOf("1", "2"), slots.map { it.subgroup }.toSet())
    }

    @Test
    fun lessonWithoutSubgroupHasBlankSubgroupField() {
        // Если подгруппа не указана в исходных данных — не выдумываем её.
        val slots = aggregateForTeacher(listOf(lesson(group = "А", subgroup = "")))
        assertEquals("", slots[0].subgroup)
    }

    @Test
    fun andreevaAndStepanovOnDifferentSubgroupsOfSameLabStayTwoDistinctSlots() {
        // Ровно сценарий из отчёта: одна и та же лабораторная, но разные
        // подгруппы у разных преподавателей — уже разведено на уровне
        // ApiMapper/TeacherNameFormat, здесь же подтверждаем, что фильтрация
        // по преподавателю с последующей агрегацией не путает эти записи.
        val allLessons = listOf(
            lesson(group = "А", subgroup = "1", teacher = "Андреева А.С.", subject = "Лабораторная №1"),
            lesson(group = "А", subgroup = "2", teacher = "Степанов К.Н.", subject = "Лабораторная №1")
        )

        val andreevaSlots = aggregateForTeacher(allLessons.filter { it.teacher == "Андреева А.С." })
        val stepanovSlots = aggregateForTeacher(allLessons.filter { it.teacher == "Степанов К.Н." })

        assertEquals(1, andreevaSlots.size)
        assertEquals("1", andreevaSlots[0].subgroup)

        assertEquals(1, stepanovSlots.size)
        assertEquals("2", stepanovSlots[0].subgroup)
    }

    // --------------------------------------------------- aggregateTeacherNames

    @Test
    fun teacherListContainsOnlyPlainNamesWithoutSubgroup() {
        val rows = listOf(
            TeacherSubgroupRow("Абрамов А.Х.", "1"),
            TeacherSubgroupRow("Абрамов А.Х.", "2"),
            TeacherSubgroupRow("Андреева А.С.", ""),
            TeacherSubgroupRow("Степанов К.Н.", "2")
        )

        val names = aggregateTeacherNames(rows)

        assertEquals(listOf("Абрамов А.Х.", "Андреева А.С.", "Степанов К.Н."), names)
    }

    @Test
    fun blankTeacherIsExcludedFromTheList() {
        val names = aggregateTeacherNames(listOf(TeacherSubgroupRow("", "1")))
        assertEquals(emptyList<String>(), names)
    }
}
