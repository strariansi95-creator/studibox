package com.studibox.app.ui.groups

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.R
import com.studibox.app.ui.common.AppButton
import com.studibox.app.ui.common.AppSearchField
import com.studibox.app.ui.common.EmptyState
import com.studibox.app.ui.common.ErrorBanner
import com.studibox.app.ui.common.GroupCard
import com.studibox.app.ui.common.LoadingState
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme

@Composable
fun GroupPickerScreen(
    onGroupSelected: () -> Unit,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    viewModel: GroupsViewModel = viewModel()
) {
    val colors = StudiboxTheme.colors

    val query by viewModel.query.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val all by viewModel.groups.collectAsState()
    val filtered = remember(all, query) { viewModel.filtered() }

    var selectedName by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .notebookPaper()
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
                        text = stringResource(R.string.choose_group),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(AppShapes.pill)
                        .clickable { viewModel.loadGroups() }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Обновить список групп",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = "Чтобы увидеть ваше расписание",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            AppSearchField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.search_group),
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        error?.let { message ->
            ErrorBanner(message = message, onRetry = { viewModel.loadGroups() })
        }

        if (loading && all.isEmpty()) {
            LoadingState(text = "Загружаем список групп…")
        }

        if (!loading && filtered.isEmpty()) {
            EmptyState(
                text = if (all.isEmpty()) {
                    "Список групп пуст. Проверьте подключение и обновите список."
                } else {
                    "Ничего не найдено"
                },
                actionLabel = if (all.isEmpty()) "Обновить" else null,
                onAction = if (all.isEmpty()) ({ viewModel.loadGroups() }) else null
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
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.name }) { group ->
                val selected = group.name == selectedName
                GroupCard(
                    name = group.name,
                    selected = selected,
                    onClick = { selectedName = group.name },
                    trailing = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = 8.dp, bottom = 20.dp)
        ) {
            val chosen = selectedName
            AppButton(
                text = "Продолжить",
                onClick = { if (chosen != null) viewModel.selectGroup(chosen, onGroupSelected) },
                enabled = chosen != null && !loading,
                loading = loading && chosen != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
