package com.studibox.app.ui.settings

import com.studibox.app.ui.theme.notebookPaper
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studibox.app.BuildConfig
import com.studibox.app.data.prefs.ThemeMode
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.ui.AppViewModel
import com.studibox.app.ui.common.AppBadge
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppInitialsAvatar
import com.studibox.app.ui.common.AppRowDivider
import com.studibox.app.ui.common.AppSecondaryButton
import com.studibox.app.ui.common.AppSegmented
import com.studibox.app.ui.common.ReminderMinutesSelector
import com.studibox.app.ui.common.SettingsItem
import com.studibox.app.ui.common.SettingsSwitchItem
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.util.SubgroupFormat

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = StudiboxTheme.colors.textTertiary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onChangeGroup: () -> Unit,
    onChangeTeacher: () -> Unit,
    onOpenLms: () -> Unit
) {
    val colors = StudiboxTheme.colors
    val settings by appViewModel.settings.collectAsState()
    val lmsSession by appViewModel.lmsSession.collectAsState()
    val sync by appViewModel.sync.collectAsState()
    val version by appViewModel.version.collectAsState()
    val current = settings ?: return

    val isTeacher = current.userRole == UserRole.TEACHER
    val profileName = if (isTeacher) {
        current.selectedTeacher ?: "Не выбран"
    } else {
        current.selectedGroup ?: "Не выбрана"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .notebookPaper()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = 10.dp, bottom = 28.dp)
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary
        )

        // ---------------------------------------------------------- профиль
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            contentPadding = PaddingValues(Spacing.cardPadding)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppInitialsAvatar(text = profileName, size = 48.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (isTeacher) "Преподаватель" else "Студент",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (!isTeacher && current.selectedSubgroup != null) {
                    AppBadge(text = SubgroupFormat.fullLabel(current.selectedSubgroup))
                }
            }

            AppSecondaryButton(
                text = if (isTeacher) "Сменить преподавателя" else "Сменить группу",
                onClick = if (isTeacher) onChangeTeacher else onChangeGroup,
                icon = if (isTeacher) Icons.Filled.Person else Icons.Filled.Groups,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            )
        }

        // ---------------------------------------------------------- роль
        Spacer(Modifier.height(Spacing.betweenSections))
        SettingsGroupTitle("Тип пользователя")
        AppSegmented(
            options = listOf("Студент", "Преподаватель"),
            selectedIndex = if (isTeacher) 1 else 0,
            onSelect = { index ->
                appViewModel.setUserRole(
                    if (index == 1) UserRole.TEACHER else UserRole.STUDENT
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (isTeacher) {
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                SettingsItem(
                    icon = Icons.Filled.Person,
                    title = "Преподаватели",
                    subtitle = current.selectedTeacher ?: "Выберите своё ФИО",
                    onClick = onChangeTeacher,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }

        // ---------------------------------------------------------- СДО
        Spacer(Modifier.height(Spacing.betweenSections))
        SettingsGroupTitle("СДО СВФУ")
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            val session = lmsSession
            if (session == null) {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = "Войти в СДО",
                    subtitle = if (isTeacher) "Ваши пары и лекции курсов" else "Лекции к парам из расписания",
                    onClick = onOpenLms,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            } else {
                SettingsItem(
                    icon = Icons.Filled.School,
                    title = session.fullName.ifBlank { session.userName },
                    subtitle = "Вход выполнен"
                )
                AppRowDivider()
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Выйти из СДО",
                    onClick = appViewModel::lmsLogout
                )
            }
        }

        // ---------------------------------------------------------- уведомления
        Spacer(Modifier.height(Spacing.betweenSections))
        SettingsGroupTitle("Уведомления")
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            SettingsSwitchItem(
                icon = Icons.Filled.NotificationsActive,
                title = "Напоминания о занятиях",
                subtitle = "За ${current.reminderMinutes} минут до начала",
                checked = current.notificationsEnabled,
                onCheckedChange = appViewModel::setNotifications
            )

            if (current.notificationsEnabled) {
                AppRowDivider()
                ReminderMinutesSelector(
                    selectedMinutes = current.reminderMinutes,
                    onSelect = appViewModel::setReminderMinutes,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        // ---------------------------------------------------------- данные
        Spacer(Modifier.height(Spacing.betweenSections))
        SettingsGroupTitle("Данные")
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            SettingsItem(
                icon = Icons.Filled.Refresh,
                title = "Обновить расписание",
                subtitle = if (sync.loading) "Обновляем…" else "Версия: ${version ?: "неизвестна"}",
                onClick = { if (!sync.loading) appViewModel.refresh(force = true) },
                trailing = {
                    if (sync.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            AppRowDivider()
            SettingsItem(
                icon = Icons.Filled.CloudSync,
                title = "Автообновление",
                subtitle = "Фоновая проверка примерно раз в 30 минут"
            )
        }

        sync.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (sync.offline) colors.danger else colors.accent,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp)
            )
        }

        // ---------------------------------------------------------- оформление
        Spacer(Modifier.height(Spacing.betweenSections))
        SettingsGroupTitle("Оформление")
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            SettingsItem(
                icon = Icons.Filled.DarkMode,
                title = "Тема",
                subtitle = when (current.themeMode) {
                    ThemeMode.SYSTEM -> "Как в системе"
                    ThemeMode.LIGHT -> "Светлая"
                    ThemeMode.DARK -> "Тёмная"
                }
            )
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
                AppSegmented(
                    options = listOf("Светлая", "Тёмная", "Системная"),
                    selectedIndex = modes.indexOf(current.themeMode).coerceAtLeast(0),
                    onSelect = { index -> appViewModel.setTheme(modes[index]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = "Студибокс, версия ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        )
    }
}
