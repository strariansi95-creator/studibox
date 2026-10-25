package com.studibox.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Единая цветовая система Studibox — «тетрадь в клетку», как на сайте
 * (web/assets/style.css): бумага, синие чернила, красная линия полей,
 * жёлтый маркер.
 *
 * Экраны не объявляют Color(0xFF...) у себя — все цвета берутся из
 * StudiboxTheme.colors и автоматически меняются вместе с темой.
 */
@Immutable
data class StudiboxColors(
    /** Бумага — фон экранов (поверх неё рисуется клетка, см. notebookPaper()). */
    val background: Color,
    val backgroundElevated: Color,
    /** Лист поверх бумаги: карточки, поля ввода. */
    val surface: Color,
    val surfaceVariant: Color,
    val surfacePressed: Color,

    /** Линейка: границы карточек и разделители. */
    val outline: Color,
    val outlineStrong: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,

    /** Чернила. */
    val accent: Color,
    val accentPressed: Color,
    val accentMuted: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,

    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerContainer: Color,

    /** Линии клетки. */
    val grid: Color,
    /** Красная линия полей и номера пар на полях. */
    val margin: Color,
    /** Маркер: текущая пара. */
    val marker: Color,
    /** Текст поверх маркера. */
    val onMarker: Color,

    val isDark: Boolean
) {
    /**
     * Раньше — фиолетово-синий градиент. В тетрадном стиле главные кнопки
     * и плашки — сплошные чернила; имя оставлено, чтобы не трогать экраны.
     */
    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(accent, accent))

    /** Мягкая подложка для выделенных, но не «кричащих» блоков. */
    val accentGradientSoft: Brush
        get() = Brush.linearGradient(listOf(accentContainer, accentContainer))
}

// Чернила — близко к прежнему индиго, чтобы приложение осталось узнаваемым.
val AccentViolet = Color(0xFF3B39D4)
val AccentIndigo = Color(0xFF3B39D4)
val AccentBlue = Color(0xFF4F8CFF)

/**
 * Цвета индикаторов (группы, дисциплины). Приглушённые «пишущие» цвета —
 * синяя, зелёная, красная ручки, карандаш, — чтобы не спорить с маркером.
 */
val SubjectAccents = listOf(
    Color(0xFF3B39D4),
    Color(0xFF2F7DD1),
    Color(0xFF138A6E),
    Color(0xFFD9822B),
    Color(0xFFC2416B),
    Color(0xFF6B5BD6)
)

/** Стабильный цвет по строковому ключу: одна и та же группа всегда одного цвета. */
fun colorForKey(key: String): Color {
    if (key.isEmpty()) return AccentIndigo
    var hash = 0
    for (ch in key) hash = (hash * 31 + ch.code) and 0x7FFFFFFF
    return SubjectAccents[hash % SubjectAccents.size]
}

/** «Ночная тетрадь». */
val StudiboxDarkColors = StudiboxColors(
    background = Color(0xFF0F1529),
    backgroundElevated = Color(0xFF151D36),
    surface = Color(0xFF151D36),
    surfaceVariant = Color(0xFF1A2342),
    surfacePressed = Color(0xFF212B4F),

    outline = Color(0xFF26304F),
    outlineStrong = Color(0xFF34406A),

    textPrimary = Color(0xFFE9EDF6),
    textSecondary = Color(0xFFA2ABC0),
    textTertiary = Color(0xFF6F7A93),
    // Чернила в тёмной теме светлые — текст на них тёмный.
    textOnAccent = Color(0xFF10142A),

    accent = Color(0xFFA3A6FF),
    accentPressed = Color(0xFF8C90F5),
    accentMuted = Color(0xFF3B4290),
    accentContainer = Color(0xFF232A5A),
    onAccentContainer = Color(0xFFD3D5FF),

    success = Color(0xFF3FCFAA),
    warning = Color(0xFFF2A54A),
    danger = Color(0xFFFF6369),
    dangerContainer = Color(0xFF3A1B24),

    grid = Color(0xFF1B2542),
    margin = Color(0xFFFF6369),
    marker = Color(0xFFF5D63D),
    onMarker = Color(0xFF10142A),

    isDark = true
)

val StudiboxLightColors = StudiboxColors(
    background = Color(0xFFF7F9FC),
    backgroundElevated = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F3F9),
    surfacePressed = Color(0xFFE6EAF4),

    outline = Color(0xFFD5DBE7),
    outlineStrong = Color(0xFFC2CADB),

    textPrimary = Color(0xFF1B2232),
    textSecondary = Color(0xFF566074),
    textTertiary = Color(0xFF8A93A6),
    textOnAccent = Color(0xFFFFFFFF),

    accent = Color(0xFF3B39D4),
    accentPressed = Color(0xFF2E2CB0),
    accentMuted = Color(0xFFB9B8F2),
    accentContainer = Color(0xFFE7E7FB),
    onAccentContainer = Color(0xFF2E2CB0),

    success = Color(0xFF138A6E),
    warning = Color(0xFFC96E1F),
    danger = Color(0xFFE5484D),
    dangerContainer = Color(0xFFFDECEC),

    grid = Color(0xFFDCE5F3),
    margin = Color(0xFFE5484D),
    marker = Color(0xFFFFE15A),
    onMarker = Color(0xFF1B2232),

    isDark = false
)
