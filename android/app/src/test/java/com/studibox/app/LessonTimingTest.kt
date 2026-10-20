package com.studibox.app

import com.studibox.app.data.local.LessonEntity
import com.studibox.app.ui.common.LessonUiState
import com.studibox.app.ui.common.classifyLessons
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class LessonTimingTest {

    private fun lesson(id: String, start: String, end: String) = LessonEntity(
        id = id,
        groupName = "X",
        date = "2026-09-18",
        dayOfWeek = 5,
        number = 0,
        startTime = start,
        endTime = end,
        subject = "Тест",
        teacher = "",
        classroom = "",
        weekType = "",
        subgroup = ""
    )

    @Test
    fun currentLessonIsDeterminedByRealTimeNotNumber() {
        val lessons = listOf(
            lesson("1", "08:30", "10:00"),
            lesson("2", "10:10", "11:40"),
            lesson("3", "14:00", "15:35")
        )
        // Ровно во время второй пары.
        val states = classifyLessons(lessons, isToday = true, now = LocalTime.of(11, 0))

        assertEquals(LessonUiState.PAST, states["1"])
        assertEquals(LessonUiState.CURRENT, states["2"])
        assertEquals(LessonUiState.NEXT, states["3"])
    }

    @Test
    fun beforeFirstLessonEverythingIsUpcomingAndFirstIsNext() {
        val lessons = listOf(
            lesson("1", "08:30", "10:00"),
            lesson("2", "10:10", "11:40")
        )
        val states = classifyLessons(lessons, isToday = true, now = LocalTime.of(7, 0))

        assertEquals(LessonUiState.NEXT, states["1"])
        assertEquals(LessonUiState.UPCOMING, states["2"])
    }

    @Test
    fun afterLastLessonEverythingIsPast() {
        val lessons = listOf(lesson("1", "08:30", "10:00"))
        val states = classifyLessons(lessons, isToday = true, now = LocalTime.of(20, 0))
        assertEquals(LessonUiState.PAST, states["1"])
    }

    @Test
    fun notTodayNeverGetsCurrentOrNext() {
        val lessons = listOf(lesson("1", "08:30", "10:00"))
        // Даже если бы время совпало — для другого дня подсветки быть не должно.
        val states = classifyLessons(lessons, isToday = false, now = LocalTime.of(9, 0))
        assertEquals(LessonUiState.UPCOMING, states["1"])
    }
}
