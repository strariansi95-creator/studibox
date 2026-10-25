package com.studibox.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.studibox.app.R

/**
 * Типографика Studibox — та же, что на сайте:
 * Unbounded (широкий, с характером) — заголовки и время пар;
 * Golos Text (создан для кириллицы) — весь остальной текст.
 * Оба шрифта с переменной толщиной (res/font, лицензия OFL — assets/licenses),
 * нужная толщина выбирается осью wght — minSdk 26 это поддерживает.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val DisplayFontFamily: FontFamily = FontFamily(
    variable(R.font.unbounded, FontWeight.Medium),
    variable(R.font.unbounded, FontWeight.SemiBold),
    variable(R.font.unbounded, FontWeight.Bold),
    variable(R.font.unbounded, FontWeight.ExtraBold)
)

val StudiboxFontFamily: FontFamily = FontFamily(
    variable(R.font.golos_text, FontWeight.Normal),
    variable(R.font.golos_text, FontWeight.Medium),
    variable(R.font.golos_text, FontWeight.SemiBold),
    variable(R.font.golos_text, FontWeight.Bold)
)

val StudiboxTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = StudiboxFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )
)

/** Время пары — Unbounded с цифрами одинаковой ширины, как на сайте. */
val TimeTextStyle = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 21.sp,
    fontFeatureSettings = "tnum"
)

/** Номер пары на полях — красный, как в тетради. */
val PairNumberTextStyle = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 17.sp,
    lineHeight = 21.sp
)

/** Метка «Сейчас» — без капслока, как на сайте. */
val BadgeTextStyle = TextStyle(
    fontFamily = StudiboxFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 15.sp
)
