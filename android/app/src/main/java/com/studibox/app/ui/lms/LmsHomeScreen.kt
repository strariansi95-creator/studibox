package com.studibox.app.ui.lms

import com.studibox.app.ui.theme.notebookPaper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.data.repo.DownloadedLecture
import com.studibox.app.data.repo.LectureStorage
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppIconTile
import com.studibox.app.ui.common.AppInitialsAvatar
import com.studibox.app.ui.common.AppSecondaryButton
import com.studibox.app.ui.common.AppStatePlaceholder
import com.studibox.app.ui.common.ErrorBanner
import com.studibox.app.ui.common.LessonUiState
import com.studibox.app.ui.common.LoadingState
import com.studibox.app.ui.common.TeacherLessonCard
import com.studibox.app.ui.common.classifyTeacherSlots
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.util.DateUtils
import java.time.LocalDate

/**
 * Вкладка «СДО»: студенту — его курсы с лекциями, преподавателю — ещё и
 * его пары на две недели вперёд; нажатие на пару открывает её лекции.
 */
@Composable
fun LmsHomeScreen(
    onLogin: () -> Unit,
    onOpenCourse: (Int) -> Unit,
    onOpenLesson: (String) -> Unit,
    onChangeTeacher: () -> Unit,
    onOpenPdf: (path: String, title: String) -> Unit,
    viewModel: LmsHomeViewModel = viewModel()
) {
    val colors = StudiboxTheme.colors
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState()
    val openDownload: (DownloadedLecture) -> Unit = { lecture ->
        if (LectureStorage.isPdf(lecture.file)) onOpenPdf(lecture.file.absolutePath, lecture.name)
        else openDownloadedFile(context, lecture.file, "")
    }
    val session by viewModel.session.collectAsState()
    val sessionLoaded by viewModel.sessionLoaded.collectAsState()
    val role by viewModel.role.collectAsState()
    val teacherName by viewModel.teacher.collectAsState()
    val slots by viewModel.teacherSlots.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val isTeacher = role == UserRole.TEACHER

    LaunchedEffect(session?.userId) {
        if (session != null) viewModel.loadCourses()
    }

    val today = LocalDate.now()
    val todayIso = DateUtils.iso(today)
    val todayStates = remember(slots) {
        classifyTeacherSlots(slots.filter { it.date == todayIso }, isToday = true)
    }
    val slotsByDate = remember(slots) { slots.groupBy { it.date } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .notebookPaper(),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            top = 10.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
    ) {
        item(key = "title") {
            Text(
                text = "СДО СВФУ",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary
            )
        }

        val currentSession = session
        if (!sessionLoaded) {
            item(key = "loading") { LoadingState("Загружаем…") }
            return@LazyColumn
        }
        if (currentSession == null) {
            item(key = "login") {
                AppStatePlaceholder(
                    icon = Icons.Filled.School,
                    title = "Войдите в СДО СВФУ",
                    description = if (isTeacher) {
                        "Ваши пары и лекции курсов — в одном месте. Нажмите на пару, чтобы открыть её лекции."
                    } else {
                        "Лекции к парам прямо из расписания: просто нажмите на пару."
                    },
                    actionLabel = "Войти",
                    onAction = onLogin
                )
            }
            downloadsSection(downloads, openDownload, viewModel::deleteDownload)
            return@LazyColumn
        }

        item(key = "profile") {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.cardPadding)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppInitialsAvatar(text = currentSession.fullName.ifBlank { currentSession.userName })
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = currentSession.fullName.ifBlank { currentSession.userName },
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (isTeacher) "Преподаватель, вход в СДО выполнен" else "Студент, вход в СДО выполнен",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                AppSecondaryButton(
                    text = "Выйти из СДО",
                    onClick = viewModel::logout,
                    icon = Icons.AutoMirrored.Filled.Logout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }

        if (courses.deadlines.isNotEmpty()) {
            item(key = "deadlines-title") { SectionTitle(if (isTeacher) "Сроки сдачи у студентов" else "Сроки сдачи") }
            items(courses.deadlines, key = { "deadline-${it.id}" }) { assignment ->
                DeadlineCard(assignment)
            }
        }

        if (isTeacher) {
            item(key = "pairs-title") { SectionTitle("Мои пары") }
            when {
                teacherName == null -> item(key = "pairs-no-teacher") {
                    AppStatePlaceholder(
                        icon = Icons.Filled.School,
                        title = "Не выбрано ФИО",
                        description = "Выберите себя в списке преподавателей, чтобы видеть свои пары.",
                        actionLabel = "Выбрать",
                        onAction = onChangeTeacher
                    )
                }
                slots.isEmpty() -> item(key = "pairs-empty") {
                    Text(
                        text = "В ближайшие две недели пар нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary
                    )
                }
                else -> slotsByDate.forEach { (dateIso, daySlots) ->
                    item(key = "date-$dateIso") {
                        val date = DateUtils.parseDate(dateIso)
                        Text(
                            text = when {
                                date == null -> dateIso
                                date == today -> "Сегодня, ${DateUtils.humanDate(date)}"
                                date == today.plusDays(1) -> "Завтра, ${DateUtils.humanDate(date)}"
                                else -> DateUtils.humanDateWithDay(date).replaceFirstChar { it.uppercase() }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                        )
                    }
                    items(daySlots, key = { "slot-${it.id}" }) { slot ->
                        TeacherLessonCard(
                            slot,
                            state = todayStates[slot.id] ?: LessonUiState.UPCOMING,
                            onClick = { onOpenLesson(slot.subject) }
                        )
                    }
                }
            }
        }

        item(key = "courses-title") { SectionTitle("Мои курсы") }
        when {
            courses.loading -> item(key = "courses-loading") { LoadingState("Загружаем курсы…") }
            courses.error != null -> item(key = "courses-error") {
                ErrorBanner(message = courses.error.orEmpty(), onRetry = { viewModel.loadCourses(force = true) })
            }
            courses.courses.isEmpty() -> item(key = "courses-empty") {
                Text(
                    text = "Вы не записаны ни на один курс",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary
                )
            }
            else -> items(courses.courses, key = { "course-${it.id}" }) { course ->
                LmsCourseCard(course = course, onClick = { onOpenCourse(course.id) })
            }
        }

        downloadsSection(downloads, openDownload, viewModel::deleteDownload)

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }
}

/** «Скачанные лекции»: открываются без интернета, лишнее можно удалить. */
private fun LazyListScope.downloadsSection(
    downloads: List<DownloadedLecture>,
    onOpen: (DownloadedLecture) -> Unit,
    onDelete: (DownloadedLecture) -> Unit
) {
    if (downloads.isEmpty()) return
    item(key = "downloads-title") {
        SectionTitle("Скачанные лекции, ${formatSize(downloads.sumOf { it.sizeBytes })}")
    }
    downloads.groupBy { it.courseName }.forEach { (courseName, lectures) ->
        item(key = "downloads-course-${lectures.first().courseId}") {
            Text(
                text = courseName.ifBlank { "Курс" },
                style = MaterialTheme.typography.labelLarge,
                color = StudiboxTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
        items(lectures, key = { "download-${it.file.absolutePath}" }) { lecture ->
            DownloadedLectureCard(lecture, onOpen = { onOpen(lecture) }, onDelete = { onDelete(lecture) })
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale("ru"), "%.1f МБ", bytes / 1024f / 1024f)
    else -> "${(bytes / 1024).coerceAtLeast(1)} КБ"
}

@Composable
private fun DownloadedLectureCard(lecture: DownloadedLecture, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = StudiboxTheme.colors
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconTile(icon = Icons.Filled.DownloadDone, size = 34.dp, tint = colors.success,
                containerColor = colors.success.copy(alpha = 0.14f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = lecture.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSize(lecture.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить с телефона", tint = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = StudiboxTheme.colors.textPrimary,
        modifier = Modifier.padding(top = 12.dp)
    )
}
