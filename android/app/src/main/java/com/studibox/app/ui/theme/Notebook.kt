package com.studibox.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** Шаг клетки — как на сайте (24px). */
val NotebookCell = 24.dp

/** Где проходит красная линия полей — левее начала текста экрана (Spacing.screenHorizontal). */
val NotebookMarginX = 11.dp

/**
 * Бумага в клетку с красной линией полей — фон экранов вместо сплошного цвета.
 * Рисуется одним проходом за кадр, без картинок и аллокаций в цикле.
 */
fun Modifier.notebookPaper(showMargin: Boolean = true): Modifier = composed {
    val colors = StudiboxTheme.colors
    drawBehind { drawNotebook(colors.background, colors.grid, colors.margin, showMargin) }
}

private fun DrawScope.drawNotebook(paper: Color, grid: Color, margin: Color, showMargin: Boolean) {
    drawRect(paper)
    val step = NotebookCell.toPx()
    val stroke = 1.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(grid, Offset(x, 0f), Offset(x, size.height), stroke)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(grid, Offset(0f, y), Offset(size.width, y), stroke)
        y += step
    }
    if (showMargin) {
        val mx = NotebookMarginX.toPx()
        drawLine(margin.copy(alpha = 0.75f), Offset(mx, 0f), Offset(mx, size.height), 2.dp.toPx())
    }
}

/**
 * Маркер под текстом: слегка неровная жёлтая заливка, как от настоящего
 * маркера. [fraction] — какая часть уже закрашена (сколько пары прошло);
 * в тёмной теме маркер — яркая полоса по нижней части строки, как на сайте.
 */
fun Modifier.markerHighlight(fraction: Float = 1f): Modifier = composed {
    val colors = StudiboxTheme.colors
    drawBehind {
        val f = fraction.coerceIn(0.08f, 1f)
        val pad = 3.dp.toPx()
        val w = (size.width + pad * 2) * f
        if (colors.isDark) {
            val h = 5.dp.toPx()
            drawRoundRect(
                color = colors.marker,
                topLeft = Offset(-pad, size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
            )
        } else {
            drawRoundRect(
                color = colors.marker,
                topLeft = Offset(-pad, size.height * 0.08f),
                size = androidx.compose.ui.geometry.Size(w, size.height * 0.88f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
        }
    }
}

/** Вычеркнуто чернилами — прошедшая пара (линия чуть наклонена, как от руки). */
fun Modifier.inkStrike(): Modifier = composed {
    val ink = StudiboxTheme.colors.accent
    drawWithContent {
        drawContent()
        val y = size.height * 0.55f
        val tilt = 2.dp.toPx()
        drawLine(
            color = ink.copy(alpha = 0.8f),
            start = Offset(-2.dp.toPx(), y + tilt),
            end = Offset(size.width + 2.dp.toPx(), y - tilt),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
