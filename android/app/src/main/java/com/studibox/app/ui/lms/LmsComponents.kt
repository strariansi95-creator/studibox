package com.studibox.app.ui.lms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.content.FileProvider
import com.studibox.app.data.remote.LmsCourse
import com.studibox.app.data.remote.LmsFile
import com.studibox.app.data.remote.LmsModule
import com.studibox.app.ui.common.AppBadge
import com.studibox.app.ui.common.AppCard
import com.studibox.app.ui.common.AppIconTile
import com.studibox.app.ui.common.AppRowDivider
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.StudiboxTheme
import java.io.File

/**
 * Открывает страницу СДО или внешнюю ссылку в браузере. Сюда никогда не
 * передаются адреса файлов с токеном — файлы скачиваются через LectureStorage.
 */
fun openLmsUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения, чтобы открыть эту ссылку", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Скачанный файл (не PDF — PDF открывается встроенным просмотрщиком) отдаётся
 * другому приложению через FileProvider: передаётся сам файл, а не ссылка с токеном.
 */
fun openDownloadedFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val type = mimeType.ifBlank {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения, чтобы открыть «${file.name}»", Toast.LENGTH_SHORT).show()
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

/** Ключ файла для отметок «скачано» / «качается». */
fun lmsFileKey(module: LmsModule, file: LmsFile) = "${module.id}|${file.name}"

/** Справа на карточке: прогресс скачивания, отметка «скачано» или стрелка. */
@Composable
private fun FileStatusIcon(downloading: Boolean, downloaded: Boolean, fallback: ImageVector) {
    val colors = StudiboxTheme.colors
    when {
        downloading -> CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colors.accent
        )
        downloaded -> Icon(
            imageVector = Icons.Filled.DownloadDone,
            contentDescription = "Скачано",
            tint = colors.success,
            modifier = Modifier.size(20.dp)
        )
        else -> Icon(
            imageVector = fallback,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Материал курса. Один файл — скачивается и открывается по нажатию;
 * несколько (папка) — карточка раскрывается списком файлов; без файлов —
 * страница модуля на портале в браузере.
 */
@Composable
fun LmsModuleCard(
    module: LmsModule,
    downloadedKeys: Set<String>,
    downloadingKeys: Set<String>,
    onOpenFile: (LmsFile) -> Unit
) {
    val colors = StudiboxTheme.colors
    val context = LocalContext.current
    var expanded by rememberSaveable(module.id) { mutableStateOf(false) }
    val single = module.files.singleOrNull()
    val multiple = module.files.size > 1
    val singleKey = single?.let { lmsFileKey(module, it) }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        onClick = {
            when {
                multiple -> expanded = !expanded
                single != null -> onOpenFile(single)
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
            if (multiple) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                FileStatusIcon(
                    downloading = singleKey in downloadingKeys,
                    downloaded = singleKey in downloadedKeys,
                    fallback = Icons.AutoMirrored.Filled.KeyboardArrowRight
                )
            }
        }
        if (multiple && expanded) {
            module.files.forEach { file ->
                val key = lmsFileKey(module, file)
                AppRowDivider()
                LmsFileRow(
                    file = file,
                    downloading = key in downloadingKeys,
                    downloaded = key in downloadedKeys,
                    onClick = { onOpenFile(file) }
                )
            }
        }
    }
}

@Composable
private fun LmsFileRow(file: LmsFile, downloading: Boolean, downloaded: Boolean, onClick: () -> Unit) {
    val colors = StudiboxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconFor(file.mimeType, if (file.isLink) "url" else ""),
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
                .padding(horizontal = 10.dp)
        )
        if (!file.isLink) FileStatusIcon(downloading, downloaded, Icons.Filled.Download)
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

private val deadlineFormat = java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", java.util.Locale("ru"))

/** «сегодня», «завтра», «через 3 дн.» — сколько осталось до срока. */
private fun timeLeft(dueSeconds: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val due = java.time.Instant.ofEpochSecond(dueSeconds).atZone(zone)
    val now = java.time.ZonedDateTime.now(zone)
    val hours = java.time.Duration.between(now, due).toHours()
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
    return when {
        hours < 0 -> "срок прошёл"
        hours < 1 -> "меньше часа"
        days == 0L -> "сегодня, осталось $hours ч"
        days == 1L -> "завтра"
        else -> "через $days дн."
    }
}

/** Задание СДО со сроком сдачи. Цвет полоски — насколько срок близко. */
@Composable
fun DeadlineCard(assignment: com.studibox.app.data.remote.LmsAssignment, showCourse: Boolean = true) {
    val colors = StudiboxTheme.colors
    val context = LocalContext.current
    val due = assignment.dueDate
    val hoursLeft = due?.let { (it - System.currentTimeMillis() / 1000) / 3600 }
    val accent = when {
        hoursLeft == null -> colors.textTertiary
        hoursLeft < 24 -> colors.danger
        hoursLeft < 72 -> colors.warning
        else -> colors.success
    }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = assignment.url?.let { url -> { openLmsUrl(context, url) } },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconTile(
                icon = Icons.Filled.Schedule,
                size = 38.dp,
                tint = accent,
                containerColor = accent.copy(alpha = 0.14f)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = assignment.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (showCourse) {
                    Text(
                        text = assignment.courseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (due != null) {
                    val dueText = java.time.Instant.ofEpochSecond(due)
                        .atZone(java.time.ZoneId.systemDefault()).format(deadlineFormat)
                    Text(
                        text = "До $dueText · ${timeLeft(due)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "Без срока",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (assignment.isLab) AppBadge(text = "лаб.")
        }
    }
}
