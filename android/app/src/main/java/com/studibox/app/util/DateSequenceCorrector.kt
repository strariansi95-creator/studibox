package com.studibox.app.util

import android.util.Log
import com.studibox.app.data.local.LessonEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Иногда в исходном XLSX у одной даты опечатка в месяце — например,
 * "05.09.26" вместо настоящего "05.10.26" (человек по привычке напечатал
 * предыдущий месяц). Исправлять это нужно было бы при импорте на
 * backend, но backend обновляется автоматически и недоступен для прямого
 * редактирования — поэтому коррекция сделана здесь, единственном слое,
 * который реально можно собрать и установить.
 *
 * ГЛАВНОЕ ПРАВИЛО: дата считается подозрительной ТОЛЬКО если в реальном
 * расписании этой группы у неё нет соседа (±1 календарный день). Она
 * заменяется ТОЛЬКО когда есть РОВНО ОДНА правдоподобная альтернатива:
 * тот же день месяца и год, сдвинутые на ±1 месяц, которая встаёт рядом
 * с уже существующей датой (перестаёт быть "подозрительной" сама) и
 * лежит в разумном окне вокруг anchor. Если подходят оба варианта месяца
 * или ни один — ничего не меняется: лучше оставить странную, но нетронутую
 * дату, чем тихо подменить не ту. Никакого "если месяц anchor = 10, то
 * все даты — октябрь": чужие, легитимные месяцы (прошлые недели семестра)
 * вообще не рассматриваются, поскольку у них, как правило, есть соседи.
 *
 * [anchor] — "сегодня". ВСЕГДА локальная дата устройства по умолчанию,
 * не updatedAt с сервера — только как окно правдоподобия для кандидата,
 * а не как подмена понятия "сегодня" (см. требование задачи).
 */
object DateSequenceCorrector {

    private const val ANCHOR_WINDOW_DAYS = 45L
    private const val TAG = "StudiboxDates"

    /**
     * ОТКЛЮЧЕНО. Даты теперь проверяет и исправляет собственный backend
     * (correctDateSequence в index.ts) ещё при импорте, поэтому в приложение
     * приходят уже корректные даты, а эта клиентская догадка стала только
     * риском: дата, у которой нет соседей ±1 день (например, единственный
     * понедельник новой недели, пока остальные дни ещё не загружены),
     * выглядела "подозрительной" и могла быть сдвинута на месяц назад/вперёд,
     * если у того же числа соседнего месяца находились занятия, — понедельник
     * лежал в Supabase, но в приложении оказывался не на своём месте.
     * Класс оставлен, чтобы не менять вызов в ApiMapper.
     */
    @Suppress("UNUSED_PARAMETER")
    fun applyTo(lessons: List<LessonEntity>, anchor: LocalDate = LocalDate.now()): List<LessonEntity> = lessons

    @Suppress("unused")
    private fun applyToDisabled(lessons: List<LessonEntity>, anchor: LocalDate = LocalDate.now()): List<LessonEntity> {
        val rawDates = lessons.mapNotNull { DateUtils.parseDate(it.date) }.distinct()
        if (rawDates.size < 2) return lessons

        val dateSet = rawDates.toHashSet()
        val corrections = HashMap<LocalDate, LocalDate>()

        for (raw in rawDates) {
            // Далеко от текущего периода — не рассматриваем вообще, это
            // наверняка легитимная история прошлых недель.
            if (abs(ChronoUnit.DAYS.between(anchor, raw)) > ANCHOR_WINDOW_DAYS) continue

            val hasNeighbour = dateSet.contains(raw.minusDays(1)) || dateSet.contains(raw.plusDays(1))
            if (hasNeighbour) {
                Log.d(TAG, "RAW_DATE = $raw NORMALIZED_DATE = $raw REASON = valid date, has neighbour")
                continue
            }

            val plusCandidate = shiftMonthSameDay(raw, 1)
            val minusCandidate = shiftMonthSameDay(raw, -1)
            val plusFits = fitsAsReplacement(plusCandidate, dateSet, anchor)
            val minusFits = fitsAsReplacement(minusCandidate, dateSet, anchor)

            when {
                plusFits && !minusFits -> {
                    Log.i(
                        TAG,
                        "RAW_DATE = $raw CANDIDATE_DATE = $plusCandidate " +
                            "NORMALIZED_DATE = $plusCandidate UPDATED_AT(anchor) = $anchor " +
                            "REASON = isolated date, +1 month fits surrounding schedule"
                    )
                    corrections[raw] = plusCandidate!!
                }
                minusFits && !plusFits -> {
                    Log.i(
                        TAG,
                        "RAW_DATE = $raw CANDIDATE_DATE = $minusCandidate " +
                            "NORMALIZED_DATE = $minusCandidate UPDATED_AT(anchor) = $anchor " +
                            "REASON = isolated date, -1 month fits surrounding schedule"
                    )
                    corrections[raw] = minusCandidate!!
                }
                else -> {
                    Log.w(
                        TAG,
                        "RAW_DATE = $raw NORMALIZED_DATE = $raw " +
                            "REASON = isolated but no single confident replacement, kept as-is"
                    )
                }
            }
        }

        if (corrections.isEmpty()) return lessons

        Log.i(TAG, "Исправлено дат: ${corrections.size} из ${rawDates.size}")

        return lessons.map { lesson ->
            val original = DateUtils.parseDate(lesson.date) ?: return@map lesson
            val corrected = corrections[original] ?: return@map lesson
            val correctedIso = DateUtils.iso(corrected)

            // id собран как "group|iso|number|subgroup[...]" в ApiMapper —
            // дата всегда второй сегмент, поэтому можно точечно заменить
            // именно его, не трогая остальную (возможно, более сложную)
            // структуру id при коллизиях номера/предмета/преподавателя.
            val idParts = lesson.id.split("|").toMutableList()
            if (idParts.size > 1) idParts[1] = correctedIso

            lesson.copy(
                id = idParts.joinToString("|"),
                date = correctedIso,
                dayOfWeek = DateUtils.dayOfWeek(corrected)
            )
        }
    }

    private fun fitsAsReplacement(candidate: LocalDate?, dateSet: Set<LocalDate>, anchor: LocalDate): Boolean {
        if (candidate == null) return false
        if (dateSet.contains(candidate)) return false // уже есть такая дата — не трогаем, риск дубля
        if (abs(ChronoUnit.DAYS.between(anchor, candidate)) > ANCHOR_WINDOW_DAYS) return false
        return dateSet.contains(candidate.minusDays(1)) || dateSet.contains(candidate.plusDays(1))
    }

    /** Тот же день месяца, месяц/год сдвинуты на [months] с корректной обработкой границ года. */
    private fun shiftMonthSameDay(date: LocalDate, months: Long): LocalDate? {
        return try {
            val shiftedMonth = YearMonth.from(date).plusMonths(months)
            val day = minOf(date.dayOfMonth, shiftedMonth.lengthOfMonth())
            shiftedMonth.atDay(day)
        } catch (e: Exception) {
            null
        }
    }
}
