package com.studibox.app.util

/**
 * Код направления из имени группы КИТ: «КИТ-ИСИП-25-1» → ИСИП.
 */
object DirectionCatalog {

    val titles: Map<String, String> = mapOf(
        "ВР" to "Веб-разработка",
        "ИСИП" to "Информационные системы и программирование",
        "ИССС" to "Инфокоммуникационные сети и системы связи",
        "ОИБАС" to "Обеспечение информационной безопасности автоматизированных систем",
        "РБП" to "Разработка бизнес-приложений",
        "РИС" to "Разработка информационных систем",
        "РМП" to "Разработка мобильных приложений",
        "СИСА" to "Сетевое и системное администрирование",
        "ТС" to "Строительство и эксплуатация зданий и сооружений",
        "ТЭСИС" to "Техническая эксплуатация и сопровождение информационных систем"
    )

    private val GROUP_DIRECTION = Regex("""^КИТ[- ]([А-ЯЁA-Z]+)""", RegexOption.IGNORE_CASE)

    fun codeFromGroup(groupName: String?): String? {
        val raw = groupName?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = GROUP_DIRECTION.find(raw.uppercase().replace('Ё', 'Е')) ?: return null
        return match.groupValues[1]
    }

    fun title(code: String): String = titles[code] ?: code

    fun cleanSubject(raw: String): String =
        raw
            .replace(Regex("""\s*(?:с|до)\s*\d{6,8}""", RegexOption.IGNORE_CASE), "")
            .replace("*", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
