package com.studibox.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Единая точка работы с датами.
 *
 * Главное правило проекта: день недели НИКОГДА не берётся из текста
 * ("Вторник", "Среда" и т.п.), а всегда вычисляется математически из ISO-даты.
 */
object DateUtils {

    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val RU_MONTHS_GEN = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    private val DAY_FULL = arrayOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    private val DAY_SHORT = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    /** Учебные дни, отображаемые в селекторе расписания: ПН..СБ. */
    val STUDY_DAYS: List<Int> = listOf(1, 2, 3, 4, 5, 6)

    /**
     * Разбор даты из произвольного строкового представления.
     * Поддерживаются: 2026-09-16, 2026-09-16T00:00:00Z, 16.09.2026, 16/09/2026,
     * 16-09-2026, а также DDMMYY (160926) — как fallback из имени листа XLSX.
     */
    fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null

        // ISO, возможно с временем
        val isoPart = s.substringBefore('T').trim()
        runCatching { return LocalDate.parse(isoPart, ISO) }

        // DD.MM.YYYY / DD/MM/YYYY / DD-MM-YYYY
        val m = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$""").find(isoPart)
        if (m != null) {
            val (d, mo, y) = m.destructured
            runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
        }

        // YYYY.MM.DD
        val m2 = Regex("""^(\d{4})[./-](\d{1,2})[./-](\d{1,2})$""").find(isoPart)
        if (m2 != null) {
            val (y, mo, d) = m2.destructured
            runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
        }

        // DDMMYY (имя листа XLSX, например 160926)
        if (isoPart.length == 6 && isoPart.all { it.isDigit() }) {
            val d = isoPart.substring(0, 2).toInt()
            val mo = isoPart.substring(2, 4).toInt()
            val y = 2000 + isoPart.substring(4, 6).toInt()
            runCatching { return LocalDate.of(y, mo, d) }
        }

        return null
    }

    /** 1 = понедельник ... 7 = воскресенье. Вычисляется только из даты. */
    fun dayOfWeek(date: LocalDate): Int = date.dayOfWeek.value

    fun dayOfWeek(isoDate: String): Int? = parseDate(isoDate)?.let { dayOfWeek(it) }

    fun iso(date: LocalDate): String = date.format(ISO)

    /** Понедельник недели, в которую попадает дата. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(DayOfWeek.MONDAY).let { if (it.isAfter(date)) it.minusWeeks(1) else it }

    /** Дата конкретного дня недели (1..7) внутри недели, содержащей [anchor]. */
    fun dateOfWeekDay(anchor: LocalDate, dayOfWeek: Int): LocalDate =
        weekStart(anchor).plusDays((dayOfWeek - 1).toLong())

    fun dayFull(dayOfWeek: Int): String =
        DAY_FULL.getOrElse(dayOfWeek - 1) { "" }

    /**
     * Русское название дня недели ("Понедельник", можно с любым регистром
     * и пробелами по краям) -> номер 1..7. Нужна для ответов API вида
     * {"days":[{"day":"Понедельник","lessons":[...]}]}, где это название —
     * единственный НАДЁЖНЫЙ признак дня недели: поле date у самих занятий
     * в таком формате может быть недостоверным (см. ApiMapper.lessons).
     */
    fun dayOfWeekFromRussianName(name: String?): Int? {
        val trimmed = name?.trim()?.lowercase(Locale("ru")) ?: return null
        return DAY_FULL.indexOfFirst { it.lowercase(Locale("ru")) == trimmed }
            .takeIf { it >= 0 }
            ?.plus(1)
    }

    fun dayShort(dayOfWeek: Int): String =
        DAY_SHORT.getOrElse(dayOfWeek - 1) { "" }

    /** "17 сентября 2026" */
    fun humanDate(date: LocalDate): String =
        "${date.dayOfMonth} ${RU_MONTHS_GEN[date.monthValue - 1]} ${date.year}"

    /** "17 сентября 2026, четверг" */
    fun humanDateWithDay(date: LocalDate): String =
        "${humanDate(date)}, ${dayFull(dayOfWeek(date)).lowercase(Locale.ROOT)}"

    /** "8:30", "08:30", "8.30", "08:30:00" -> LocalTime. */
    fun parseTime(raw: String?): LocalTime? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val m = Regex("""(\d{1,2})[:.\-](\d{2})""").find(s) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }

    /** Нормализует время к виду HH:mm, либо возвращает пустую строку. */
    fun normalizeTime(raw: String?): String =
        parseTime(raw)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
}
