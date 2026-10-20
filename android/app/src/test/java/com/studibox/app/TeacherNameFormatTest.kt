package com.studibox.app

import com.studibox.app.util.TeacherNameFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class TeacherNameFormatTest {

    @Test
    fun sameTeacherDifferentFormattingNormalizesToOneString() {
        assertEquals("Абрамов А.Х.", TeacherNameFormat.normalize("Абрамов А.Х."))
        assertEquals("Абрамов А.Х.", TeacherNameFormat.normalize("абрамов а.х"))
        assertEquals("Абрамов А.Х.", TeacherNameFormat.normalize("Абрамов А. Х."))

        assertEquals("Андреева А.С.", TeacherNameFormat.normalize("Андреева А.С."))
        assertEquals("Андреева А.С.", TeacherNameFormat.normalize("Андреева А. С."))

        assertEquals("Степанов К.Н.", TeacherNameFormat.normalize("Степанов К.Н."))
        assertEquals("Степанов К.Н.", TeacherNameFormat.normalize("Степанов К. Н."))
    }

    @Test
    fun extraWhitespaceIsCollapsed() {
        assertEquals("Абрамов А.Х.", TeacherNameFormat.normalize("  Абрамов   А.Х.  "))
        assertEquals("Абрамов А.Х.", TeacherNameFormat.normalize("Абрамов  А.  Х."))
    }

    @Test
    fun differentTeachersStayDifferent() {
        // Похожие, но не совпадающие ФИО НЕ должны схлопнуться в одного.
        val a = TeacherNameFormat.normalize("Абрамов А.Х.")
        val b = TeacherNameFormat.normalize("Абрамов А.С.")
        val c = TeacherNameFormat.normalize("Абрамова А.Х.")

        assert(a != b)
        assert(a != c)
    }

    @Test
    fun nameWithoutInitialsIsOnlyCapitalized() {
        assertEquals("Абрамов", TeacherNameFormat.normalize("абрамов"))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", TeacherNameFormat.normalize("   "))
    }

    // ----------------------------------------------------- splitRawField
    // Именно эта функция решает баг из отчёта: несколько преподавателей,
    // склеенных в одну ячейку расписания, не должны схлопываться в одну
    // искажённую строку вида "Андреева А.С.С.Т.Е.П.А.Н.О.В.К.Н.".

    @Test
    fun exactBugReportCaseIsSplitIntoTwoTeachers() {
        val result = TeacherNameFormat.splitRawField("Андреева А.С. Степанова К.Н.")
        assertEquals(listOf("Андреева А.С.", "Степанова К.Н."), result)
    }

    @Test
    fun singleTeacherIsNotAccidentallySplit() {
        assertEquals(listOf("Абрамов А.Х."), TeacherNameFormat.splitRawField("Абрамов А.Х."))
        assertEquals(listOf("Абрамов А.Х."), TeacherNameFormat.splitRawField("Абрамов А. Х."))
        assertEquals(listOf("Абрамов"), TeacherNameFormat.splitRawField("абрамов"))
    }

    @Test
    fun explicitSeparatorsAlsoSplitCorrectly() {
        assertEquals(
            listOf("Андреева А.С.", "Степанов К.Н."),
            TeacherNameFormat.splitRawField("Андреева А.С., Степанов К.Н.")
        )
        assertEquals(
            listOf("Андреева А.С.", "Степанов К.Н."),
            TeacherNameFormat.splitRawField("Андреева А.С.; Степанов К.Н.")
        )
        assertEquals(
            listOf("Андреева А.С.", "Степанов К.Н."),
            TeacherNameFormat.splitRawField("Андреева А.С.\nСтепанов К.Н.")
        )
    }

    @Test
    fun threeTeachersGluedTogetherAreAllSeparated() {
        val result = TeacherNameFormat.splitRawField("Иванов И.И. Петров П.П. Сидоров С.С.")
        assertEquals(listOf("Иванов И.И.", "Петров П.П.", "Сидоров С.С."), result)
    }

    @Test
    fun blankRawFieldProducesEmptyList() {
        assertEquals(emptyList<String>(), TeacherNameFormat.splitRawField(""))
        assertEquals(emptyList<String>(), TeacherNameFormat.splitRawField("   "))
    }

    @Test
    fun duplicateTeacherInOneFieldIsNotRepeated() {
        val result = TeacherNameFormat.splitRawField("Абрамов А.Х. Абрамов А.Х.")
        assertEquals(listOf("Абрамов А.Х."), result)
    }

    // ------------------------------------------------- скобки после ФИО
    // Баг из отчёта: "Яковлева Л.Е.(Аммосов В.Н.)" показывалось как
    // "Яковлева Л. Е. А. М. М. О. С. О. В." — содержимое скобок попадало в инициалы.

    @Test
    fun parentheticalAfterInitialsIsDropped() {
        assertEquals(listOf("Яковлева Л.Е."), TeacherNameFormat.splitRawField("Яковлева Л.Е.(Аммосов В.Н.)"))
        assertEquals(listOf("Яковлева Л.Е."), TeacherNameFormat.splitRawField("Яковлева Л.Е. (Аммосов В.Н.)"))
        assertEquals("Яковлева Л.Е.", TeacherNameFormat.normalize("Яковлева Л.Е.(Аммосов В.Н.)"))
    }

    @Test
    fun unclosedParenthesisIsDropped() {
        assertEquals(listOf("Яковлева Л.Е."), TeacherNameFormat.splitRawField("Яковлева Л.Е.(Аммосов В.Н."))
    }

    @Test
    fun otherTeachersSurviveParenthesisRemoval() {
        assertEquals(
            listOf("Яковлева Л.Е.", "Петров П.П."),
            TeacherNameFormat.splitRawField("Яковлева Л.Е.(Аммосов В.Н.), Петров П.П.")
        )
        assertEquals(
            listOf("Софронова В.В.", "Сорокоумова Е.В."),
            TeacherNameFormat.splitRawField("Софронова В.В., Сорокоумова Е.В.")
        )
    }

    @Test
    fun onlyParenthesesProducesEmptyList() {
        assertEquals(emptyList<String>(), TeacherNameFormat.splitRawField("(замена)"))
    }
}
