package com.studibox.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.data.prefs.ThemeMode
import com.studibox.app.notifications.DeadlineNotificationScheduler
import com.studibox.app.notifications.LessonNotificationScheduler
import com.studibox.app.notifications.NotificationHelper
import com.studibox.app.sync.SyncScheduler
import com.studibox.app.ui.AppViewModel
import com.studibox.app.ui.nav.StudiboxNavHost
import com.studibox.app.ui.theme.StudiboxTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Отказ в разрешении — не ошибка: приложение работает без уведомлений.
            if (granted) {
                LessonNotificationScheduler.rescheduleToday(this)
                DeadlineNotificationScheduler.reschedule(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.init(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val settings by appViewModel.settings.collectAsState()

            StudiboxTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM) {
                if (settings == null) {
                    // Настройки ещё читаются из DataStore.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LaunchedEffect(Unit) {
                        appViewModel.refresh(force = false)
                        SyncScheduler.syncNow(this@MainActivity)
                    }
                    // Сменили роль, группу, ФИО, время напоминания или вошли/вышли
                    // из СДО — переставляем напоминания сразу, а не при следующем запуске.
                    val lmsSession by appViewModel.lmsSession.collectAsState()
                    val current = settings
                    LaunchedEffect(
                        current?.userRole, current?.selectedGroup, current?.selectedTeacher,
                        current?.notificationsEnabled, current?.reminderMinutes
                    ) {
                        LessonNotificationScheduler.rescheduleToday(this@MainActivity)
                    }
                    LaunchedEffect(current?.userRole, current?.notificationsEnabled, lmsSession?.userId) {
                        DeadlineNotificationScheduler.reschedule(this@MainActivity)
                    }
                    StudiboxNavHost(appViewModel = appViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LessonNotificationScheduler.rescheduleToday(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasPermission(this)
        ) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
