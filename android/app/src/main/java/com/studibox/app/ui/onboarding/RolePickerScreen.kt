package com.studibox.app.ui.onboarding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppIconTile
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme

@Composable
fun RolePickerScreen(onRoleChosen: (UserRole) -> Unit) {
    val colors = StudiboxTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .notebookPaper()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(AppShapes.cardLarge)
                .background(colors.accentGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                tint = colors.textOnAccent,
                modifier = Modifier.size(38.dp)
            )
        }

        Text(
            text = "Студибокс",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = "Твоё расписание всегда под рукой",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(36.dp))

        RoleCard(
            title = "Студент",
            subtitle = "Выбор группы и подгруппы",
            icon = Icons.Filled.Groups,
            onClick = { onRoleChosen(UserRole.STUDENT) }
        )
        Spacer(Modifier.height(12.dp))
        RoleCard(
            title = "Преподаватель",
            subtitle = "Расписание по вашему ФИО",
            icon = Icons.Filled.Person,
            onClick = { onRoleChosen(UserRole.TEACHER) }
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudiboxTheme.colors
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconTile(icon = icon, size = 44.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
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
