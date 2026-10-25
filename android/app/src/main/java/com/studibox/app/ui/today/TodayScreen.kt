package com.studibox.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.R
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.ui.common.AppBadge
import com.studibox.app.ui.common.AppIconTile
import com.studibox.app.ui.common.CurrentLessonCard
import com.studibox.app.ui.common.EmptyState
import com.studibox.app.ui.common.LessonCard
import com.studibox.app.ui.common.LessonUiState
import com.studibox.app.ui.common.NoteCard
import com.studibox.app.ui.common.SubgroupSelector
import com.studibox.app.ui.common.TeacherLessonCard
import com.studibox.app.ui.common.TeacherLessonSlot
import com.studibox.app.ui.common.classifyLessons
import com.studibox.app.ui.common.classifyTeacherSlots
import com.studibox.app.ui.common.CurrentStudentSlotCard
import com.studibox.app.ui.common.StudentSlotCard
import com.studibox.app.ui.common.groupIntoSlots
import com.studibox.app.ui.lms.DeadlineCard
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.util.DateUtils
import java.time.LocalDate
import java.time.LocalTime

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 5 -> "Доброй ночи"
        hour < 12 -> "Доброе утро"
        hour < 18 -> "Добрый день"
        else -> "Добрый вечер"
    }
}

private val screenPadding = PaddingValues(
    start = Spacing.screenHorizontal,
    end = Spacing.screenHorizontal,
    top = 10.dp,
    bottom = 28.dp
)

/** Шапка приложения: логотип, название, обновление и уведомления. */
@Composable
private fun StudiboxTopBar(
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onOpenNotifications: () -> Unit
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(AppShapes.chip)
                    .background(colors.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = null,
                    tint = colors.textOnAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Студибокс",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Пункт 5 задачи: пока идёт обновление, кнопка недоступна и
            // показывает прогресс вместо иконки — чтобы нельзя было
            // запустить второй запрос поверх первого случайным двойным тапом.
            Box(
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .clickable(enabled = !refreshing, onClick = onRefresh)
                    .padding(8.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh_schedule),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            AppIconTile(
                icon = Icons.Filled.Notifications,
                size = 36.dp,
                contentDescription = "Уведомления",
                onClick = onOpenNotifications,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** Приветствие, группа (или ФИО преподавателя) и дата. */
@Composable
private fun GreetingBlock(subtitle: String?, date: LocalDate) {
    val colors = StudiboxTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = greeting(),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary
        )
        if (subtitle != null) {
            AppBadge(
                text = subtitle,
                containerColor = colors.accentContainer,
                contentColor = colors.onAccentContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            text = DateUtils.humanDateWithDay(date).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionDescription: String? = null
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary
        )
        if (onAction != null) {
            Box(
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(colors.accentContainer)
                    .clickable(onClick = onAction)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = actionDescription,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TodayScreen(
    onAddNote: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onGoToSchedule: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenLesson: (String) -> Unit = {},
    viewModel: TodayViewModel = viewModel()
) {
    val lessons by viewModel.lessons.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val group by viewModel.group.collectAsState()
    val subgroups by viewModel.availableSubgroups.collectAsState()
    val selectedSubgroup by viewModel.selectedSubgroup.collectAsState()
    val deadlines by viewModel.deadlines.collectAsState()
    val today = viewModel.today

    val role by viewModel.role.collectAsState()
    val teacherName by viewModel.teacher.collectAsState()
    val teacherSlots by viewModel.teacherSlots.collectAsState()

    if (role == UserRole.TEACHER) {
        TodayScreenTeacher(
            teacherName = teacherName,
            slots = teacherSlots,
            today = today,
            onRefresh = onRefresh,
            refreshing = refreshing,
            onOpenNotifications = onOpenNotifications,
            onOpenLesson = onOpenLesson
        )
        return
    }

    val states = remember(lessons) { classifyLessons(lessons, isToday = true) }
    // Пары разных подгрупп в одно время — одна карточка (см. StudentSlots.kt).
    val slots = remember(lessons) { groupIntoSlots(lessons) }
    val current = slots.filter { states[it.first.id] == LessonUiState.CURRENT }
    val upcoming = slots.filterNot { it in current }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudiboxTheme.colors.background),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
    ) {
        item(key = "top-bar") {
            StudiboxTopBar(
                onRefresh = onRefresh,
                refreshing = refreshing,
                onOpenNotifications = onOpenNotifications
            )
        }

        item(key = "greeting") {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                GreetingBlock(subtitle = group, date = today)
            }
        }

        if (subgroups.isNotEmpty()) {
            item(key = "subgroups") {
                SubgroupSelector(
                    subgroups = subgroups,
                    selected = selectedSubgroup,
                    onSelect = viewModel::selectSubgroup
                )
            }
        }

        if (lessons.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    text = stringResource(R.string.no_lessons_today),
                    actionLabel = "К расписанию",
                    onAction = onGoToSchedule
                )
            }
        } else {
            items(current, key = { "cur-${it.id}" }) { slot ->
                CurrentStudentSlotCard(slot, onOpenLesson = { onOpenLesson(it.subject) })
            }

            if (upcoming.isNotEmpty()) {
                item(key = "lessons-title") {
                    SectionRow(
                        title = if (current.isEmpty()) "Сегодня" else "Остальные пары",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(upcoming, key = { "lesson-${it.id}" }) { slot ->
                    StudentSlotCard(
                        slot,
                        state = states[slot.first.id] ?: LessonUiState.UPCOMING,
                        onOpenLesson = { onOpenLesson(it.subject) }
                    )
                }
            }
        }

        if (deadlines.isNotEmpty()) {
            item(key = "deadlines-title") {
                SectionRow(title = "Сроки сдачи", modifier = Modifier.padding(top = 12.dp))
            }
            items(deadlines, key = { "deadline-${it.id}" }) { assignment ->
                DeadlineCard(assignment)
            }
        }

        item(key = "notes-title") {
            SectionRow(
                title = stringResource(R.string.notes_today),
                modifier = Modifier.padding(top = 12.dp),
                onAction = { onAddNote(DateUtils.iso(today)) },
                actionDescription = stringResource(R.string.add_note)
            )
        }

        if (notes.isEmpty()) {
            item(key = "notes-empty") {
                Text(
                    text = stringResource(R.string.no_notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudiboxTheme.colors.textTertiary
                )
            }
        } else {
            items(notes, key = { "note-${it.id}" }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onDelete = { viewModel.deleteNote(note.id) }
                )
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }
}

/** Экран «Сегодня» для роли «Преподаватель»: только его пары, без группы и подгрупп. */
@Composable
private fun TodayScreenTeacher(
    teacherName: String?,
    slots: List<TeacherLessonSlot>,
    today: LocalDate,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onOpenNotifications: () -> Unit,
    onOpenLesson: (String) -> Unit
) {
    val states = remember(slots) { classifyTeacherSlots(slots, isToday = true) }
    val current = slots.filter { states[it.id] == LessonUiState.CURRENT }
    val rest = slots.filterNot { it in current }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudiboxTheme.colors.background),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
    ) {
        item(key = "top-bar") {
            StudiboxTopBar(
                onRefresh = onRefresh,
                refreshing = refreshing,
                onOpenNotifications = onOpenNotifications
            )
        }

        item(key = "greeting") {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                GreetingBlock(
                    subtitle = teacherName ?: "Преподаватель не выбран",
                    date = today
                )
            }
        }

        if (slots.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.no_lessons_today)) }
        } else {
            items(current, key = { "cur-${it.id}" }) { slot ->
                TeacherLessonCard(slot, state = LessonUiState.CURRENT, onClick = { onOpenLesson(slot.subject) })
            }
            if (rest.isNotEmpty()) {
                item(key = "rest-title") {
                    SectionRow(
                        title = if (current.isEmpty()) "Сегодня" else "Остальные пары",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(rest, key = { "rest-${it.id}" }) { slot ->
                    TeacherLessonCard(
                        slot,
                        state = states[slot.id] ?: LessonUiState.UPCOMING,
                        onClick = { onOpenLesson(slot.subject) }
                    )
                }
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }
}
