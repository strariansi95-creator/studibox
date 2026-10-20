package com.studibox.app.ui.common

import com.studibox.app.data.local.LessonEntity
import com.studibox.app.util.DateUtils
import java.time.LocalTime

/** Визуальное состояние карточки пары. Не хранится нигде — вычисляется на лету. */
enum class LessonUiState { CURRENT, NEXT, PAST, UPCOMING }

/**
 * Универсальная версия классификации по времени: подходит и для занятий
 * студента (LessonEntity), и для объединённых пар преподавателя
 * (TeacherLessonSlot) — обоим нужно одно и то же правило
 * startTime <= now < endTime, а не номер пары.
 */
private fun classifyByTime(
    items: List<Triple<String, String, String>>, // id, startTime, endTime
    isToday: Boolean,
    now: LocalTime
): Map<String, LessonUiState> {
    if (!isToday) return items.associate { it.first to LessonUiState.UPCOMING }

    val result = HashMap<String, LessonUiState>(items.size)
    var nextAssigned = false

    for ((id, startRaw, endRaw) in items) {
        val start = DateUtils.parseTime(startRaw)
        val end = DateUtils.parseTime(endRaw)

        result[id] = when {
            start != null && end != null && !now.isBefore(start) && now.isBefore(end) ->
                LessonUiState.CURRENT
            start != null && now.isBefore(start) && !nextAssigned -> {
                nextAssigned = true
                LessonUiState.NEXT
            }
            start != null && now.isBefore(start) -> LessonUiState.UPCOMING
            else -> LessonUiState.PAST
        }
    }
    return result
}

/**
 * Определяет состояние каждой пары СПИСКА ОДНОГО ДНЯ студента.
 *
 * «Сейчас» и «Следующая» имеют смысл только для сегодняшнего дня и вычисляются
 * строго по реальному времени телефона: startTime <= now < endTime — а не по
 * номеру пары, как и требуется.
 */
fun classifyLessons(
    lessons: List<LessonEntity>,
    isToday: Boolean,
    now: LocalTime = LocalTime.now()
): Map<String, LessonUiState> =
    classifyByTime(lessons.map { Triple(it.id, it.startTime, it.endTime) }, isToday, now)

/** То же самое, но для уже объединённых по группам пар преподавателя. */
fun classifyTeacherSlots(
    slots: List<TeacherLessonSlot>,
    isToday: Boolean,
    now: LocalTime = LocalTime.now()
): Map<String, LessonUiState> =
    classifyByTime(slots.map { Triple(it.id, it.startTime, it.endTime) }, isToday, now)

/**
 * Прогресс текущей пары от 0f до 1f (сколько времени уже прошло) и сколько
 * целых минут осталось до конца. null, если время начала/конца не удалось
 * разобрать, или сейчас не идёт эта пара (используется только для карточки
 * состояния CURRENT — вызывающий код сам решает, когда это уместно).
 */
data class LessonProgress(val fraction: Float, val minutesLeft: Int)

fun lessonProgress(
    startTime: String,
    endTime: String,
    now: LocalTime = LocalTime.now()
): LessonProgress? {
    val start = DateUtils.parseTime(startTime) ?: return null
    val end = DateUtils.parseTime(endTime) ?: return null

    val totalMinutes = (end.toSecondOfDay() - start.toSecondOfDay()) / 60
    if (totalMinutes <= 0) return null

    val passedMinutes = (now.toSecondOfDay() - start.toSecondOfDay()) / 60
    val fraction = (passedMinutes.toFloat() / totalMinutes).coerceIn(0f, 1f)
    val minutesLeft = (totalMinutes - passedMinutes).coerceIn(0, totalMinutes)

    return LessonProgress(fraction, minutesLeft)
}

/**
 * Перемена между двумя соседними парами одного дня: чем заканчивается
 * предыдущее занятие, чем начинается следующее и сколько минут между ними.
 * Используется на экране уведомлений (п. 3 задачи — «информация о
 * переменах»).
 */
data class BreakInfo(
    val afterSubject: String,
    val beforeSubject: String,
    val breakStart: String,
    val breakEnd: String,
    val minutes: Int
)

/**
 * Универсальный расчёт перемен по уже отсортированному списку пар одного
 * дня. Пары с нераспознаваемым временем или нулевой/отрицательной
 * перемены пропускаются — показывать «перемену минус 5 минут» из-за
 * опечатки во времени хуже, чем просто не показать её вообще.
 */
private fun computeBreaksByTime(
    items: List<Triple<String, String, String>> // subject, startTime, endTime
): List<BreakInfo> {
    val withTimes = items.mapNotNull { (subject, startRaw, endRaw) ->
        val start = DateUtils.parseTime(startRaw) ?: return@mapNotNull null
        val end = DateUtils.parseTime(endRaw) ?: return@mapNotNull null
        Triple(subject, start, end)
    }.sortedBy { it.second }

    val result = ArrayList<BreakInfo>(maxOf(withTimes.size - 1, 0))
    for (i in 0 until withTimes.size - 1) {
        val (afterSubject, _, afterEnd) = withTimes[i]
        val (beforeSubject, beforeStart, _) = withTimes[i + 1]
        val minutes = (beforeStart.toSecondOfDay() - afterEnd.toSecondOfDay()) / 60
        if (minutes <= 0) continue
        result += BreakInfo(
            afterSubject = afterSubject,
            beforeSubject = beforeSubject,
            breakStart = DateUtils.normalizeTime(afterEnd.toString()),
            breakEnd = DateUtils.normalizeTime(beforeStart.toString()),
            minutes = minutes
        )
    }
    return result
}

/** Перемены в расписании студента за один день. */
fun computeBreaks(lessons: List<LessonEntity>): List<BreakInfo> =
    computeBreaksByTime(lessons.map { Triple(it.subject.ifBlank { "Занятие" }, it.startTime, it.endTime) })

/** То же самое для объединённых пар преподавателя. */
fun computeBreaksForTeacher(slots: List<TeacherLessonSlot>): List<BreakInfo> =
    computeBreaksByTime(slots.map { Triple(it.subject.ifBlank { "Занятие" }, it.startTime, it.endTime) })
