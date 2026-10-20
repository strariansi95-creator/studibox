package com.studibox.app.data.repo

import android.content.Context
import android.util.Log
import com.studibox.app.data.remote.LmsCourse
import com.studibox.app.data.remote.LmsFile
import com.studibox.app.data.remote.LmsModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Скачанный файл лекции — доступен без интернета. */
data class DownloadedLecture(
    val courseId: Int,
    val courseName: String,
    val name: String,
    val file: File
) {
    val sizeBytes: Long get() = file.length()
}

/**
 * Лекции из СДО в приватной памяти приложения (filesDir/lectures/<courseId>/).
 * Ссылка с токеном СДО используется только здесь, для скачивания, и никогда
 * не уходит в сторонние приложения — наружу (если это не PDF) передаётся
 * уже сам файл через FileProvider.
 */
class LectureStorage(context: Context) {

    private val tag = "StudiboxLectures"
    private val root = File(context.filesDir, "lectures")
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloaded = MutableStateFlow(scan())
    /** Все скачанные лекции; обновляется после скачивания и удаления. */
    val downloaded: StateFlow<List<DownloadedLecture>> = _downloaded.asStateFlow()

    fun fileFor(courseId: Int, module: LmsModule, file: LmsFile): File =
        File(courseDir(courseId), "${module.id}_${sanitize(file.name)}")

    fun isDownloaded(courseId: Int, module: LmsModule, file: LmsFile): Boolean =
        fileFor(courseId, module, file).let { it.exists() && it.length() > 0 }

    /** Скачивает файл (или возвращает уже скачанный). */
    suspend fun download(course: LmsCourse, module: LmsModule, file: LmsFile): Result<File> =
        withContext(Dispatchers.IO) {
            val target = fileFor(course.id, module, file)
            if (target.exists() && target.length() > 0) return@withContext Result.success(target)
            runCatching {
                target.parentFile?.mkdirs()
                File(courseDir(course.id), COURSE_NAME_FILE).writeText(course.name)
                val request = Request.Builder().url(file.url).header("User-Agent", "Studibox/1.0").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("СДО ответила HTTP ${response.code}")
                    val body = response.body ?: error("Пустой ответ СДО")
                    // При неверном токене Moodle отвечает JSON-ошибкой вместо файла.
                    val type = body.contentType()?.toString().orEmpty()
                    if (type.startsWith("application/json") && !file.mimeType.contains("json")) {
                        error("Сессия СДО истекла. Войдите снова")
                    }
                    val temp = File(target.parentFile, "${target.name}.part")
                    temp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                    if (!temp.renameTo(target)) error("Не удалось сохранить файл")
                }
                refresh()
                target
            }.onFailure { e ->
                Log.w(tag, "Не удалось скачать ${file.name}", e)
                File(target.parentFile, "${target.name}.part").delete()
            }.recoverCatching { e ->
                throw IllegalStateException(
                    when (e) {
                        is java.io.IOException -> "Нет связи с СДО. Проверьте интернет"
                        else -> e.message ?: "Не удалось скачать файл"
                    }
                )
            }
        }

    suspend fun delete(lecture: DownloadedLecture) = withContext(Dispatchers.IO) {
        lecture.file.delete()
        val dir = courseDir(lecture.courseId)
        if (dir.listFiles().orEmpty().none { it.name != COURSE_NAME_FILE }) dir.deleteRecursively()
        refresh()
    }

    private fun refresh() {
        _downloaded.value = scan()
    }

    private fun scan(): List<DownloadedLecture> =
        root.listFiles().orEmpty().filter { it.isDirectory }.flatMap { dir ->
            val courseId = dir.name.toIntOrNull() ?: return@flatMap emptyList()
            val courseName = File(dir, COURSE_NAME_FILE).takeIf { it.exists() }?.readText().orEmpty()
            dir.listFiles().orEmpty()
                .filter { it.isFile && it.name != COURSE_NAME_FILE && !it.name.endsWith(".part") }
                .map { file ->
                    DownloadedLecture(
                        courseId = courseId,
                        courseName = courseName,
                        name = file.name.substringAfter('_'),
                        file = file
                    )
                }
        }.sortedWith(compareBy({ it.courseName.lowercase() }, { it.name.lowercase() }))

    private fun courseDir(courseId: Int) = File(root, courseId.toString())

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").take(120).ifBlank { "file" }

    companion object {
        private const val COURSE_NAME_FILE = ".course"

        fun isPdf(file: File, mimeType: String = ""): Boolean =
            mimeType.contains("pdf") || file.name.endsWith(".pdf", ignoreCase = true)
    }
}
