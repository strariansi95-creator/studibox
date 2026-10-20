package com.studibox.app.ui.teacher

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppInitialsAvatar
import com.studibox.app.ui.common.AppSearchField
import com.studibox.app.ui.common.EmptyState
import com.studibox.app.ui.common.ErrorBanner
import com.studibox.app.ui.common.LoadingState
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme

@Composable
fun TeacherPickerScreen(
    onTeacherSelected: () -> Unit,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    viewModel: TeacherPickerViewModel = viewModel()
) {
    val colors = StudiboxTheme.colors

    val query by viewModel.query.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val all by viewModel.teachers.collectAsState()
    val filtered = remember(all, query) { viewModel.filtered() }

    // Группировка по первой букве ФИО — как в контактах телефона.
    val grouped = remember(filtered) {
        filtered.groupBy { it.firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap()
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBack) {
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
                    }
                    Text(
                        text = "Преподаватели",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(AppShapes.pill)
                        .clickable { viewModel.load() }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Обновить список",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AppSearchField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = "Поиск преподавателя…",
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        if (loading && all.isEmpty()) {
            LoadingState(text = "Загружаем расписание всех групп…")
        }

        error?.let { message ->
            ErrorBanner(message = message, onRetry = { viewModel.load() })
        }

        if (!loading && filtered.isEmpty() && error == null) {
            EmptyState(
                text = if (all.isEmpty()) {
                    "Список преподавателей пока пуст. Обновите расписание."
                } else {
                    "Ничего не найдено"
                },
                actionLabel = if (all.isEmpty()) "Обновить" else null,
                onAction = if (all.isEmpty()) ({ viewModel.load() }) else null
            )
        }

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grouped.forEach { (letter, names) ->
                item(key = "header-$letter") {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
                items(names, key = { it }) { teacher ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.selectTeacher(teacher, onTeacherSelected) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppInitialsAvatar(
                                text = teacher,
                                size = 40.dp,
                                shape = CircleShape
                            )
                            Text(
                                text = teacher,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
