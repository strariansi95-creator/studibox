package com.studibox.app

import com.studibox.app.data.local.LessonEntity
import com.studibox.app.ui.common.groupIntoSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentSlotsTest {

    private fun lesson(
        id: String,
        number: Int,
        start: String,
        end: String,
        subject: String,
        subgroup: String = ""
    ) = LessonEntity(
        id = id,
        groupName = "КИТ-ИСИП-24-1",
        date = "2026-10-26",
        dayOfWeek = 1,
        number = number,
        startTime = start,
        endTime = end,
        subject = subject,
        teacher = "",
        classroom = "",
        weekType = "",
        subgroup = subgroup
    )

    @Test
    fun subgroupsAtSameTimeBecomeOneSlot() {
        val slots = groupIntoSlots(
            listOf(
                lesson("b", 5, "15:50", "17:25", "Тестирование", "2"),
                lesson("a", 5, "15:50", "17:25", "Разработка ПО", "1"),
                lesson("c", 6, "17:40", "19:15", "Разработка ПО", "1")
            )
        )
        assertEquals(2, slots.size)
        assertTrue(slots[0].isSplit)
        assertEquals(listOf("1", "2"), slots[0].lessons.map { it.subgroup })
        assertFalse(slots[1].isSplit)
    }

    @Test
    fun simultaneousLessonsWithoutSubgroupStaySeparate() {
        val slots = groupIntoSlots(
            listOf(
                lesson("a", 1, "08:00", "09:35", "Математика"),
                lesson("b", 1, "08:00", "09:35", "Физика")
            )
        )
        assertEquals(2, slots.size)
        assertTrue(slots.none { it.isSplit })
    }

    @Test
    fun slotIdIsStableRegardlessOfInputOrder() {
        val one = lesson("a", 5, "15:50", "17:25", "X", "1")
        val two = lesson("b", 5, "15:50", "17:25", "Y", "2")
        assertEquals(groupIntoSlots(listOf(one, two))[0].id, groupIntoSlots(listOf(two, one))[0].id)
    }
}
