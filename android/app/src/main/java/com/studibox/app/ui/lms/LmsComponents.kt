package com.studibox.app.ui.lms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studibox.app.data.remote.LmsCourse
import com.studibox.app.data.remote.LmsFile
import com.studibox.app.data.remote.LmsModule
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppIconTile
import com.studibox.app.ui.common.AppRowDivider
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.StudiboxTheme

/** Открывает файл/страницу СДО во внешнем приложении (браузер, просмотрщик PDF). */
fun openLmsUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения, чтобы открыть этот материал", Toast.LENGTH_SHORT).show()
    }
}

private fun iconFor(mimeType: String, modName: String): ImageVector = when {
    mimeType.contains("pdf") -> Icons.Filled.PictureAsPdf
    mimeType.contains("presentation") || mimeType.contains("powerpoint") -> Icons.Filled.Slideshow
    mimeType.startsWith("video") -> Icons.Filled.PlayCircle
    mimeType.contains("word") || mimeType.startsWith("text") -> Icons.Filled.Description
    modName == "url" -> Icons.Filled.Link
    modName == "folder" -> Icons.Filled.Folder
    modName == "page" || modName == "book" || modName == "lesson" -> Icons.AutoMirrored.Filled.MenuBook
    else -> Icons.Filled.InsertDriveFile
}

private fun moduleKind(module: LmsModule): String = when (module.modName) {
    "resource" -> "Файл"
    "folder" -> "Папка · ${module.files.size} файл."
    "url" -> "Ссылка"
    "page" -> "Страница"
    "book" -> "Книга"
    "lesson" -> "Лекция"
    "assign" -> "Задание"
    "quiz" -> "Тест"
    "forum" -> "Форум"
    else -> module.modName.ifBlank { "Материал" }
}

/**
 * Материал курса. Один файл — открывается сразу; несколько (папка) —
 * карточка раскрывается списком файлов; без файлов — страница модуля на портале.
 */
@Composable
fun LmsModuleCard(module: LmsModule) {
    val colors = StudiboxTheme.colors
    val context = LocalContext.current
    var expanded by rememberSaveable(module.id) { mutableStateOf(false) }
    val single = module.files.singleOrNull()
    val multiple = module.files.size > 1

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        onClick = {
            when {
                multiple -> expanded = !expanded
                single != null -> openLmsUrl(context, single.url)
                module.url != null -> openLmsUrl(context, module.url)
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconTile(icon = iconFor(single?.mimeType.orEmpty(), module.modName), size = 38.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = moduleKind(module),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (module.description.isNotBlank()) {
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Icon(
                imageVector = when {
                    !multiple -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                    expanded -> Icons.Filled.ExpandLess
                    else -> Icons.Filled.ExpandMore
                },
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (multiple && expanded) {
            module.files.forEach { file ->
                AppRowDivider()
                LmsFileRow(file)
            }
        }
    }
}

@Composable
private fun LmsFileRow(file: LmsFile) {
    val colors = StudiboxTheme.colors
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openLmsUrl(context, file.url) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconFor(file.mimeType, ""),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier
                .padding(start = 50.dp)
                .size(18.dp)
        )
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
    }
}

/** Строка курса в списке «Мои курсы» и в выборе курса для предмета. */
@Composable
fun LmsCourseCard(course: LmsCourse, onClick: () -> Unit, selected: Boolean = false) {
    val colors = StudiboxTheme.colors
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = if (selected) colors.accentContainer else colors.surface,
        borderColor = if (selected) colors.accent else colors.outline,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        shape = AppShapes.card
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconTile(icon = Icons.AutoMirrored.Filled.MenuBook, size = 38.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (course.shortName.isNotBlank() && course.shortName != course.name) {
                    Text(
                        text = course.shortName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
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
