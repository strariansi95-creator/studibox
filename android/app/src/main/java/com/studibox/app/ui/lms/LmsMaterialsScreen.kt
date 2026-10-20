package com.studibox.app.ui.lms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.ui.common.AppSecondaryButton
import com.studibox.app.ui.common.AppSegmented
import com.studibox.app.ui.common.AppStatePlaceholder
import com.studibox.app.ui.common.ErrorBanner
import com.studibox.app.ui.common.LoadingState
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme

/**
 * Лекции пары: [subject] — предмет из расписания (курс подбирается сам),
 * либо [courseId] — курс, выбранный в списке «Мои курсы».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LmsMaterialsScreen(
    subject: String?,
    courseId: Int?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenPdf: (path: String, title: String) -> Unit,
    viewModel: LmsMaterialsViewModel = viewModel()
) {
    val colors = StudiboxTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val loggedIn by viewModel.loggedIn.collectAsState()
    val downloading by viewModel.downloading.collectAsState()
    val downloaded by viewModel.downloaded.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.openRequests.collect { request ->
            when (request) {
                is LmsOpenRequest.Link -> openLmsUrl(context, request.url)
                is LmsOpenRequest.Pdf -> onOpenPdf(request.file.absolutePath, request.title)
                is LmsOpenRequest.OtherFile -> openDownloadedFile(context, request.file, request.mimeType)
                is LmsOpenRequest.Message -> snackbar.showSnackbar(request.text)
            }
        }
    }

    // Перезагрузка и после возврата с экрана входа: loggedIn станет true.
    LaunchedEffect(subject, courseId, loggedIn) {
        if (loggedIn != null) viewModel.load(subject, courseId)
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textSecondary,
                    actionIconContentColor = colors.textSecondary
                ),
                title = {
                    Text(
                        text = subject ?: state.course?.name ?: "Материалы",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.course != null && !state.showPicker && state.sections.isNotEmpty()) {
                        IconButton(onClick = viewModel::downloadAll) {
                            Icon(Icons.Filled.DownloadForOffline, contentDescription = "Скачать все для офлайна")
                        }
                    }
                    if (loggedIn == true) {
                        IconButton(onClick = { viewModel.load(subject, courseId, force = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when {
            loggedIn == false || state.needsLogin -> AppStatePlaceholder(
                icon = Icons.AutoMirrored.Filled.Login,
                title = "Войдите в СДО СВФУ",
                description = state.error.takeIf { state.needsLogin }
                    ?: "Чтобы открыть лекции этой пары, войдите с логином и паролем от СДО.",
                actionLabel = "Войти",
                onAction = onLogin,
                modifier = modifier
            )

            state.loading || loggedIn == null -> LoadingState("Загружаем лекции…", modifier)

            else -> LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    top = 8.dp,
                    bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
            ) {
                state.error?.let { error ->
                    item(key = "error") {
                        ErrorBanner(
                            message = error,
                            onRetry = { viewModel.load(subject, courseId, force = true) }
                        )
                    }
                }

                if (state.showPicker) {
                    coursePicker(state, subject, viewModel)
                } else {
                    materials(state, subject, viewModel, downloaded, downloading)
                }

                item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.coursePicker(
    state: LmsMaterialsState,
    subject: String?,
    viewModel: LmsMaterialsViewModel
) {
    item(key = "picker-title") {
        Column {
            Text(
                text = if (state.course == null) "Курс не найден автоматически" else "Выберите курс",
                style = MaterialTheme.typography.titleMedium,
                color = StudiboxTheme.colors.textPrimary
            )
            Text(
                text = if (subject != null) {
                    "Укажите, какой курс СДО относится к «$subject». Выбор запомнится."
                } else {
                    "Выберите курс из списка."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = StudiboxTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    if (state.allCourses.isEmpty()) {
        item(key = "no-courses") {
            AppStatePlaceholder(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Вы не записаны ни на один курс"
            )
        }
    }
    items(state.allCourses, key = { "course-${it.id}" }) { course ->
        LmsCourseCard(
            course = course,
            selected = course.id == state.course?.id,
            onClick = { viewModel.chooseCourse(course) }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.materials(
    state: LmsMaterialsState,
    subject: String?,
    viewModel: LmsMaterialsViewModel,
    downloaded: Set<String>,
    downloading: Set<String>
) {
    val course = state.course ?: return
    item(key = "course") {
        Column {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium,
                color = StudiboxTheme.colors.textPrimary
            )
            if (subject != null) {
                AppSecondaryButton(
                    text = "Другой курс",
                    onClick = viewModel::changeCourse,
                    icon = Icons.Filled.SwapHoriz,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }

    if (state.assignments.isNotEmpty()) {
        item(key = "assignments-title") {
            Text(
                text = "Задания и сроки сдачи",
                style = MaterialTheme.typography.labelLarge,
                color = StudiboxTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }
        items(state.assignments, key = { "assign-${it.id}" }) { assignment ->
            DeadlineCard(assignment, showCourse = false)
        }
    }

    if (state.hasLectures) {
        item(key = "filter") {
            AppSegmented(
                options = listOf("Лекции", "Все материалы"),
                selectedIndex = if (state.lecturesOnly) 0 else 1,
                onSelect = { viewModel.setLecturesOnly(it == 0) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val sections = state.visibleSections
    if (sections.isEmpty()) {
        item(key = "empty") {
            AppStatePlaceholder(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "В курсе пока нет материалов"
            )
        }
    }
    sections.forEach { section ->
        if (section.name.isNotBlank()) {
            item(key = "section-${section.id}") {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = StudiboxTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }
        items(section.modules, key = { "module-${section.id}-${it.id}" }) { module ->
            LmsModuleCard(
                module = module,
                downloadedKeys = downloaded,
                downloadingKeys = downloading,
                onOpenFile = { file -> viewModel.openFile(module, file) }
            )
        }
    }
}
