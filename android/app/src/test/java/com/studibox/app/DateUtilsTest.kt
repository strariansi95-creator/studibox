package com.studibox.app

import com.studibox.app.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Проверка ключевого правила: день недели вычисляется из даты,
 * а не из текста заголовка XLSX.
 */
class DateUtilsTest {

    @Test
    fun weekDaysAreComputedFromDate() {
        assertEquals(1, DateUtils.dayOfWeek(LocalDate.of(2026, 9, 14))) // понедельник
        assertEquals(2, DateUtils.dayOfWeek(LocalDate.of(2026, 9, 15))) // вторник
        assertEquals(3, DateUtils.dayOfWeek(LocalDate.of(2026, 9, 16))) // среда
        assertEquals(4, DateUtils.dayOfWeek(LocalDate.of(2026, 9, 17))) // четверг
        assertEquals(5, DateUtils.dayOfWeek(LocalDate.of(2026, 9, 18))) // пятница
        assertEquals(6, DateUtils.dayOfWeek(LocalDate.of(2026, 9, 19))) // суббота
        assertEquals(7, DateUtils.dayOfWeek(LocalDate.of(2026, 9, 20))) // воскресенье
    }

    @Test
    fun sheetNameDdmmyyIsParsed() {
        assertEquals(LocalDate.of(2026, 9, 14), DateUtils.parseDate("140926"))
        assertEquals(LocalDate.of(2026, 9, 16), DateUtils.parseDate("160926"))
        // Ошибочное имя листа 170626 разбирается как 17.06.2026 —
        // приоритет всегда у даты внутри файла, имя листа только fallback.
        assertEquals(LocalDate.of(2026, 6, 17), DateUtils.parseDate("170626"))
    }

    @Test
    fun wednesdayStaysWednesdayEvenIfHeaderSaysTuesday() {
        // В XLSX лист 160926 подписан «Вторник», но 16.09.2026 — среда.
        val date = DateUtils.parseDate("2026-09-16")!!
        assertEquals(3, DateUtils.dayOfWeek(date))
        assertEquals("Среда", DateUtils.dayFull(DateUtils.dayOfWeek(date)))
    }

    @Test
    fun variousDateFormatsAreParsed() {
        assertEquals(LocalDate.of(2026, 9, 17), DateUtils.parseDate("2026-09-17"))
        assertEquals(LocalDate.of(2026, 9, 17), DateUtils.parseDate("2026-09-17T08:30:00Z"))
        assertEquals(LocalDate.of(2026, 9, 17), DateUtils.parseDate("17.09.2026"))
        assertEquals(LocalDate.of(2026, 9, 17), DateUtils.parseDate("17/09/2026"))
        assertNull(DateUtils.parseDate("не дата"))
        assertNull(DateUtils.parseDate(null))
    }

    @Test
    fun weekStartIsMonday() {
        assertEquals(LocalDate.of(2026, 9, 14), DateUtils.weekStart(LocalDate.of(2026, 9, 16)))
        assertEquals(LocalDate.of(2026, 9, 14), DateUtils.weekStart(LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun dateOfWeekDayMatchesExpectedDates() {
        val anchor = LocalDate.of(2026, 9, 16)
        assertEquals(LocalDate.of(2026, 9, 14), DateUtils.dateOfWeekDay(anchor, 1))
        assertEquals(LocalDate.of(2026, 9, 15), DateUtils.dateOfWeekDay(anchor, 2))
        assertEquals(LocalDate.of(2026, 9, 16), DateUtils.dateOfWeekDay(anchor, 3))
        assertEquals(LocalDate.of(2026, 9, 17), DateUtils.dateOfWeekDay(anchor, 4))
        assertEquals(LocalDate.of(2026, 9, 18), DateUtils.dateOfWeekDay(anchor, 5))
        assertEquals(LocalDate.of(2026, 9, 19), DateUtils.dateOfWeekDay(anchor, 6))
    }

    @Test
    fun timeIsNormalized() {
        assertEquals("08:30", DateUtils.normalizeTime("8:30"))
        assertEquals("10:00", DateUtils.normalizeTime("10:00:00"))
        assertEquals("", DateUtils.normalizeTime("нет"))
    }
}
