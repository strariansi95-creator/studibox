package com.studibox.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studibox.app.data.local.LessonEntity
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.BadgeTextStyle
import com.studibox.app.ui.theme.PairNumberTextStyle
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.ui.theme.markerHighlight
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

/** Часть карточки — пара одной подгруппы. Нажимается отдельно: у подгрупп разные лекции. */
@Composable
private fun SubgroupPart(
    lesson: LessonEntity,
    past: Boolean,
    large: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = SubgroupFormat.fullLabel(lesson.subgroup),
                style = MaterialTheme.typography.labelMedium,
                color = colors.margin
            )
            Text(
                text = lesson.subject.ifBlank { "Занятие" },
                style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = if (past) colors.textSecondary else colors.textPrimary,
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

/**
 * Пара, поделённая на подгруппы: номер и время — один раз, ниже части
 * «1 подгруппа» / «2 подгруппа».
 */
@Composable
private fun SubgroupSlotCard(
    slot: StudentSlot,
    state: LessonUiState,
    onOpenLesson: (LessonEntity) -> Unit,
    modifier: Modifier
) {
    val colors = StudiboxTheme.colors
    val first = slot.first

    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surface,
        borderColor = if (state == LessonUiState.CURRENT) colors.accent else colors.outline,
        contentPadding = PaddingValues(top = 14.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            PairNumber(first.number)
            Spacer(Modifier.width(12.dp))
            Column {
                LessonTimeText(first.startTime, first.endTime, state)
                LessonStateLabel(state)
            }
        }

        slot.lessons.forEachIndexed { index, lesson ->
            if (index > 0) AppRowDivider(Modifier.padding(start = 40.dp, end = 14.dp), inset = 0.dp)
            SubgroupPart(
                lesson = lesson,
                past = state == LessonUiState.PAST,
                large = false,
                onClick = { onOpenLesson(lesson) },
                modifier = Modifier.padding(start = 40.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)
            )
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 6.dp, bottom = 6.dp)
            .currentSheet()
            .padding(vertical = Spacing.cardPaddingLarge)
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.cardPaddingLarge)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Сейчас", style = BadgeTextStyle, color = colors.accent)
                if (first.number > 0) {
                    Text(text = "${first.number} пара", style = PairNumberTextStyle, color = colors.margin)
                }
            }
            Text(
                text = timeRange(first.startTime, first.endTime),
                style = MaterialTheme.typography.headlineSmall,
                color = if (colors.isDark) colors.textPrimary else colors.onMarker,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .markerHighlight(progress?.fraction ?: 1f)
            )
        }

        slot.lessons.forEachIndexed { index, lesson ->
            if (index > 0) AppRowDivider(Modifier.padding(horizontal = Spacing.cardPaddingLarge), inset = 0.dp)
            SubgroupPart(
                lesson = lesson,
                past = false,
                large = true,
                onClick = { onOpenLesson(lesson) },
                modifier = Modifier.padding(horizontal = Spacing.cardPaddingLarge, vertical = 10.dp)
            )
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
