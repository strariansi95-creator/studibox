package com.studibox.app

import com.studibox.app.ui.common.lessonProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class LessonProgressTest {

    @Test
    fun halfwayThroughGivesFiftyPercentAndHalfMinutesLeft() {
        val progress = lessonProgress("10:00", "12:00", now = LocalTime.of(11, 0))!!
        assertEquals(0.5f, progress.fraction, 0.001f)
        assertEquals(60, progress.minutesLeft)
    }

    @Test
    fun rightAtStartIsZeroPercent() {
        val progress = lessonProgress("10:00", "11:30", now = LocalTime.of(10, 0))!!
        assertEquals(0f, progress.fraction, 0.001f)
        assertEquals(90, progress.minutesLeft)
    }

    @Test
    fun afterEndIsClampedToFullProgress() {
        val progress = lessonProgress("10:00", "11:00", now = LocalTime.of(12, 0))!!
        assertEquals(1f, progress.fraction, 0.001f)
        assertEquals(0, progress.minutesLeft)
    }

    @Test
    fun invalidTimesReturnNull() {
        assertNull(lessonProgress("", "11:00"))
        assertNull(lessonProgress("10:00", ""))
        assertNull(lessonProgress("нет", "тоже нет"))
    }
}
