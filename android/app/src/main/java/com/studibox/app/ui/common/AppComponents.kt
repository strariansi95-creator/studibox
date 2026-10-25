package com.studibox.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.BadgeTextStyle
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme
import com.studibox.app.ui.theme.colorForKey

// ---------------------------------------------------------------- базовые блоки

/** Базовая карточка приложения: один радиус, мягкая граница, без тяжёлых теней. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.cardPadding),
    containerColor: Color = StudiboxTheme.colors.surface,
    borderColor: Color = StudiboxTheme.colors.outline,
    shape: androidx.compose.ui.graphics.Shape = AppShapes.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(),
        label = "cardScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content
    )
}

/** Заголовок раздела внутри экрана. */
@Composable
fun AppSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = StudiboxTheme.colors.textPrimary
        )
        if (trailing != null) trailing()
    }
}

/** Секция: заголовок + содержимое с едиными отступами. */
@Composable
fun AppSection(
    title: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) AppSectionTitle(text = title, trailing = trailing)
        content()
    }
}

/** Главная кнопка: фиолетово-синий градиент, лёгкое нажатие. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = StudiboxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(),
        label = "buttonScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AppShapes.button)
            .then(
                if (enabled) {
                    Modifier.background(colors.accentGradient)
                } else {
                    Modifier.background(colors.surfaceVariant)
                }
            )
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = if (enabled) colors.textOnAccent else colors.textTertiary
                )
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) colors.textOnAccent else colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) colors.textOnAccent else colors.textTertiary
            )
        }
    }
}

/** Второстепенная кнопка: та же геометрия, но контурная. */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier
            .clip(AppShapes.button)
            .background(colors.surfaceVariant)
            .border(BorderStroke(1.dp, colors.outlineStrong), AppShapes.button)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary
        )
    }
}

/** Небольшая метка: «СЕЙЧАС», код группы, тип занятия. */
@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = StudiboxTheme.colors.accentContainer,
    contentColor: Color = StudiboxTheme.colors.onAccentContainer,
    uppercase: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = if (uppercase) text.uppercase() else text,
            style = if (uppercase) BadgeTextStyle
            else MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Иконка-плашка со скруглением — используется в шапках и строках настроек. */
@Composable
fun AppIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = StudiboxTheme.colors.accent,
    containerColor: Color = StudiboxTheme.colors.accentContainer,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AppShapes.chip)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.5f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Круглый аватар с инициалами — группы, преподаватели. */
@Composable
fun AppInitialsAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    shape: androidx.compose.ui.graphics.Shape = AppShapes.chip
) {
    val accent = colorForKey(text)
    val initials = text.trim().take(2).uppercase()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
    }
}

/** Поле поиска в стиле приложения. */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = AppShapes.field,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.outline,
            cursorColor = colors.accent,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Переключатель из двух-трёх вариантов: «Неделя / День», «Светлая / Тёмная / Системная». */
@Composable
fun AppSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier
            .clip(AppShapes.chip)
            .background(colors.surfaceVariant)
            .border(BorderStroke(1.dp, colors.outline), AppShapes.chip)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) colors.accent else Color.Transparent,
                animationSpec = tween(180),
                label = "segmentBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) colors.textOnAccent else colors.textSecondary,
                animationSpec = tween(180),
                label = "segmentText"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.chip)
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

/** Строка «иконка — подпись — значение» для деталей занятия. */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconTile(icon = icon, size = 34.dp)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary
            )
        }
    }
}

// ---------------------------------------------------------------- настройки

/** Строка настроек: иконка, заголовок, подпись и произвольный элемент справа. */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconTile(icon = icon, size = 36.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Строка настроек с переключателем. */
@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val colors = StudiboxTheme.colors
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.textOnAccent,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.textTertiary,
                    uncheckedTrackColor = colors.surfaceVariant,
                    uncheckedBorderColor = colors.outlineStrong
                )
            )
        }
    )
}

/**
 * Выбор «за сколько минут приходит уведомление» — строка чипов 5/10/15/30.
 * Общий компонент для «Настроек» и экрана уведомлений (п. 3 задачи требует,
 * чтобы было видно, за сколько минут приходит уведомление, в обоих местах).
 */
@Composable
fun ReminderMinutesSelector(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Int> = listOf(5, 10, 15, 30)
) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { minutes ->
            val selected = selectedMinutes == minutes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.chip)
                    .background(if (selected) colors.accent else colors.surfaceVariant)
                    .clickable { onSelect(minutes) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$minutes мин",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.textOnAccent else colors.textSecondary
                )
            }
        }
    }
}

/** Разделитель внутри карточки настроек. */
@Composable
fun AppRowDivider(modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 62.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(StudiboxTheme.colors.outline)
    )
}

// ---------------------------------------------------------------- выбор группы

/** Карточка группы на экране выбора. */
@Composable
fun GroupCard(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = StudiboxTheme.colors
    val border by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.outline,
        animationSpec = tween(180),
        label = "groupBorder"
    )
    val background by animateColorAsState(
        targetValue = if (selected) colors.accentContainer else colors.surface,
        animationSpec = tween(180),
        label = "groupBg"
    )

    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = background,
        borderColor = border,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppInitialsAvatar(text = name)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) trailing()
        }
    }
}

// ---------------------------------------------------------------- состояния

/** Пустое состояние в стиле приложения. */
@Composable
fun AppStatePlaceholder(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accent: Color = StudiboxTheme.colors.accent
) {
    val colors = StudiboxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            AppButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- нижняя навигация

data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** Нижняя навигация: скруглённая панель, активный раздел — фиолетовый. */
@Composable
fun AppBottomBar(
    tabs: List<BottomTab>,
    isSelected: (BottomTab) -> Boolean,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.bottomBar)
                .background(colors.backgroundElevated)
                .border(BorderStroke(1.dp, colors.outline), AppShapes.bottomBar)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    selected = isSelected(tab),
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    val tint by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textTertiary,
        animationSpec = tween(180),
        label = "tabTint"
    )
    val background by animateColorAsState(
        targetValue = if (selected) colors.accentContainer else Color.Transparent,
        animationSpec = tween(180),
        label = "tabBg"
    )

    Column(
        modifier = modifier
            .clip(AppShapes.chip)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(AppShapes.pill)
                .background(background)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            modifier = Modifier
                .padding(top = 3.dp)
                .alpha(if (selected) 1f else 0.9f)
        )
    }
}

// ---------------------------------------------------------------- вспомогательное

/** Горизонтальный отступ экрана — чтобы не дублировать 20.dp по всем файлам. */
fun Modifier.screenPadding(): Modifier = this.padding(horizontal = Spacing.screenHorizontal)

@Composable
fun RowSpacer(width: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.width(width))
}

@Composable
fun ColumnSpacer(height: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.height(height))
}
