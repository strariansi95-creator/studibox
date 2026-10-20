package com.studibox.app

import com.studibox.app.data.repo.LmsRepository
import com.studibox.app.util.CourseMatcher
import com.studibox.app.util.TeacherNameFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseMatcherTest {

    private val courses = listOf(
        "Математический анализ (КИТ-ВР-26) 2025/2026",
        "Линейная алгебра и аналитическая геометрия",
        "Программирование на Python",
        "Физическая культура и спорт"
    )

    @Test
    fun matchesCourseWithGroupCodeAndYear() {
        assertEquals(courses[0], CourseMatcher.bestMatch("Математический анализ", courses) { it })
    }

    @Test
    fun matchesDespiteInflection() {
        assertEquals(courses[2], CourseMatcher.bestMatch("Основы программирования на Python", courses) { it })
    }

    @Test
    fun prefersMoreSpecificCourse() {
        val list = listOf("Алгебра и геометрия: практикум для студентов", "Алгебра и геометрия")
        assertEquals("Алгебра и геометрия", CourseMatcher.bestMatch("Алгебра и геометрия", list) { it })
    }

    @Test
    fun noMatchForUnrelatedSubject() {
        assertNull(CourseMatcher.bestMatch("История России", courses) { it })
    }

    @Test
    fun subjectKeyIgnoresCaseAndPunctuation() {
        assertEquals(
            CourseMatcher.subjectKey("Математический анализ"),
            CourseMatcher.subjectKey("математический  АНАЛИЗ.")
        )
    }

    @Test
    fun lectureDetection() {
        assertTrue(LmsRepository.isLecture("Раздел 1", "Лекция 3. Пределы"))
        assertTrue(LmsRepository.isLecture("Лекционные материалы", "Презентация"))
        assertFalse(LmsRepository.isLecture("Практика", "Задание 1"))
    }

    @Test
    fun teacherShortNameMatchesFullName() {
        assertTrue(TeacherNameFormat.matchesFullName("Иванов И.П.", "Иванов Иван Петрович"))
        assertTrue(TeacherNameFormat.matchesFullName("Иванов И.П.", "Иван Петрович Иванов"))
        assertTrue(TeacherNameFormat.matchesFullName("Семёнова А.А.", "Семенова Анна Алексеевна"))
        assertFalse(TeacherNameFormat.matchesFullName("Иванов И.П.", "Иванов Пётр Иванович"))
        assertFalse(TeacherNameFormat.matchesFullName("Петров И.П.", "Иванов Иван Петрович"))
    }
}
