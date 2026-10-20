package com.studibox.app

import com.studibox.app.data.remote.ApiMapper
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ApiMapperTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun groupsAreParsedFromRealApiShape() {
        val payload = """
            [{"id":23,"name":"КИТ-ВР-26"},{"id":12,"name":"КИТ-ИСИП-24-1"}]
        """.trimIndent()
        val groups = ApiMapper.groups(json.parseToJsonElement(payload))
        assertEquals(2, groups.size)
        assertEquals("КИТ-ВР-26", groups[0].name)
        assertEquals(23, groups[0].serverId)
    }

    @Test
    fun versionIsParsed() {
        val payload = """{"version":30,"updatedAt":"2026-09-17T07:00:08.408432+00:00","status":"ok"}"""
        val info = ApiMapper.version(json.parseToJsonElement(payload))
        assertEquals("30", info.version)
    }

    @Test
    fun serverDayOfWeekNeverOverridesTheDate() {
        // Сервер прислал day_of_week = 2 («Вторник») для 16.09.2026,
        // которое на самом деле среда. Победить должна дата.
        val payload = """
            [{"date":"2026-09-16","day_of_week":2,"lesson_number":1,
              "start_time":"08:30","end_time":"10:00",
              "subject":"Математика","teacher":"Иванов И.И.","classroom":"305"}]
        """.trimIndent()
        val lessons = ApiMapper.lessons(json.parseToJsonElement(payload), "КИТ-ИСИП-24-1")
        assertEquals(1, lessons.size)
        assertEquals("2026-09-16", lessons[0].date)
        assertEquals(3, lessons[0].dayOfWeek)
    }

    @Test
    fun alternativeFieldNamesAreSupported() {
        val payload = """
            [{"lessonDate":"17.09.2026","time":"10:10 — 11:40",
              "discipline":"Физика","lecturer":"Петров П.П.","room":"210"}]
        """.trimIndent()
        val lessons = ApiMapper.lessons(json.parseToJsonElement(payload), "КИТ-ТС-24")
        assertEquals(1, lessons.size)
        assertEquals("2026-09-17", lessons[0].date)
        assertEquals(4, lessons[0].dayOfWeek)
        assertEquals("10:10", lessons[0].startTime)
        assertEquals("11:40", lessons[0].endTime)
        assertEquals("Физика", lessons[0].subject)
    }

    @Test
    fun wrappedArrayIsSupported() {
        val payload = """{"data":[{"date":"2026-09-18","subject":"История","start_time":"12:20"}]}"""
        val lessons = ApiMapper.lessons(json.parseToJsonElement(payload), "КИТ-ТС-24")
        assertEquals(1, lessons.size)
        assertEquals(5, lessons[0].dayOfWeek)
    }

    @Test
    fun dateKeyedObjectIsSupported() {
        val payload = """
            {"2026-09-15":[{"subject":"Химия","start_time":"08:30"}],
             "2026-09-16":[{"subject":"Биология","start_time":"08:30"}]}
        """.trimIndent()
        val lessons = ApiMapper.lessons(json.parseToJsonElement(payload), "КИТ-ТС-24")
        assertEquals(2, lessons.size)
        val tuesday = lessons.first { it.date == "2026-09-15" }
        val wednesday = lessons.first { it.date == "2026-09-16" }
        assertEquals(2, tuesday.dayOfWeek)
        assertEquals(3, wednesday.dayOfWeek)
    }

    @Test
    fun lessonsWithoutDateFallBackToWeekday() {
        val payload = """[{"day_of_week":3,"subject":"Английский","start_time":"08:30"}]"""
        val anchor = LocalDate.of(2026, 9, 17)
        val lessons = ApiMapper.lessons(json.parseToJsonElement(payload), "КИТ-ТС-24", anchor)
        assertEquals("2026-09-16", lessons[0].date)
        assertEquals(3, lessons[0].dayOfWeek)
    }

    @Test
    fun realBackendShapeDaysLessonsIsParsed() {
        // Точный формат реального API: group/days[]/lessons[], timeStart/timeEnd/room/type.
        val payload = """
            {
              "group": "КИТ-ИСИП-24-1",
              "updatedAt": "2026-09-17T07:00:08.408432+00:00",
              "version": 30,
              "days": [
                {
                  "date": null,
                  "day": "Четверг",
                  "lessons": [
                    {
                      "id": 13921,
                      "dayOfWeek": 4,
                      "date": "2026-09-18",
                      "timeStart": "11:40",
                      "timeEnd": "13:15",
                      "subject": "Менеджмент в профессиональной деятельности",
                      "teacher": "Реснянская А.С.",
                      "room": "дист",
                      "type": "Лекция",
                      "subgroup": null
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val lessons = ApiMapper.lessons(json.parseToJsonElement(payload), "КИТ-ИСИП-24-1")

        assertEquals(1, lessons.size)
        val lesson = lessons[0]
        assertEquals("2026-09-18", lesson.date)
        // Сервер прислал dayOfWeek=4 (четверг), но 18.09.2026 — пятница (5).
        // Побеждает вычисление из даты, а не серверное значение.
        assertEquals(5, lesson.dayOfWeek)
        assertEquals("11:40", lesson.startTime)
        assertEquals("13:15", lesson.endTime)
        assertEquals("Менеджмент в профессиональной деятельности", lesson.subject)
        assertEquals("Реснянская А.С.", lesson.teacher)
        assertEquals("дист", lesson.classroom)
        assertEquals("Лекция", lesson.type)
    }

    @Test
    fun brokenPayloadDoesNotCrash() {
        assertTrue(ApiMapper.lessons(json.parseToJsonElement("[]"), "X").isEmpty())
        assertTrue(ApiMapper.lessons(json.parseToJsonElement("{}"), "X").isEmpty())
        assertTrue(ApiMapper.groups(json.parseToJsonElement("{}")).isEmpty())
    }
}
