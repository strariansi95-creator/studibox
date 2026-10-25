package com.studibox.app.ui.notifications

import com.studibox.app.ui.theme.notebookPaper
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.ui.AppViewModel
import com.studibox.app.ui.common.AppBadge
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppIconTile
import com.studibox.app.ui.common.AppStatePlaceholder
import com.studibox.app.ui.common.BreakInfo
import com.studibox.app.ui.common.ReminderMinutesSelector
import com.studibox.app.ui.common.SettingsSwitchItem
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme

/**
 * Открывается по колокольчику на экране «Сегодня» (п. 3 задачи). Показывает
 * РЕАЛЬНОЕ состояние напоминаний (их ставит LessonNotificationScheduler
 * через AlarmManager — см. этот класс) и перемены сегодняшнего дня: когда
 * заканчивается текущая пара, когда начинается следующая и сколько длится
 * перерыв между ними.
 */
@Composable
fun NotificationsScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = viewModel()
) {
    val colors = StudiboxTheme.colors
    val settings by appViewModel.settings.collectAsState()
    val breaks by viewModel.todayBreaks.collectAsState()
    val current = settings ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .notebookPaper()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .clickable(onClick = onBack)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Уведомления",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = 16.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
        ) {
            item(key = "reminders-card") {
                AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    SettingsSwitchItem(
                        icon = Icons.Filled.NotificationsActive,
                        title = "Напоминания о занятиях",
                        subtitle = if (current.notificationsEnabled) {
                            "Придёт за ${current.reminderMinutes} минут до начала пары"
                        } else {
                            "Сейчас выключены"
                        },
                        checked = current.notificationsEnabled,
                        onCheckedChange = appViewModel::setNotifications
                    )
                    if (current.notificationsEnabled) {
                        ReminderMinutesSelector(
                            selectedMinutes = current.reminderMinutes,
                            onSelect = appViewModel::setReminderMinutes,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            item(key = "breaks-title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconTile(icon = Icons.Filled.FreeBreakfast, size = 34.dp)
                    Text(
                        text = "Перемены сегодня",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            if (breaks.isEmpty()) {
                item(key = "breaks-empty") {
                    AppStatePlaceholder(
                        icon = Icons.Filled.FreeBreakfast,
                        title = "Сегодня перемен нет",
                        description = "Появятся, когда в расписании будет больше одной пары подряд."
                    )
                }
            } else {
                items(breaks, key = { "${it.breakStart}-${it.breakEnd}" }) { info ->
                    BreakCard(info)
                }
            }
        }
    }
}

@Composable
private fun BreakCard(info: BreakInfo) {
    val colors = StudiboxTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${info.breakStart} — ${info.breakEnd}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "После «${info.afterSubject}», перед «${info.beforeSubject}»",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            AppBadge(
                text = "${info.minutes} мин",
                containerColor = colors.accentContainer,
                contentColor = colors.onAccentContainer
            )
        }
    }
}
