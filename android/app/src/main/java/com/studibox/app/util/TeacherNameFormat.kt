package com.studibox.app.util

/**
 * Форматирование и разделение преподавателей.
 *
 * Примеры:
 * "Абрамов А.Х."  -> "Абрамов А.Х."
 * "абрамов а.х"   -> "Абрамов А.Х."
 * "Абрамов А. Х." -> "Абрамов А.Х."
 *
 * Если в одном поле несколько преподавателей:
 * "Андреева А.С. Степанов К.Н."
 * -> ["Андреева А.С.", "Степанов К.Н."]
 *
 * Несколько преподавателей должны оставаться отдельными людьми.
 */
object TeacherNameFormat {

    private val parenthetical = Regex("""\s*\([^)]*\)?""")

    /**
     * После фамилии и инициалов ничего быть не должно. В расписании бывает
     * "Яковлева Л.Е.(Аммосов В.Н.)" — скобки с заменой/вторым лицом убираем
     * вместе с содержимым (в том числе незакрытую скобку). Раньше содержимое
     * скобок содержало точки, поэтому считалось "инициалами" первой фамилии и
     * приложение показывало "Яковлева Л. Е. А. М. М. О. С. О. В.".
     */
    fun stripParentheses(raw: String): String =
        raw.replace(parenthetical, " ").replace(Regex("""\s+"""), " ").trim()

    /**
     * Разбирает сырое значение поля teacher
     * на список отдельных преподавателей.
     *
     * Каждый преподаватель возвращается уже в нормализованном виде.
     */
    fun splitRawField(raw: String): List<String> {
        val trimmed = stripParentheses(raw)

        if (trimmed.isEmpty()) {
            return emptyList()
        }

        // Явные разделители нескольких преподавателей.
        val hardParts = trimmed
            .split(Regex("""[,;/\n]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(trimmed) }

        val names = LinkedHashSet<String>()

        for (part in hardParts) {
            val people = splitGluedNames(part)

            for (person in people) {
                val normalized = normalize(person)

                if (normalized.isNotEmpty()) {
                    names.add(normalized)
                }
            }
        }

        return names.toList()
    }

    /**
     * Разделяет несколько ФИО, записанных через пробел.
     *
     * Например:
     * "Андреева А.С. Степанов К.Н."
     *
     * превращается в:
     * "Андреева А.С."
     * "Степанов К.Н."
     */
    private fun splitGluedNames(raw: String): List<String> {
        val tokens = raw
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            return emptyList()
        }

        val people = mutableListOf<MutableList<String>>()

        for (token in tokens) {
            val looksLikeInitials = token.contains('.')

            if (!looksLikeInitials || people.isEmpty()) {
                // Токен без точки — фамилия нового преподавателя.
                people.add(mutableListOf(token))
            } else {
                // Инициалы относятся к последней фамилии.
                people.last().add(token)
            }
        }

        return people.map { person ->
            person.joinToString(" ")
        }
    }

    /**
     * Нормализует ОДНО ФИО преподавателя.
     *
     * На входе здесь уже должен находиться только один человек.
     */
    fun normalize(raw: String): String {
        val trimmed = stripParentheses(raw)

        if (trimmed.isEmpty()) {
            return trimmed
        }

        // Убираем повторяющиеся пробелы.
        val collapsed = trimmed.replace(
            Regex("""\s+"""),
            " "
        )

        val tokens = collapsed
            .split(" ")
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            return collapsed
        }

        // Первая часть — фамилия.
        val surname = capitalizeSurname(tokens[0])

        // Остальное — инициалы.
        val initialsTokens = tokens.drop(1)

        if (initialsTokens.isEmpty()) {
            return surname
        }

        // Собираем все буквы из инициалов.
        val letters = buildString {
            for (token in initialsTokens) {
                for (char in token) {
                    if (char.isLetter()) {
                        append(char)
                    }
                }
            }
        }

        if (letters.isEmpty()) {
            return "$surname ${initialsTokens.joinToString(" ")}"
        }

        // Формат:
        // АХ -> А.Х.
        val initials = buildString {
            for (char in letters) {
                append(char.uppercaseChar())
                append('.')
            }
        }

        return "$surname $initials"
    }

    /**
     * Совпадает ли ФИО из расписания ("Иванов И.И.") с полным именем из СДО
     * ("Иванов Иван Иванович" или "Иван Иванович Иванов"): фамилия совпадает
     * целиком, инициалы — с первыми буквами остальных слов по порядку.
     */
    fun matchesFullName(scheduleName: String, fullName: String): Boolean {
        val normalized = normalize(scheduleName)
        val surname = normalized.substringBefore(' ').lowercase().replace('ё', 'е')
        if (surname.isEmpty()) return false
        val initials = normalized.substringAfter(' ', "").filter { it.isLetter() }.lowercase()
        val words = fullName.lowercase().replace('ё', 'е')
            .split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val surnameIndex = words.indexOf(surname)
        if (surnameIndex < 0) return false
        val others = words.filterIndexed { index, _ -> index != surnameIndex }
        val otherInitials = others.joinToString("") { it.take(1) }
        return initials.isEmpty() || otherInitials.startsWith(initials)
    }

    /**
     * Делает фамилию красиво оформленной:
     * абрамов -> Абрамов
     * иванов-петров -> Иванов-Петров
     */
    private fun capitalizeSurname(token: String): String {
        return token
            .split("-")
            .joinToString("-") { part ->
                if (part.isEmpty()) {
                    part
                } else {
                    part
                        .lowercase()
                        .replaceFirstChar { char ->
                            char.uppercaseChar()
                        }
                }
            }
    }
}