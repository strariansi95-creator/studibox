package com.studibox.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.studibox.app.ui.theme.inkStrike
import com.studibox.app.ui.theme.markerHighlight
import com.studibox.app.ui.theme.PairNumberTextStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studibox.app.data.local.LessonEntity
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.BadgeTextStyle
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.ui.theme.TimeTextStyle
import com.studibox.app.ui.theme.colorForKey
import com.studibox.app.util.SubgroupFormat

internal fun timeRange(start: String, end: String): String =
    listOf(start, end).filter { it.isNotBlank() }.joinToString(" — ")

/** Преподавателей в поле может быть несколько — они разделены запятой. */
internal fun teacherNames(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

// ---------------------------------------------------------------- баннеры и состояния

@Composable
fun OfflineBanner(message: String, modifier: Modifier = Modifier) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp)
            .clip(AppShapes.chip)
            .background(colors.surfaceVariant)
            .border(BorderStroke(1.dp, colors.outline), AppShapes.chip)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

/** Понятная студенту ошибка вместо технического текста, с кнопкой «Повторить». */
@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp)
            .clip(AppShapes.chip)
            .background(colors.dangerContainer)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.danger,
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Повторить",
                style = MaterialTheme.typography.labelLarge,
                color = colors.danger,
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Аккуратный loading state вместо голого спиннера. */
@Composable
fun LoadingState(text: String = "Загружаем расписание…", modifier: Modifier = Modifier) {
    val colors = StudiboxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp, color = colors.accent)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AppStatePlaceholder(
        icon = Icons.Filled.CalendarMonth,
        title = text,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction
    )
}

/** Состояние «нет интернета» на весь экран. */
@Composable
fun OfflineState(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    AppStatePlaceholder(
        icon = Icons.Filled.CloudOff,
        title = "Нет интернета",
        description = "Проверьте подключение к сети и попробуйте снова.",
        modifier = modifier,
        actionLabel = if (onRetry != null) "Повторить" else null,
        onAction = onRetry,
        accent = StudiboxTheme.colors.warning
    )
}

// ---------------------------------------------------------------- карточки занятий

/** Мелкая строка «иконка + текст» внутри карточки пары. */
@Composable
internal fun LessonMetaRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Номер пары красным на полях карточки — как номер задания в тетради. */
@Composable
internal fun PairNumber(number: Int, modifier: Modifier = Modifier) {
    Text(
        text = if (number > 0) number.toString() else "",
        style = PairNumberTextStyle,
        color = StudiboxTheme.colors.margin,
        textAlign = TextAlign.End,
        modifier = modifier.width(20.dp)
    )
}

/**
 * Время пары: у текущей — под маркером на ту часть, которая уже прошла,
 * у прошедшей — вычеркнуто чернилами.
 */
@Composable
internal fun LessonTimeText(
    startTime: String,
    endTime: String,
    state: LessonUiState,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    val text = timeRange(startTime, endTime).ifBlank { "Время не указано" }
    val decoration = when (state) {
        LessonUiState.CURRENT -> Modifier.markerHighlight(lessonProgress(startTime, endTime)?.fraction ?: 1f)
        LessonUiState.PAST -> Modifier.inkStrike()
        else -> Modifier
    }
    Text(
        text = text,
        style = TimeTextStyle,
        color = when (state) {
            LessonUiState.CURRENT -> if (colors.isDark) colors.textPrimary else colors.onMarker
            LessonUiState.PAST -> colors.textTertiary
            else -> colors.textPrimary
        },
        modifier = modifier.then(decoration)
    )
}

/** Подпись состояния под предметом: «Идёт сейчас» / «Следующая». */
@Composable
internal fun LessonStateLabel(state: LessonUiState) {
    val text = when (state) {
        LessonUiState.CURRENT -> "Идёт сейчас"
        LessonUiState.NEXT -> "Следующая"
        else -> return
    }
    Text(
        text = text,
        style = BadgeTextStyle,
        color = StudiboxTheme.colors.accent,
        modifier = Modifier.padding(top = 3.dp)
    )
}

/**
 * Карточка обычной пары — строка тетрадного листа: номер на полях, время,
 * дисциплина, преподаватель, аудитория и подгруппа, если она есть в данных.
 */
@Composable
fun LessonCard(
    lesson: LessonEntity,
    modifier: Modifier = Modifier,
    state: LessonUiState = LessonUiState.UPCOMING,
    onClick: (() -> Unit)? = null
) {
    val colors = StudiboxTheme.colors
    val isCurrent = state == LessonUiState.CURRENT

    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = colors.surface,
        borderColor = if (isCurrent) colors.accent else colors.outline,
        contentPadding = PaddingValues(start = 8.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PairNumber(lesson.number)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                LessonTimeText(lesson.startTime, lesson.endTime, state)

                Text(
                    text = lesson.subject.ifBlank { "Занятие" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state == LessonUiState.PAST) colors.textSecondary else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                LessonStateLabel(state)

                teacherNames(lesson.teacher).forEach { name ->
                    LessonMetaRow(
                        icon = Icons.Filled.Person,
                        text = name,
                        tint = colors.textTertiary,
                        textColor = colors.textSecondary
                    )
                }

                if (lesson.classroom.isNotBlank()) {
                    LessonMetaRow(
                        icon = Icons.Filled.LocationOn,
                        text = "Ауд. ${lesson.classroom}",
                        tint = colors.textTertiary,
                        textColor = colors.textSecondary
                    )
                }

                val chips = buildList {
                    if (lesson.type.isNotBlank()) add(lesson.type)
                    if (lesson.subgroup.isNotBlank()) add(SubgroupFormat.fullLabel(lesson.subgroup))
                    if (lesson.weekType.isNotBlank()) add(lesson.weekType)
                }
                if (chips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chips.forEach { chip -> AppBadge(text = chip) }
                    }
                }
            }

            if (onClick != null) OpenLessonHint()
        }
    }
}

/** Стрелка справа: по нажатию на карточку открываются лекции пары из СДО. */
@Composable
internal fun OpenLessonHint() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "Лекции",
        tint = StudiboxTheme.colors.textTertiary,
        modifier = Modifier
            .padding(start = 6.dp, top = 12.dp)
            .size(20.dp)
    )
}

/**
 * Лист «Сейчас» для главной карточки экрана «Сегодня»: чернильная рамка и
 * сдвинутая подложка, как у блока звонков на сайте.
 */
internal fun Modifier.currentSheet(): Modifier = composed {
    val colors = StudiboxTheme.colors
    this
        .drawBehind {
            val shift = 6.dp.toPx()
            drawRoundRect(
                color = colors.accentContainer,
                topLeft = androidx.compose.ui.geometry.Offset(shift, shift),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
            )
        }
        .clip(AppShapes.cardLarge)
        .background(colors.surface)
        .border(BorderStroke(2.dp, colors.accent), AppShapes.cardLarge)
}

/**
 * Главная карточка экрана «Сегодня»: текущая пара, время под маркером на ту
 * часть, что уже прошла, полоса прогресса и обратный отсчёт.
 */
@Composable
fun CurrentLessonCard(
    lesson: LessonEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = StudiboxTheme.colors
    val progress = lessonProgress(lesson.startTime, lesson.endTime)
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.fraction ?: 0f,
        animationSpec = tween(600),
        label = "lessonProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 6.dp, bottom = 6.dp)
            .currentSheet()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.cardPaddingLarge)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Сейчас", style = BadgeTextStyle, color = colors.accent)
            if (lesson.number > 0) {
                Text(
                    text = "${lesson.number} пара",
                    style = PairNumberTextStyle,
                    color = colors.margin
                )
            }
        }

        Text(
            text = timeRange(lesson.startTime, lesson.endTime),
            style = MaterialTheme.typography.headlineSmall,
            color = if (colors.isDark) colors.textPrimary else colors.onMarker,
            modifier = Modifier
                .padding(top = 10.dp)
                .markerHighlight(progress?.fraction ?: 1f)
        )

        Text(
            text = lesson.subject.ifBlank { "Занятие" },
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        teacherNames(lesson.teacher).forEach { name ->
            LessonMetaRow(
                icon = Icons.Filled.Person,
                text = name,
                tint = colors.textTertiary,
                textColor = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (lesson.classroom.isNotBlank()) {
            LessonMetaRow(
                icon = Icons.Filled.LocationOn,
                text = "Ауд. ${lesson.classroom}",
                tint = colors.textTertiary,
                textColor = colors.textSecondary
            )
        }

        if (lesson.subgroup.isNotBlank()) {
            AppBadge(
                text = SubgroupFormat.fullLabel(lesson.subgroup),
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (progress != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(AppShapes.pill),
                    color = colors.accent,
                    trackColor = colors.accentContainer
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${progress.minutesLeft} мин",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/** Переключатель «Все / 1 / 2». Показывается только если у группы есть подгруппы. */
@Composable
fun SubgroupSelector(
    subgroups: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (subgroups.isEmpty()) return
    val colors = StudiboxTheme.colors
    val options = listOf<String?>(null) + subgroups
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val label = option?.let { SubgroupFormat.shortLabel(it) } ?: "Все"
            Box(
                modifier = Modifier
                    .clip(AppShapes.chip)
                    .background(if (isSelected) colors.accent else colors.surfaceVariant)
                    .border(
                        BorderStroke(1.dp, if (isSelected) colors.accent else colors.outline),
                        AppShapes.chip
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.textOnAccent else colors.textSecondary
                )
            }
        }
    }
}

/**
 * Карточка пары преподавателя. Отличие от LessonCard: вместо одной группы —
 * список групп, которым читается эта же пара в это же время (см. TeacherAggregation).
 * Одна пара = одна карточка, даже если групп несколько.
 */
@Composable
fun TeacherLessonCard(
    slot: TeacherLessonSlot,
    modifier: Modifier = Modifier,
    state: LessonUiState = LessonUiState.UPCOMING,
    onClick: (() -> Unit)? = null
) {
    val colors = StudiboxTheme.colors
    val isCurrent = state == LessonUiState.CURRENT

    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = colors.surface,
        borderColor = if (isCurrent) colors.accent else colors.outline,
        contentPadding = PaddingValues(start = 8.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PairNumber(slot.number)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                LessonTimeText(slot.startTime, slot.endTime, state)

                Text(
                    text = slot.subject.ifBlank { "Занятие" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state == LessonUiState.PAST) colors.textSecondary else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                LessonStateLabel(state)

                // Несколько групп на одной паре — перечисляем внутри ОДНОЙ карточки.
                slot.groups.forEach { groupRoom ->
                    val text = if (groupRoom.classroom.isNotBlank()) {
                        "${groupRoom.group}, ауд. ${groupRoom.classroom}"
                    } else {
                        groupRoom.group
                    }
                    LessonMetaRow(
                        icon = Icons.Filled.LocationOn,
                        text = text,
                        tint = colors.textTertiary,
                        textColor = colors.textSecondary
                    )
                }

                val chips = buildList {
                    if (slot.type.isNotBlank()) add(slot.type)
                    if (slot.subgroup.isNotBlank()) add(SubgroupFormat.fullLabel(slot.subgroup))
                    if (slot.weekType.isNotBlank()) add(slot.weekType)
                }
                if (chips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chips.forEach { chip -> AppBadge(text = chip) }
                    }
                }
            }

            if (onClick != null) OpenLessonHint()
        }
    }
}
