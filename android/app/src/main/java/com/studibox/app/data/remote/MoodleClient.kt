package com.studibox.app.data.remote

import android.util.Log
import com.studibox.app.BuildConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LmsSession(
    val token: String,
    val userId: Int,
    val fullName: String,
    val userName: String
)

data class LmsCourse(
    val id: Int,
    val name: String,
    val shortName: String
)

data class LmsAssignment(
    val id: Int,
    val courseId: Int,
    val courseName: String,
    val name: String,
    val intro: String,
    /** Срок сдачи, секунды Unix. */
    val dueDate: Long?,
    val isLab: Boolean,
    /** Страница задания на портале (без токена — откроется в браузере). */
    val url: String? = null
)

data class LmsFile(
    val name: String,
    /**
     * Для файлов — адрес с токеном: скачивается только внутри приложения
     * и наружу не передаётся. Для ссылок ([isLink]) — обычный внешний адрес.
     */
    val url: String,
    val mimeType: String,
    val isLink: Boolean = false
)

data class LmsModule(
    val id: Int,
    val name: String,
    /** resource, page, url, folder, book, assign, quiz … */
    val modName: String,
    /** Страница модуля на портале (для page/book/quiz — без токена). */
    val url: String?,
    val description: String,
    val files: List<LmsFile>
)

data class LmsSection(
    val id: Int,
    val name: String,
    val modules: List<LmsModule>
)

/** Токен истёк или отозван — нужно войти заново. */
class LmsAuthException(message: String) : IllegalStateException(message)

/**
 * Официальный Moodle REST СЭДО СВФУ (sdo.s-vfu.ru).
 * Пароль на сервер Studibox не уходит — только на портал университета.
 */
class MoodleClient(
    private val baseUrl: String = BuildConfig.LMS_BASE_URL,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val json = NetworkModule.json
    private val root = baseUrl.trimEnd('/')
    private val tag = "StudiboxLms"

    fun login(username: String, password: String): LmsSession {
        val token = requestToken(username.trim(), password)
        val info = rest(token, "core_webservice_get_site_info")
        val obj = info as? JsonObject ?: throw IllegalStateException("Не удалось прочитать профиль СДО")
        val userId = obj.int("userid") ?: throw IllegalStateException("В ответе СДО нет userid")
        val fullName = obj.str("fullname") ?: username
        val userName = obj.str("username") ?: username
        return LmsSession(token = token, userId = userId, fullName = fullName, userName = userName)
    }

    fun courses(token: String, userId: Int): List<LmsCourse> {
        val payload = rest(
            token,
            "core_enrol_get_users_courses",
            mapOf("userid" to userId.toString())
        )
        val array = payload as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.int("id") ?: return@mapNotNull null
            val name = obj.str("fullname") ?: obj.str("displayname") ?: return@mapNotNull null
            LmsCourse(
                id = id,
                name = name,
                shortName = obj.str("shortname").orEmpty()
            )
        }
    }

    fun assignments(token: String, courses: List<LmsCourse>): List<LmsAssignment> {
        if (courses.isEmpty()) return emptyList()
        val params = linkedMapOf<String, String>()
        courses.forEachIndexed { index, course ->
            params["courseids[$index]"] = course.id.toString()
        }
        val payload = rest(token, "mod_assign_get_assignments", params)
        val rootObj = payload as? JsonObject ?: return emptyList()
        val courseById = courses.associateBy { it.id }
        val result = ArrayList<LmsAssignment>()
        val courseArray = rootObj["courses"] as? JsonArray ?: return emptyList()
        for (courseEl in courseArray) {
            val courseObj = courseEl as? JsonObject ?: continue
            val courseId = courseObj.int("id") ?: continue
            val courseName = courseObj.str("fullname") ?: courseById[courseId]?.name.orEmpty()
            val assigns = courseObj["assignments"] as? JsonArray ?: continue
            for (assignEl in assigns) {
                val a = assignEl as? JsonObject ?: continue
                val name = a.str("name") ?: continue
                val due = a.long("duedate")?.takeIf { it > 0 }
                result += LmsAssignment(
                    id = a.int("id") ?: continue,
                    courseId = courseId,
                    courseName = courseName,
                    name = name,
                    intro = a.str("intro").orEmpty().stripHtml(),
                    dueDate = due,
                    isLab = name.contains("лаборатор", ignoreCase = true) ||
                        name.contains("лаб.", ignoreCase = true) ||
                        name.contains("л/р", ignoreCase = true),
                    url = a.int("cmid")?.let { "$root/mod/assign/view.php?id=$it" }
                )
            }
        }
        return result.sortedWith(
            compareBy<LmsAssignment> { !it.isLab }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenBy { it.name }
        )
    }

    /** true, если студент уже отправил работу по заданию (не черновик). */
    fun isSubmitted(token: String, assignId: Int): Boolean {
        val payload = rest(token, "mod_assign_get_submission_status", mapOf("assignid" to assignId.toString()))
        val status = ((payload as? JsonObject)
            ?.get("lastattempt") as? JsonObject)
            ?.let { (it["submission"] as? JsonObject) ?: (it["teamsubmission"] as? JsonObject) }
            ?.str("status")
        return status == "submitted"
    }

    /** Разделы курса с материалами (лекции, файлы, ссылки). Скрытые модули пропускаются. */
    fun courseContents(token: String, courseId: Int): List<LmsSection> {
        val payload = rest(token, "core_course_get_contents", mapOf("courseid" to courseId.toString()))
        val array = payload as? JsonArray ?: return emptyList()
        return array.mapNotNull { sectionEl ->
            val section = sectionEl as? JsonObject ?: return@mapNotNull null
            val modules = (section["modules"] as? JsonArray).orEmpty().mapNotNull { moduleEl ->
                val m = moduleEl as? JsonObject ?: return@mapNotNull null
                if (m.int("visible") == 0 || m.int("uservisible") == 0) return@mapNotNull null
                val modName = m.str("modname").orEmpty()
                // Метки (label) — это просто текст между материалами, не материал.
                if (modName == "label") return@mapNotNull null
                val files = (m["contents"] as? JsonArray).orEmpty().mapNotNull { contentEl ->
                    val c = contentEl as? JsonObject ?: return@mapNotNull null
                    val fileUrl = c.str("fileurl") ?: return@mapNotNull null
                    val isLink = c.str("type") == "url"
                    LmsFile(
                        name = c.str("filename") ?: m.str("name").orEmpty(),
                        url = if (isLink) fileUrl else withToken(fileUrl, token),
                        mimeType = c.str("mimetype").orEmpty(),
                        isLink = isLink
                    )
                }
                LmsModule(
                    id = m.int("id") ?: return@mapNotNull null,
                    name = m.str("name") ?: return@mapNotNull null,
                    modName = modName,
                    url = m.str("url"),
                    description = m.str("description").orEmpty().stripHtml(),
                    files = files
                )
            }
            LmsSection(
                id = section.int("id") ?: 0,
                name = section.str("name").orEmpty(),
                modules = modules
            )
        }.filter { it.modules.isNotEmpty() }
    }

    /**
     * Файлы Moodle отдаёт через pluginfile.php — без токена браузер попросит
     * войти ещё раз. webservice/pluginfile.php + token открывает файл сразу.
     */
    private fun withToken(fileUrl: String, token: String): String {
        if (!fileUrl.contains("pluginfile.php")) return fileUrl
        val base = if (fileUrl.contains("/webservice/pluginfile.php")) fileUrl
        else fileUrl.replace("/pluginfile.php", "/webservice/pluginfile.php")
        val separator = if (base.contains('?')) '&' else '?'
        return "$base${separator}token=$token"
    }

    private fun requestToken(username: String, password: String): String {
        val url = "$root/login/token.php"
        val body = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("service", "moodle_mobile_app")
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", "Studibox/1.0")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("СДО ответила HTTP ${response.code}")
            }
            val obj = json.parseToJsonElement(text) as? JsonObject
                ?: throw IllegalStateException("СДО вернула не JSON")
            val token = obj.str("token")
            if (!token.isNullOrBlank()) return token
            val message = obj.str("error") ?: obj.str("message") ?: "Не удалось войти в СДО"
            Log.w(tag, "token.php: $message")
            throw IllegalStateException(humanLoginError(message))
        }
    }

    private fun rest(
        token: String,
        function: String,
        extra: Map<String, String> = emptyMap()
    ): JsonElement {
        val builder = "$root/webservice/rest/server.php".toHttpUrl().newBuilder()
            .addQueryParameter("wstoken", token)
            .addQueryParameter("wsfunction", function)
            .addQueryParameter("moodlewsrestformat", "json")
        extra.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("User-Agent", "Studibox/1.0")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("СДО REST HTTP ${response.code}")
            }
            val element = json.parseToJsonElement(text)
            if (element is JsonObject && element.str("exception") != null) {
                val message = element.str("message") ?: "Ошибка СДО"
                val code = element.str("errorcode").orEmpty()
                if (code == "invalidtoken" || code == "accessexception") {
                    throw LmsAuthException("Сессия СДО истекла. Войдите снова")
                }
                throw IllegalStateException(message)
            }
            return element
        }
    }

    private fun humanLoginError(raw: String): String = when {
        raw.contains("invalidlogin", ignoreCase = true) ->
            "Неверный логин или пароль. Логин — от ЛКС, без @stud.s-vfu.ru"
        raw.contains("servicenotavailable", ignoreCase = true) ->
            "Мобильный вход в СДО сейчас недоступен. Попробуйте позже"
        else -> raw
    }
}

private fun String.stripHtml(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun JsonObject.str(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key] ?: continue
        val text = when (value) {
            is JsonNull -> null
            is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
            else -> null
        }
        if (text != null) return text
    }
    return null
}

private fun JsonObject.int(key: String): Int? {
    val value = this[key] ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull?.toIntOrNull() ?: value.contentOrNull?.toDoubleOrNull()?.toInt()
        else -> null
    }
}

private fun JsonObject.long(key: String): Long? {
    val value = this[key] ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull?.toLongOrNull() ?: value.contentOrNull?.toDoubleOrNull()?.toLong()
        else -> null
    }
}
