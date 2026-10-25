package com.studibox.app.ui.nav

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.ui.AppViewModel
import com.studibox.app.ui.common.AppBottomBar
import com.studibox.app.ui.common.BottomTab
import com.studibox.app.ui.common.OfflineBanner
import com.studibox.app.ui.groups.GroupPickerScreen
import com.studibox.app.ui.lms.LmsHomeScreen
import com.studibox.app.ui.lms.LmsLoginScreen
import com.studibox.app.ui.lms.LmsMaterialsScreen
import com.studibox.app.ui.lms.PdfViewerScreen
import com.studibox.app.ui.notes.NoteEditScreen
import com.studibox.app.ui.notes.NotesScreen
import com.studibox.app.ui.notifications.NotificationsScreen
import com.studibox.app.ui.onboarding.RolePickerScreen
import com.studibox.app.ui.schedule.ScheduleScreen
import com.studibox.app.ui.settings.SettingsScreen
import com.studibox.app.ui.teacher.TeacherPickerScreen
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.ui.today.TodayScreen

object Routes {
    const val ROLE_PICKER = "role_picker"
    const val GROUP_PICKER = "group_picker"
    const val TEACHER_PICKER = "teacher_picker"
    const val TODAY = "today"
    const val SCHEDULE = "schedule"
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    const val NOTE_EDIT = "note_edit"
    const val NOTIFICATIONS = "notifications"
    const val LMS = "lms"
    const val LMS_LOGIN = "lms_login"
    const val LMS_MATERIALS = "lms_materials"
    const val PDF_VIEWER = "pdf_viewer"

    /** Встроенный просмотр скачанной лекции (путь внутри filesDir приложения). */
    fun pdfViewer(path: String, title: String) =
        "$PDF_VIEWER?path=${Uri.encode(path)}&title=${Uri.encode(title)}"

    /** Лекции пары: курс СДО подбирается по названию предмета. */
    fun lessonMaterials(subject: String) = "$LMS_MATERIALS?subject=${Uri.encode(subject)}"

    fun courseMaterials(courseId: Int) = "$LMS_MATERIALS?courseId=$courseId"
}

private val tabs = listOf(
    BottomTab(Routes.TODAY, "Сегодня", Icons.Filled.Today),
    BottomTab(Routes.SCHEDULE, "Расписание", Icons.Filled.CalendarMonth),
    BottomTab(Routes.LMS, "СДО", Icons.Filled.School),
    BottomTab(Routes.NOTES, "Заметки", Icons.Filled.EditNote),
    BottomTab(Routes.SETTINGS, "Настройки", Icons.Filled.Settings)
)

@Composable
fun StudiboxNavHost(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val settings by appViewModel.settings.collectAsState()
    val sync by appViewModel.sync.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Есть ли экран, на который реально можно вернуться. Используется и для
    // видимости кнопки «Назад» в меню выбора, и для системной кнопки Back —
    // пункт 1 задачи: назад должен возвращать на предыдущий экран, а не
    // закрывать приложение, но только там, где действительно есть куда
    // возвращаться (на стартовом экране системный back как и раньше выходит
    // из приложения — это ожидаемое поведение Android).
    val canGoBack = navController.previousBackStackEntry != null
    BackHandler(enabled = canGoBack) { navController.popBackStack() }

    val current = settings
    val role = current?.userRole ?: UserRole.STUDENT

    // «Настроено» — по студенту выбрана группа, либо по преподавателю выбрано ФИО.
    // Существующие пользователи (группа была выбрана ещё до появления ролей)
    // считаются настроенными автоматически, roleChosen для них неважен.
    val configured = when (role) {
        UserRole.STUDENT -> current?.selectedGroup != null
        UserRole.TEACHER -> current?.selectedTeacher != null
    }

    // Экран выбора роли показывается только тем, кто ни разу explicitly её не
    // выбирал и ещё ничего не настроил — то есть по-настоящему первый запуск.
    val needsRolePicker = current != null && !configured && !current.roleChosen

    val showBottomBar = configured &&
        currentRoute in setOf(Routes.TODAY, Routes.SCHEDULE, Routes.LMS, Routes.NOTES, Routes.SETTINGS)

    // Если конфигурация сброшена (группа/преподаватель пропали из данных или
    // пользователь сменил роль в настройках) — возвращаем на нужный экран выбора.
    LaunchedEffect(configured, role, needsRolePicker) {
        if (current == null) return@LaunchedEffect
        val target = when {
            needsRolePicker -> Routes.ROLE_PICKER
            configured -> null
            role == UserRole.TEACHER -> Routes.TEACHER_PICKER
            else -> Routes.GROUP_PICKER
        }
        if (target != null && currentRoute != null && currentRoute != target) {
            if (needsRolePicker) {
                // Подлинный первый запуск — предыдущих экранов нет и быть не должно.
                navController.navigate(target) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                }
            } else {
                // Роль переключили ПРЯМО В НАСТРОЙКАХ, новая роль ещё не
                // настроена. Раньше здесь тоже был popUpTo(inclusive = true),
                // который стирал весь back stack — из-за этого «Назад» с
                // этого экрана закрывал приложение, хотя пользователь пришёл
                // из «Настроек» и логично было бы туда и вернуться. Обычный
                // navigate() сохраняет «Настройки» в стеке.
                navController.navigate(target)
            }
        }
    }

    LaunchedEffect(sync.message) {
        sync.message?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (sync.offline) "Повторить" else null
            )
            if (result == SnackbarResult.ActionPerformed) {
                appViewModel.refresh(force = true)
            }
            appViewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = StudiboxTheme.colors.background,
        // Плашка в тетрадном стиле: чернила вместо стандартной чёрной.
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = AppShapes.card,
                    containerColor = StudiboxTheme.colors.accent,
                    contentColor = StudiboxTheme.colors.textOnAccent,
                    actionColor = StudiboxTheme.colors.textOnAccent
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    tabs = tabs,
                    isSelected = { tab ->
                        currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    },
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (sync.offline) {
                OfflineBanner("Нет подключения. Показаны сохранённые данные.")
            }

            NavHost(
                navController = navController,
                startDestination = when {
                    needsRolePicker -> Routes.ROLE_PICKER
                    configured -> Routes.TODAY
                    role == UserRole.TEACHER -> Routes.TEACHER_PICKER
                    else -> Routes.GROUP_PICKER
                },
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.ROLE_PICKER) {
                    RolePickerScreen(
                        onRoleChosen = { chosenRole ->
                            appViewModel.setUserRole(chosenRole)
                            val next = if (chosenRole == UserRole.TEACHER) Routes.TEACHER_PICKER else Routes.GROUP_PICKER
                            navController.navigate(next) {
                                popUpTo(Routes.ROLE_PICKER) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.GROUP_PICKER) {
                    GroupPickerScreen(
                        onGroupSelected = {
                            navController.navigate(Routes.TODAY) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        },
                        // Видимость кнопки «Назад» зависит от того, есть ли
                        // реально предыдущий экран, а не от бизнес-статуса
                        // "configured" — иначе при переключении роли без
                        // настроенной новой роли кнопка пропадала именно
                        // тогда, когда она нужнее всего.
                        showBack = canGoBack,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.TEACHER_PICKER) {
                    TeacherPickerScreen(
                        onTeacherSelected = {
                            navController.navigate(Routes.TODAY) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        },
                        showBack = canGoBack,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.TODAY) {
                    TodayScreen(
                        onAddNote = { date -> navController.navigate("${Routes.NOTE_EDIT}/0?date=$date") },
                        onOpenNote = { id -> navController.navigate("${Routes.NOTE_EDIT}/$id") },
                        onRefresh = { appViewModel.refresh(force = true) },
                        refreshing = sync.loading,
                        onGoToSchedule = {
                            navController.navigate(Routes.SCHEDULE) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onOpenLesson = { subject -> navController.navigate(Routes.lessonMaterials(subject)) }
                    )
                }

                composable(Routes.SCHEDULE) {
                    ScheduleScreen(
                        onAddNote = { date -> navController.navigate("${Routes.NOTE_EDIT}/0?date=$date") },
                        onOpenNote = { id -> navController.navigate("${Routes.NOTE_EDIT}/$id") },
                        onRefresh = { appViewModel.refresh(force = true) },
                        refreshing = sync.loading,
                        onOpenLesson = { subject -> navController.navigate(Routes.lessonMaterials(subject)) }
                    )
                }

                composable(Routes.LMS) {
                    LmsHomeScreen(
                        onLogin = { navController.navigate(Routes.LMS_LOGIN) },
                        onOpenCourse = { id -> navController.navigate(Routes.courseMaterials(id)) },
                        onOpenLesson = { subject -> navController.navigate(Routes.lessonMaterials(subject)) },
                        onChangeTeacher = { navController.navigate(Routes.TEACHER_PICKER) },
                        onOpenPdf = { path, title -> navController.navigate(Routes.pdfViewer(path, title)) }
                    )
                }

                composable(
                    route = "${Routes.PDF_VIEWER}?path={path}&title={title}",
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType; defaultValue = "" },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { entry ->
                    PdfViewerScreen(
                        path = entry.arguments?.getString("path").orEmpty(),
                        title = entry.arguments?.getString("title").orEmpty().ifBlank { "Лекция" },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.LMS_LOGIN) {
                    LmsLoginScreen(
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${Routes.LMS_MATERIALS}?subject={subject}&courseId={courseId}",
                    arguments = listOf(
                        navArgument("subject") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("courseId") {
                            type = NavType.IntType
                            defaultValue = -1
                        }
                    )
                ) { entry ->
                    LmsMaterialsScreen(
                        subject = entry.arguments?.getString("subject"),
                        courseId = entry.arguments?.getInt("courseId")?.takeIf { it > 0 },
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.LMS_LOGIN) },
                        onOpenPdf = { path, title -> navController.navigate(Routes.pdfViewer(path, title)) }
                    )
                }

                composable(Routes.NOTES) {
                    NotesScreen(
                        onAddNote = { navController.navigate("${Routes.NOTE_EDIT}/0") },
                        onOpenNote = { id -> navController.navigate("${Routes.NOTE_EDIT}/$id") }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        appViewModel = appViewModel,
                        onChangeGroup = { navController.navigate(Routes.GROUP_PICKER) },
                        onChangeTeacher = { navController.navigate(Routes.TEACHER_PICKER) },
                        onOpenLms = { navController.navigate(Routes.LMS_LOGIN) }
                    )
                }

                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        appViewModel = appViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${Routes.NOTE_EDIT}/{noteId}?date={date}",
                    arguments = listOf(
                        navArgument("noteId") {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                        navArgument("date") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    NoteEditScreen(
                        noteId = entry.arguments?.getLong("noteId") ?: 0L,
                        presetDate = entry.arguments?.getString("date"),
                        onDone = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
