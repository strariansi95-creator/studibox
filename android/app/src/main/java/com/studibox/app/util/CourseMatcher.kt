package com.studibox.app.util

/**
 * Сопоставляет дисциплину из расписания с курсом СДО.
 *
 * Названия почти никогда не совпадают буквально:
 * "Математический анализ" ↔ "Математический анализ (КИТ-ВР-26) 2025/2026".
 * Поэтому сравниваются «основы» слов (первые 5 букв — грубо срезают падежные
 * окончания), без служебных слов, чисел и кодов групп.
 */
object CourseMatcher {

    private const val STEM_LENGTH = 5
    private const val MIN_SCORE = 0.6

    private val stopWords = setOf(
        "и", "в", "во", "на", "по", "для", "с", "со", "к", "о", "об", "от", "из", "при",
        "the", "of", "and", "in", "for", "курс", "дисциплина", "часть", "семестр"
    )

    fun stems(text: String): Set<String> =
        text.lowercase()
            .replace('ё', 'е')
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .asSequence()
            .filter { it.length >= 2 }
            .filter { it !in stopWords }
            .filter { token -> token.none { it.isDigit() } }
            .map { it.take(STEM_LENGTH) }
            .toSet()

    /** Доля слов предмета, найденных в названии курса (0..1). */
    fun score(subject: String, courseName: String): Double {
        val subjectStems = stems(subject)
        if (subjectStems.isEmpty()) return 0.0
        val courseStems = stems(courseName)
        if (courseStems.isEmpty()) return 0.0
        val common = subjectStems.count { it in courseStems }
        return common.toDouble() / subjectStems.size
    }

    /**
     * Лучший курс для предмета или null, если уверенного совпадения нет.
     * При равной доле выигрывает курс с меньшим количеством «лишних» слов.
     */
    fun <T> bestMatch(subject: String, courses: List<T>, name: (T) -> String): T? =
        courses
            .map { course -> course to score(subject, name(course)) }
            .filter { it.second >= MIN_SCORE }
            .maxWithOrNull(
                compareBy<Pair<T, Double>> { it.second }
                    .thenByDescending { stems(name(it.first)).size }
            )
            ?.first

    /** Ключ для запоминания ручного выбора курса. */
    fun subjectKey(subject: String): String = stems(subject).sorted().joinToString(" ")
}
