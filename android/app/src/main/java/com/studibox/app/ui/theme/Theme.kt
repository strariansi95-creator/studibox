package com.studibox.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.studibox.app.data.prefs.ThemeMode

/**
 * Палитра Studibox, доступная из любого composable как StudiboxTheme.colors.
 * Material-схема ниже заполняется из неё же, чтобы стандартные компоненты
 * (Switch, TextField, Snackbar, DatePicker) попадали в ту же систему.
 */
val LocalStudiboxColors = staticCompositionLocalOf { StudiboxDarkColors }

private fun materialDark(c: StudiboxColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.textOnAccent,
    primaryContainer = c.accentContainer,
    onPrimaryContainer = c.onAccentContainer,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = c.accentContainer,
    onSecondaryContainer = c.onAccentContainer,
    tertiary = c.success,
    background = c.background,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.textSecondary,
    surfaceContainer = c.surfaceVariant,
    surfaceContainerHigh = c.surfacePressed,
    outline = c.outlineStrong,
    outlineVariant = c.outline,
    error = c.danger,
    onError = Color.White,
    errorContainer = c.dangerContainer,
    onErrorContainer = c.danger,
    scrim = Color.Black
)

private fun materialLight(c: StudiboxColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = c.textOnAccent,
    primaryContainer = c.accentContainer,
    onPrimaryContainer = c.onAccentContainer,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = c.accentContainer,
    onSecondaryContainer = c.onAccentContainer,
    tertiary = c.success,
    background = c.background,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.textSecondary,
    surfaceContainer = c.surfaceVariant,
    surfaceContainerHigh = c.surfacePressed,
    outline = c.outlineStrong,
    outlineVariant = c.outline,
    error = c.danger,
    onError = Color.White,
    errorContainer = c.dangerContainer,
    onErrorContainer = c.danger,
    scrim = Color.Black
)

/**
 * Тема приложения. Сигнатура не менялась — MainActivity по-прежнему передаёт
 * ThemeMode из DataStore, поддерживаются СВЕТЛАЯ / ТЁМНАЯ / СИСТЕМНАЯ.
 *
 * Динамические цвета Android 12+ отключены намеренно: у Studibox собственная
 * фирменная фиолетово-синяя палитра, и она должна быть одинаковой на всех
 * устройствах.
 */
@Composable
fun StudiboxTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = if (dark) StudiboxDarkColors else StudiboxLightColors
    val scheme = if (dark) materialDark(colors) else materialLight(colors)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalStudiboxColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = StudiboxTypography,
            shapes = StudiboxShapes,
            content = content
        )
    }
}

/** Точка доступа к палитре: StudiboxTheme.colors.accent и т.д. */
object StudiboxTheme {
    val colors: StudiboxColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStudiboxColors.current
}
