package com.studibox.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Карточка слота: одиночная пара — обычная LessonCard, поделённая на подгруппы — одна общая. */
@Composable
fun StudentSlotCard(
    slot: StudentSlot,
    state: LessonUiState,
    onOpenLesson: (LessonEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!slot.isSplit) {
        LessonCard(slot.first, modifier, state = state, onClick = { onOpenLesson(slot.first) })
    } else {
        SubgroupSlotCard(slot, state, onOpenLesson, modifier)
    }
}

/** Большая карточка «Сейчас» на экране «Сегодня» — тоже одна на время пары. */
@Composable
fun CurrentStudentSlotCard(
    slot: StudentSlot,
    onOpenLesson: (LessonEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!slot.isSplit) {
        CurrentLessonCard(slot.first, modifier, onClick = { onOpenLesson(slot.first) })
    } else {
        CurrentSubgroupSlotCard(slot, onOpenLesson, modifier)
    }
}

/**
 * Пара, поделённая на подгруппы: время и номер пары — один раз, ниже части
 * «1 подгруппа» / «2 подгруппа». Каждая часть нажимается отдельно: у подгрупп
 * разные предметы, а значит, и разные лекции в СДО.
 */
@Composable
private fun SubgroupSlotCard(
    slot: StudentSlot,
    state: LessonUiState,
    onOpenLesson: (LessonEntity) -> Unit,
    modifier: Modifier
) {
    val colors = StudiboxTheme.colors
    val isCurrent = state == LessonUiState.CURRENT
    val first = slot.first
    val indicator = when (state) {
        LessonUiState.CURRENT -> colors.accent
        LessonUiState.NEXT -> colors.success
        else -> colorForKey(first.subject)
    }

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (state == LessonUiState.PAST) 0.55f else 1f),
        containerColor = if (isCurrent) colors.surfaceVariant else colors.surface,
        borderColor = if (isCurrent) colors.accentMuted else colors.outline,
        contentPadding = PaddingValues(top = 14.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(AppShapes.pill)
                    .background(indicator)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = timeRange(first.startTime, first.endTime).ifBlank { "Время не указано" },
                style = TimeTextStyle,
                color = if (isCurrent) colors.accent else colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (first.number > 0) {
                Text(
                    text = "${first.number} пара",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }

        slot.lessons.forEachIndexed { index, lesson ->
            if (index > 0) AppRowDivider(Modifier.padding(start = 30.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLesson(lesson) }
                    .padding(start = 30.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = SubgroupFormat.fullLabel(lesson.subgroup),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent
                    )
                    Text(
                        text = lesson.subject.ifBlank { "Занятие" },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    teacherNames(lesson.teacher).forEach { name ->
                        LessonMetaRow(Icons.Filled.Person, name, colors.textTertiary, colors.textSecondary)
                    }
                    if (lesson.classroom.isNotBlank()) {
                        LessonMetaRow(Icons.Filled.LocationOn, "Ауд. ${lesson.classroom}", colors.textTertiary, colors.textSecondary)
                    }
                    val chips = listOf(lesson.type, lesson.weekType).filter { it.isNotBlank() }
                    if (chips.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chips.forEach { AppBadge(text = it) }
                        }
                    }
                }
                OpenLessonHint()
            }
        }
    }
}

@Composable
private fun CurrentSubgroupSlotCard(
    slot: StudentSlot,
    onOpenLesson: (LessonEntity) -> Unit,
    modifier: Modifier
) {
    val colors = StudiboxTheme.colors
    val first = slot.first
    val progress = lessonProgress(first.startTime, first.endTime)
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.fraction ?: 0f,
        animationSpec = tween(600),
        label = "slotProgress"
    )
    val onAccent = Color.White
    val onAccentMuted = Color.White.copy(alpha = 0.78f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.cardLarge)
            .background(colors.accentGradient)
            .padding(vertical = Spacing.cardPaddingLarge)
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.cardPaddingLarge)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(AppShapes.pill)
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(text = "СЕЙЧАС", style = BadgeTextStyle, color = onAccent)
                }
                if (first.number > 0) {
                    Text(text = "${first.number} пара", style = MaterialTheme.typography.labelMedium, color = onAccentMuted)
                }
            }
            Text(
                text = timeRange(first.startTime, first.endTime),
                style = MaterialTheme.typography.headlineSmall,
                color = onAccent,
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        slot.lessons.forEachIndexed { index, lesson ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLesson(lesson) }
                    .padding(horizontal = Spacing.cardPaddingLarge, vertical = 10.dp)
            ) {
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = SubgroupFormat.fullLabel(lesson.subgroup),
                    style = MaterialTheme.typography.labelMedium,
                    color = onAccentMuted
                )
                Text(
                    text = lesson.subject.ifBlank { "Занятие" },
                    style = MaterialTheme.typography.titleLarge,
                    color = onAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val details = buildList {
                    addAll(teacherNames(lesson.teacher))
                    if (lesson.classroom.isNotBlank()) add("ауд. ${lesson.classroom}")
                }.joinToString(", ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = onAccentMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        if (progress != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.cardPaddingLarge)
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(AppShapes.pill),
                    color = onAccent,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                Spacer(Modifier.width(12.dp))
                Text(text = "${progress.minutesLeft} мин", style = MaterialTheme.typography.labelMedium, color = onAccentMuted)
            }
        }
    }
}
