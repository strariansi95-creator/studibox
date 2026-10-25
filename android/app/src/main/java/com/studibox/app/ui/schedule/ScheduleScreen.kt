package com.studibox.app.ui.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.R
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.ui.common.AppBadge
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppIconTile
import com.studibox.app.ui.common.AppSegmented
import com.studibox.app.ui.common.EmptyState
import com.studibox.app.ui.common.LessonCard
import com.studibox.app.ui.common.LessonUiState
import com.studibox.app.ui.common.NoteCard
import com.studibox.app.ui.common.SubgroupSelector
import com.studibox.app.ui.common.TeacherLessonCard
import com.studibox.app.ui.common.classifyLessons
import com.studibox.app.ui.common.classifyTeacherSlots
import com.studibox.app.ui.common.StudentSlotCard
import com.studibox.app.ui.common.groupIntoSlots
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.util.DateUtils
import java.time.LocalDate

private const val MODE_WEEK = 0
private const val MODE_DAY = 1

@Composable
fun ScheduleScreen(
    onAddNote: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
    onOpenLesson: (String) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val colors = StudiboxTheme.colors

    val lessons by viewModel.lessons.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val selectedDay by viewModel.selectedDayOfWeek.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val availableDates by viewModel.availableDates.collectAsState()
    val group by viewModel.group.collectAsState()
    val subgroups by viewModel.availableSubgroups.collectAsState()
    val selectedSubgroup by viewModel.selectedSubgroup.collectAsState()

    val role by viewModel.role.collectAsState()
    val teacherName by viewModel.teacher.collectAsState()
    val teacherSlots by viewModel.teacherSlots.collectAsState()
    val teacherAvailableDates by viewModel.teacherAvailableDates.collectAsState()
    val isTeacher = role == UserRole.TEACHER

    val today = LocalDate.now()
    val isToday = selectedDate == today
    val states = remember(lessons, isToday) { classifyLessons(lessons, isToday) }
    // Пары разных подгрупп в одно время — одна карточка (см. StudentSlots.kt).
    val slots = remember(lessons) { groupIntoSlots(lessons) }
    val teacherStates = remember(teacherSlots, isToday) { classifyTeacherSlots(teacherSlots, isToday) }
    val dates = if (isTeacher) teacherAvailableDates else availableDates

    var mode by remember { mutableIntStateOf(MODE_DAY) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Расписание",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isToday) {
                        Text(
                            text = "Сегодня",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(AppShapes.pill)
                                .background(colors.accentContainer)
                                .clickable { viewModel.goToToday() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    // Пункт 5 задачи: та же недоступность во время
                    // обновления, что и на экране «Сегодня» — кнопка не
                    // даёт запустить второй запрос поверх первого.
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
                }
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconTile(icon = Icons.Filled.CalendarMonth, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isTeacher) {
                        teacherName ?: "Преподаватель не выбран"
                    } else {
                        group ?: "Группа не выбрана"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
            }

            AppSegmented(
                options = listOf("Неделя", "День"),
                selectedIndex = mode,
                onSelect = { mode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            )

            WeekSwitcher(
                weekStart = weekStart,
                onPrevious = viewModel::previousWeek,
                onNext = viewModel::nextWeek,
                modifier = Modifier.padding(top = 12.dp)
            )

            if (mode == MODE_DAY) {
                DaySelector(
                    weekStart = weekStart,
                    selectedDayOfWeek = selectedDay,
                    availableDates = dates,
                    onSelect = viewModel::selectDay,
                    modifier = Modifier.padding(top = 10.dp)
                )

                if (!isTeacher && subgroups.isNotEmpty()) {
                    SubgroupSelector(
                        subgroups = subgroups,
                        selected = selectedSubgroup,
                        onSelect = viewModel::selectSubgroup,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        if (mode == MODE_WEEK) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    top = 14.dp,
                    bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
            ) {
                items(DateUtils.STUDY_DAYS, key = { "day-$it" }) { dayOfWeek ->
                    val date = weekStart.plusDays((dayOfWeek - 1).toLong())
                    DayOverviewCard(
                        date = date,
                        dayOfWeek = dayOfWeek,
                        selected = dayOfWeek == selectedDay,
                        hasLessons = dates.contains(date),
                        isToday = date == today,
                        onClick = {
                            viewModel.selectDay(dayOfWeek)
                            mode = MODE_DAY
                        }
                    )
                }
            }
        } else {

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = 14.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
        ) {
            if (isTeacher) {
                if (teacherSlots.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            text = if (teacherAvailableDates.isEmpty()) {
                                "Расписание пока недоступно"
                            } else {
                                stringResource(R.string.no_lessons_day)
                            },
                            actionLabel = if (teacherAvailableDates.isEmpty()) "Обновить" else null,
                            onAction = if (teacherAvailableDates.isEmpty()) onRefresh else null
                        )
                    }
                } else {
                    items(teacherSlots, key = { it.id }) { slot ->
                        TeacherLessonCard(
                            slot,
                            state = teacherStates[slot.id] ?: LessonUiState.UPCOMING,
                            onClick = { onOpenLesson(slot.subject) }
                        )
                    }
                }
            } else if (lessons.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        text = if (availableDates.isEmpty()) {
                            "Расписание пока недоступно"
                        } else {
                            stringResource(R.string.no_lessons_day)
                        },
                        actionLabel = if (availableDates.isEmpty()) "Обновить" else null,
                        onAction = if (availableDates.isEmpty()) onRefresh else null
                    )
                }
            } else {
                items(slots, key = { it.id }) { slot ->
                    StudentSlotCard(
                        slot,
                        state = states[slot.first.id] ?: LessonUiState.UPCOMING,
                        onOpenLesson = { onOpenLesson(it.subject) }
                    )
                }
            }

            item(key = "notes-title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tab_notes),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(AppShapes.pill)
                            .background(colors.accentContainer)
                            .clickable { onAddNote(DateUtils.iso(selectedDate)) }
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_note),
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                item(key = "notes-empty") {
                    Text(
                        text = stringResource(R.string.no_notes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary
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
    }
}

/** Переключение недели: «‹ 14 сентября — 19 сентября ›». */
@Composable
private fun WeekSwitcher(
    weekStart: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.chip)
            .background(colors.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onPrevious)
                .padding(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Предыдущая неделя",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = "${DateUtils.humanDate(weekStart)} — ${DateUtils.humanDate(weekStart.plusDays(5))}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onNext)
                .padding(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Следующая неделя",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Полоса дней недели: Пн 14 … Сб 19. Выбранный день — фиолетовый. */
@Composable
private fun DaySelector(
    weekStart: LocalDate,
    selectedDayOfWeek: Int,
    availableDates: Set<LocalDate>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    val today = LocalDate.now()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DateUtils.STUDY_DAYS.forEach { dayOfWeek ->
            val date = weekStart.plusDays((dayOfWeek - 1).toLong())
            val selected = dayOfWeek == selectedDayOfWeek
            val hasLessons = availableDates.contains(date)
            val isToday = date == today

            val background by animateColorAsState(
                targetValue = when {
                    selected -> colors.accent
                    isToday -> colors.accentContainer
                    else -> colors.surfaceVariant
                },
                animationSpec = tween(180),
                label = "dayBg"
            )
            val content by animateColorAsState(
                targetValue = when {
                    selected -> colors.textOnAccent
                    isToday -> colors.onAccentContainer
                    else -> colors.textSecondary
                },
                animationSpec = tween(180),
                label = "dayContent"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.chip)
                    .background(background)
                    .clickable { onSelect(dayOfWeek) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = DateUtils.dayShort(dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = content
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !hasLessons -> androidx.compose.ui.graphics.Color.Transparent
                                selected -> colors.textOnAccent
                                else -> colors.accent
                            }
                        )
                )
            }
        }
    }
}

/** Карточка дня в режиме «Неделя». */
@Composable
private fun DayOverviewCard(
    date: LocalDate,
    dayOfWeek: Int,
    selected: Boolean,
    hasLessons: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val colors = StudiboxTheme.colors
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = if (selected) colors.accentContainer else colors.surface,
        borderColor = if (selected) colors.accent else colors.outline,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconTile(icon = Icons.Filled.CalendarMonth, size = 38.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateUtils.dayFull(dayOfWeek),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                    if (isToday) {
                        Spacer(Modifier.width(8.dp))
                        AppBadge(text = "сегодня")
                    }
                }
                Text(
                    text = DateUtils.humanDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasLessons) colors.textSecondary else colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
