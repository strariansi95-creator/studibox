package com.studibox.app.data.remote

import android.util.Log
import com.studibox.app.data.local.GroupEntity
import com.studibox.app.data.local.LessonEntity
import com.studibox.app.util.DateSequenceCorrector
import com.studibox.app.util.DateUtils
import com.studibox.app.util.TeacherNameFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * Разбор ответов API Studibox.
 *
 * Фактический формат расписания:
 *
 * {
 *   "group": "КИТ-ИСИП-24-1",
 *   "updatedAt": "...",
 *   "version": 30,
 *   "days": [
 *     {
 *       "date": null,
 *       "day": "Четверг",
 *       "lessons": [
 *         {
 *           "id": 13921,
 *           "dayOfWeek": 4,
 *           "date": "2026-06-17",
 *           "timeStart": "11:40",
 *           "timeEnd": "13:15",
 *           "subject": "...",
 *           "teacher": "...",
 *           "room": "дист",
 *           "type": "Лекция",
 *           "subgroup": null
 *         }
 *       ]
 *     }
 *   ]
 * }
 *
 * День недели ВСЕГДА вычисляется из date.
 * Значение dayOfWeek от сервера используется только для диагностики.
 */
object ApiMapper {

    private const val TAG = NetworkModule.TAG

    // -------------------------------------------------------------------------
    // JSON utils
    // -------------------------------------------------------------------------

    private fun JsonElement.asStringOrNull(): String? {
        return when (this) {
            is JsonPrimitive -> {
                if (this is JsonNull) {
                    null
                } else {
                    content
                        .trim()
                        .takeIf { it.isNotEmpty() }
                }
            }

            else -> null
        }
    }

    private fun JsonObject.str(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]?.asStringOrNull()

            if (value != null && !value.equals("null", ignoreCase = true)) {
                return value
            }
        }

        return null
    }

    private fun JsonObject.int(vararg keys: String): Int? {
        for (key in keys) {
            val value = this[key]?.asStringOrNull() ?: continue

            val parsed = value.toIntOrNull()
                ?: Regex("""\d+""")
                    .find(value)
                    ?.value
                    ?.toIntOrNull()

            if (parsed != null) {
                return parsed
            }
        }

        return null
    }

    // -------------------------------------------------------------------------
    // Array extraction
    // -------------------------------------------------------------------------

    /**
     * Достаёт массив из различных вариантов ответа API.
     *
     * Поддерживаются:
     *
     * [
     *   {...}
     * ]
     *
     * {
     *   "data": [...]
     * }
     *
     * {
     *   "lessons": [...]
     * }
     *
     * {
     *   "days": [
     *      {
     *          "lessons": [...]
     *      }
     *   ]
     * }
     */
    private fun asArray(element: JsonElement): List<JsonElement> {
        return when (element) {

            is JsonArray -> {
                element.toList()
            }

            is JsonObject -> {

                // Обычные обёртки с массивом.
                val directKeys = listOf(
                    "data",
                    "items",
                    "lessons",
                    "schedule",
                    "result",
                    "rows",
                    "groups"
                )

                for (key in directKeys) {
                    val array = element[key] as? JsonArray

                    if (array != null) {
                        return array.toList()
                    }
                }

                // -------------------------------------------------------------
                // Главный формат Studibox:
                //
                // {
                //   "group": "...",
                //   "days": [
                //      {
                //          "date": null,
                //          "day": "Четверг",
                //          "lessons": [...]
                //      }
                //   ]
                // }
                //
                // Разворачиваем days -> lessons.
                // -------------------------------------------------------------

                val days = element["days"] as? JsonArray

                if (days != null) {
                    val result = ArrayList<JsonElement>()

                    for (dayElement in days) {
                        val dayObject = dayElement as? JsonObject
                            ?: continue

                        val lessons = dayObject["lessons"] as? JsonArray
                            ?: continue

                        // Название дня недели ("Понедельник" и т.п.) — в
                        // этом формате ответа это ЕДИНСТВЕННЫЙ надёжный
                        // признак того, какому дню недели принадлежит
                        // занятие. Поле date у самого занятия в реальных
                        // ответах оказалось недостоверным: например, у
                        // "Понедельника" и "Вторника" подряд идущие даты
                        // 05.09/06.09, а у "Четверга" той же недели —
                        // 03.09, РАНЬШЕ понедельника. Это похоже на шаблон
                        // "что бывает по вторникам" с произвольной
                        // исторической датой внутри, а не на фактическую
                        // дату занятия — поэтому дату всегда пересчитываем
                        // из названия дня недели (см. ApiMapper.lessons),
                        // а исходное date только сохраняем рядом для
                        // диагностики.
                        val dayName = dayObject.str("day", "dayName", "weekday")

                        for (lesson in lessons) {
                            if (lesson is JsonObject && dayName != null) {
                                result += JsonObject(
                                    lesson.toMutableMap().apply {
                                        put("_dayName", JsonPrimitive(dayName))
                                    }
                                )
                            } else {
                                result += lesson
                            }
                        }
                    }

                    Log.d(
                        TAG,
                        "Формат schedule: найдено days=${days.size}, " +
                            "развёрнуто lessons=${result.size}"
                    )

                    return result
                }

                // -------------------------------------------------------------
                // Формат:
                //
                // {
                //   "2026-09-16": [...],
                //   "2026-09-17": [...]
                // }
                //
                // Поддерживаем на всякий случай.
                // -------------------------------------------------------------

                if (element.values.any { it is JsonArray }) {
                    val result = ArrayList<JsonElement>()

                    for ((key, value) in element) {
                        val array = value as? JsonArray
                            ?: continue

                        for (item in array) {
                            result += injectDate(item, key)
                        }
                    }

                    return result
                }

                emptyList()
            }

            else -> emptyList()
        }
    }

    /**
     * Для формата:
     *
     * {
     *   "2026-09-16": [...]
     * }
     *
     * добавляет дату внутрь занятия.
     */
    private fun injectDate(
        item: JsonElement,
        dateKey: String
    ): JsonElement {

        if (item !is JsonObject) {
            return item
        }

        if (item.containsKey("date")) {
            return item
        }

        if (DateUtils.parseDate(dateKey) == null) {
            return item
        }

        return JsonObject(
            item.toMutableMap().apply {
                put("date", JsonPrimitive(dateKey))
            }
        )
    }

    // -------------------------------------------------------------------------
    // Groups
    // -------------------------------------------------------------------------

    fun groups(element: JsonElement): List<GroupEntity> {

        val items = asArray(element)

        val result = ArrayList<GroupEntity>(items.size)

        val seen = HashSet<String>()

        items.forEachIndexed { index, item ->

            val name: String?
            var serverId: Int? = null

            when (item) {

                is JsonObject -> {

                    name = item.str(
                        "name",
                        "group",
                        "group_name",
                        "groupName",
                        "title",
                        "value"
                    )

                    serverId = item.int(
                        "id",
                        "group_id",
                        "groupId"
                    )
                }

                is JsonPrimitive -> {
                    name = item.asStringOrNull()
                }

                else -> {
                    name = null
                }
            }

            if (!name.isNullOrBlank() && seen.add(name)) {

                result += GroupEntity(
                    name = name,
                    serverId = serverId,
                    sortOrder = index
                )
            }
        }

        Log.i(
            TAG,
            "Разобрано групп: ${result.size}"
        )

        return result
    }

    // -------------------------------------------------------------------------
    // Version
    // -------------------------------------------------------------------------

    data class VersionInfo(
        val version: String,
        val updatedAt: String?,
        val message: String?
    )

    fun version(element: JsonElement): VersionInfo {

        val obj = element as? JsonObject

        val version =
            obj?.str(
                "version",
                "scheduleVersion",
                "schedule_version",
                "hash",
                "etag"
            )
                ?: element.asStringOrNull()
                ?: ""

        return VersionInfo(
            version = version,
            updatedAt = obj?.str(
                "updatedAt",
                "updated_at",
                "time",
                "date"
            ),
            message = obj?.str(
                "message",
                "status"
            )
        )
    }

    // -------------------------------------------------------------------------
    // Schedule
    // -------------------------------------------------------------------------

    /**
     * Разбирает расписание выбранной группы.
     *
     * Главный источник истины для даты — поле lesson.date.
     *
     * dayOfWeek от сервера намеренно НЕ используется для определения дня.
     * Это важно, потому что в текущем backend-ответе встречаются записи,
     * где dayOfWeek не соответствует фактической дате.
     */
    fun lessons(
        element: JsonElement,
        group: String,
        fallbackAnchor: LocalDate = LocalDate.now()
    ): List<LessonEntity> {

        val items = asArray(element)

        Log.i(
            TAG,
            "Получен schedule для $group: элементов=${items.size}"
        )

        val result = ArrayList<LessonEntity>(items.size)

        val usedIds = HashSet<String>()

        var skippedNoDate = 0
        var skippedNoContent = 0
        var skippedInvalid = 0

        for ((index, item) in items.withIndex()) {

            val obj = item as? JsonObject

            if (obj == null) {
                skippedInvalid++

                Log.w(
                    TAG,
                    "Элемент #$index пропущен: это не JsonObject"
                )

                continue
            }

            // -----------------------------------------------------------------
            // DATE
            // -----------------------------------------------------------------

            val rawDate = obj.str(
                "date",
                "lesson_date",
                "lessonDate",
                "day_date",
                "dayDate",
                "dt"
            )

            // _dayName проставлен в asArray() из названия дня недели
            // ("Понедельник" и т.п.) в формате {days:[{day, lessons:[]}]}.
            // Это НАДЁЖНЕЕ, чем поле date у самого занятия: на реальных
            // ответах API оно недостоверно — например, у "Четверга" дата
            // оказывается РАНЬШЕ даты "Понедельника" той же недели, то
            // есть это не фактический календарный день, а что-то вроде
            // служебной метки из исходного шаблона. Поэтому для этого
            // формата дата всегда считается от текущей недели по названию
            // дня, а не берётся из rawDate — даже если rawDate успешно
            // парсится как валидная дата.
            val dayNameFromServer = obj.str("_dayName")
            val dowFromDayName = DateUtils.dayOfWeekFromRussianName(dayNameFromServer)

            var date: LocalDate? = if (dowFromDayName != null) {
                DateUtils.dateOfWeekDay(fallbackAnchor, dowFromDayName).also {
                    Log.d(
                        TAG,
                        "Элемент #$index: дата определена по дню недели " +
                            "\"$dayNameFromServer\" -> $it (raw date сервера " +
                            "'$rawDate' проигнорирован как ненадёжный)"
                    )
                }
            } else {
                DateUtils.parseDate(rawDate)
            }

            // Если даты нет, используем day_of_week только как запасной
            // вариант для старых/альтернативных форматов API.
            if (date == null) {

                val dow = obj.int(
                    "day_of_week",
                    "dayOfWeek",
                    "weekday",
                    "dow"
                )

                if (dow != null && dow in 1..7) {

                    date = DateUtils.dateOfWeekDay(
                        fallbackAnchor,
                        dow
                    )

                    Log.d(
                        TAG,
                        "Элемент #$index: дата отсутствует, " +
                            "восстановлена из dayOfWeek=$dow → $date"
                    )
                }
            }

            if (date == null) {

                skippedNoDate++

                Log.w(
                    TAG,
                    "Элемент #$index пропущен: нет корректной даты. " +
                        "rawDate='$rawDate', keys=${obj.keys}"
                )

                continue
            }

            // -----------------------------------------------------------------
            // DAY OF WEEK
            // -----------------------------------------------------------------

            // ВСЕГДА вычисляем из фактической даты.
            val dayOfWeek = DateUtils.dayOfWeek(date)

            // Значение сервера только проверяем и логируем.
            val serverDow = obj.int(
                "day_of_week",
                "dayOfWeek",
                "weekday",
                "dow"
            )

            if (serverDow != null && serverDow != dayOfWeek) {

                Log.w(
                    TAG,
                    "Расхождение dayOfWeek: " +
                        "сервер=$serverDow, " +
                        "вычислено=$dayOfWeek, " +
                        "date=${DateUtils.iso(date)}, " +
                        "group=$group"
                )
            }

            // -----------------------------------------------------------------
            // TIME
            // -----------------------------------------------------------------

            // Реальный API использует timeStart / timeEnd.
            // Оставляем также старые варианты.
            var start = obj.str(
                "timeStart",
                "start_time",
                "startTime",
                "time_start",
                "start",
                "begin",
                "from"
            )

            var end = obj.str(
                "timeEnd",
                "end_time",
                "endTime",
                "time_end",
                "end",
                "finish",
                "to"
            )

            // Дополнительная поддержка формата:
            // "08:30 — 10:00"
            val combined = obj.str(
                "time",
                "interval",
                "period"
            )

            if (
                (start == null || end == null) &&
                combined != null
            ) {

                val parts = combined
                    .split(
                        "—",
                        "-",
                        "–",
                        "до"
                    )
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (start == null) {
                    start = parts.getOrNull(0)
                }

                if (end == null) {
                    end = parts.getOrNull(1)
                }
            }

            // -----------------------------------------------------------------
            // LESSON NUMBER
            // -----------------------------------------------------------------

            val number =
                obj.int(
                    "lesson_number",
                    "lessonNumber",
                    "number",
                    "pair",
                    "num",
                    "index"
                )
                    ?: inferNumber(start)

            // -----------------------------------------------------------------
            // SUBJECT
            // -----------------------------------------------------------------

            val subject = obj.str(
                "subject",
                "discipline",
                "discipline_name",
                "disciplineName",
                "name",
                "title",
                "lesson",
                "subject_name",
                "subjectName"
            ).orEmpty()

            // -----------------------------------------------------------------
            // TEACHER
            // -----------------------------------------------------------------

            // ВАЖНО: в одной ячейке иногда реально лежит НЕСКОЛЬКО ФИО
            // (например, лабораторная разбита на подгруппы с разными
            // преподавателями). splitRawField() разводит их на отдельных
            // людей и нормализует каждого — см. TeacherNameFormat.kt.
            // Нельзя просто взять всё после первой фамилии как "инициалы":
            // именно так возникала склейка "Андреева А.С.С.Т.Е.П.А.Н.О.В.К.Н.".
            val rawTeacherField = obj.str(
                "teacher",
                "teacher_name",
                "teacherName",
                "teacher_fio",
                "teacherFio",
                "lecturer",
                "prepod",
                "tutor"
            ).orEmpty()

            val teacherNames = TeacherNameFormat.splitRawField(rawTeacherField)
                .ifEmpty { listOf("") }

            // -----------------------------------------------------------------
            // ROOM
            // -----------------------------------------------------------------

            // Реальный API использует "room".
            val classroom = obj.str(
                "room",
                "classroom",
                "auditorium",
                "cabinet",
                "aud",
                "class_room",
                "classRoom",
                "location"
            ).orEmpty()

            // -----------------------------------------------------------------
            // TYPE
            // -----------------------------------------------------------------

            // LessonEntity в текущем проекте не обязательно имеет поле type,
            // поэтому здесь значение читается для диагностики, но не требует
            // изменения структуры Room.
            val type = obj.str(
                "type",
                "lesson_type",
                "lessonType"
            ).orEmpty()

            // -----------------------------------------------------------------
            // WEEK / SUBGROUP
            // -----------------------------------------------------------------

            val weekType = obj.str(
                "week_type",
                "weekType",
                "parity",
                "week"
            ).orEmpty()

            val subgroup = obj.str(
                "subgroup",
                "sub_group",
                "subGroup",
                "podgruppa"
            ).orEmpty()

            // -----------------------------------------------------------------
            // CONTENT CHECK
            // -----------------------------------------------------------------

            if (
                subject.isBlank() &&
                rawTeacherField.isBlank() &&
                classroom.isBlank()
            ) {

                skippedNoContent++

                Log.w(
                    TAG,
                    "Элемент #$index пропущен: " +
                        "нет subject/teacher/room. " +
                        "date=${DateUtils.iso(date)}, " +
                        "time=$start-$end, " +
                        "type=$type, " +
                        "keys=${obj.keys}"
                )

                continue
            }

            if (teacherNames.size > 1) {
                Log.i(
                    TAG,
                    "В одной ячейке найдено несколько преподавателей: " +
                        "\"$rawTeacherField\" -> $teacherNames " +
                        "(group=$group, date=${DateUtils.iso(date)}, subject=$subject) " +
                        "— создаётся отдельная запись занятия на каждого"
                )
            }

            // -----------------------------------------------------------------
            // ID / ENTITY (по одной записи на каждого преподавателя из ячейки)
            // -----------------------------------------------------------------

            val iso = DateUtils.iso(date)

            for ((teacherIndex, teacher) in teacherNames.withIndex()) {

                var id = "$group|$iso|$number|$subgroup" +
                    if (teacherNames.size > 1) "|t$teacherIndex" else ""

                if (!usedIds.add(id)) {

                    // Если в одно время есть несколько занятий,
                    // различаем их по предмету.
                    id = "$group|$iso|$number|$subgroup|${subject.hashCode()}" +
                        if (teacherNames.size > 1) "|t$teacherIndex" else ""

                    if (!usedIds.add(id)) {
                        continue
                    }
                }

                result += LessonEntity(
                    id = id,
                    groupName = group,
                    date = iso,
                    dayOfWeek = dayOfWeek,
                    number = number,
                    startTime = DateUtils.normalizeTime(start),
                    endTime = DateUtils.normalizeTime(end),
                    subject = subject,
                    teacher = teacher,
                    classroom = classroom,
                    weekType = weekType,
                    subgroup = subgroup,
                    type = type
                )
            }
        }

        val sorted = result.sortedWith(
            compareBy(
                { it.date },
                { it.number },
                { it.startTime }
            )
        )

        Log.i(
            TAG,
            "Разобрано занятий для $group: ${sorted.size}; " +
                "пропущено: ${skippedNoDate + skippedNoContent + skippedInvalid} " +
                "(без даты=$skippedNoDate, " +
                "без содержания=$skippedNoContent, " +
                "неверный элемент=$skippedInvalid)"
        )

        // Коррекция опечаток в месяце (см. DateSequenceCorrector) — ПОСЛЕ
        // сортировки по исходной (возможно, неверной) дате, он сам
        // пересортировывать не обязан: порядок внутри одного дня не
        // зависит от того, к какому месяцу этот день относится.
        return DateSequenceCorrector.applyTo(sorted)
    }

    // -------------------------------------------------------------------------
    // Lesson number inference
    // -------------------------------------------------------------------------

    /**
     * Если номер пары отсутствует, пытаемся определить его по времени начала.
     */
    private fun inferNumber(start: String?): Int {

        val time = DateUtils.parseTime(start)
            ?: return 0

        val minutes =
            time.hour * 60 + time.minute

        val slots = listOf(
            8 * 60 + 30,
            10 * 60 + 10,
            12 * 60 + 20,
            14 * 60,
            15 * 60 + 40,
            17 * 60 + 20
        )

        val index = slots.indexOfFirst {
            minutes < it + 45
        }

        return if (index >= 0) {
            index + 1
        } else {
            slots.size + 1
        }
    }
}