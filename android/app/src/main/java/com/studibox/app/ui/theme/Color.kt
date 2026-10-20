package com.studibox.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Единая цветовая система Studibox.
 *
 * Экраны не объявляют Color(0xFF...) у себя — все цвета берутся из
 * StudiboxTheme.colors и автоматически меняются вместе с темой.
 */
@Immutable
data class StudiboxColors(
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfacePressed: Color,

    val outline: Color,
    val outlineStrong: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,

    val accent: Color,
    val accentPressed: Color,
    val accentMuted: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,

    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerContainer: Color,

    val isDark: Boolean
) {
    /** Градиент акцента: карточка текущей пары, главные кнопки. */
    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(AccentViolet, AccentBlue))

    /** Мягкая подложка того же направления — для выделенных, но не «кричащих» блоков. */
    val accentGradientSoft: Brush
        get() = if (isDark) {
            Brush.linearGradient(listOf(Color(0xFF232A5C), Color(0xFF1B2C50)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFEDEBFF), Color(0xFFE6EEFF)))
        }
}

// Базовая палитра акцента — одна и та же в обеих темах.
val AccentViolet = Color(0xFF7567FF)
val AccentIndigo = Color(0xFF635BFF)
val AccentBlue = Color(0xFF4F8CFF)

/** Цвета индикаторов карточек (группы, дисциплины). */
val SubjectAccents = listOf(
    Color(0xFF7567FF),
    Color(0xFF4F8CFF),
    Color(0xFF34C6A8),
    Color(0xFFF2994A),
    Color(0xFFEB5E8C),
    Color(0xFF9B6BFF)
)

/** Стабильный цвет по строковому ключу: одна и та же группа всегда одного цвета. */
fun colorForKey(key: String): Color {
    if (key.isEmpty()) return AccentViolet
    var hash = 0
    for (ch in key) hash = (hash * 31 + ch.code) and 0x7FFFFFFF
    return SubjectAccents[hash % SubjectAccents.size]
}

val StudiboxDarkColors = StudiboxColors(
    background = Color(0xFF07111F),
    backgroundElevated = Color(0xFF0B1628),
    surface = Color(0xFF101F33),
    surfaceVariant = Color(0xFF13243A),
    surfacePressed = Color(0xFF1A3050),

    outline = Color(0xFF1E3350),
    outlineStrong = Color(0xFF2B456A),

    textPrimary = Color(0xFFF2F6FC),
    textSecondary = Color(0xFF93A4BF),
    textTertiary = Color(0xFF64768F),
    textOnAccent = Color(0xFFFFFFFF),

    accent = AccentViolet,
    accentPressed = Color(0xFF5B4EE0),
    accentMuted = Color(0xFF3A3A7A),
    accentContainer = Color(0xFF1E2050),
    onAccentContainer = Color(0xFFCFC9FF),

    success = Color(0xFF34C6A8),
    warning = Color(0xFFF2994A),
    danger = Color(0xFFFF6B6B),
    dangerContainer = Color(0xFF3A1B24),

    isDark = true
)

val StudiboxLightColors = StudiboxColors(
    background = Color(0xFFF5F7FB),
    backgroundElevated = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F9),
    surfacePressed = Color(0xFFE7EBF5),

    outline = Color(0xFFE4E8F2),
    outlineStrong = Color(0xFFD2D8E8),

    textPrimary = Color(0xFF111A2B),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A0B4),
    textOnAccent = Color(0xFFFFFFFF),

    accent = AccentIndigo,
    accentPressed = Color(0xFF4F46E5),
    accentMuted = Color(0xFFBFBDF7),
    accentContainer = Color(0xFFEDEBFF),
    onAccentContainer = Color(0xFF3B33C7),

    success = Color(0xFF12A183),
    warning = Color(0xFFD97B2B),
    danger = Color(0xFFE04B4B),
    dangerContainer = Color(0xFFFDECEC),

    isDark = false
)
