package com.studibox.app.data.repo

import android.util.Log
import com.studibox.app.data.prefs.LmsStore
import com.studibox.app.data.remote.LmsAssignment
import com.studibox.app.data.remote.LmsAuthException
import com.studibox.app.data.remote.LmsCourse
import com.studibox.app.data.remote.LmsSection
import com.studibox.app.data.remote.LmsSession
import com.studibox.app.data.remote.MoodleClient
import com.studibox.app.util.CourseMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Вход в СДО СВФУ и материалы курсов. Курсы и содержимое кэшируются в
 * памяти на время жизни процесса — в Room ничего не пишется, расписание
 * и заметки этот репозиторий не трогает.
 */
class LmsRepository(
    private val store: LmsStore,
    private val client: MoodleClient = MoodleClient()
) {
    private val tag = "StudiboxLms"
    private val mutex = Mutex()

    private var cachedCourses: Pair<Int, List<LmsCourse>>? = null
    private val cachedContents = HashMap<Int, List<LmsSection>>()
    private var cachedAssignments: Pair<Int, List<LmsAssignment>>? = null

    val session: Flow<LmsSession?> = store.session

    suspend fun login(username: String, password: String): Result<LmsSession> = withContext(Dispatchers.IO) {
        runCatching { client.login(username, password) }
            .onSuccess { session ->
                clearCache()
                store.save(session)
            }
            .recoverCatching { throw IllegalStateException(humanError(it)) }
    }

    suspend fun logout() {
        clearCache()
        store.clear()
    }

    suspend fun courses(force: Boolean = false): Result<List<LmsCourse>> = authorized { session ->
        mutex.withLock {
            val cached = cachedCourses
            if (!force && cached != null && cached.first == session.userId) return@withLock cached.second
            client.courses(session.token, session.userId)
                .sortedBy { it.name.lowercase() }
                .also { cachedCourses = session.userId to it }
        }
    }

    suspend fun contents(courseId: Int, force: Boolean = false): Result<List<LmsSection>> = authorized { session ->
        mutex.withLock {
            if (!force) cachedContents[courseId]?.let { return@withLock it }
            client.courseContents(session.token, courseId).also { cachedContents[courseId] = it }
        }
    }

    /** Все задания по курсам пользователя (со сроками и без). */
    suspend fun assignments(force: Boolean = false): Result<List<LmsAssignment>> {
        val courses = courses(force).getOrElse { return Result.failure(it) }
        return authorized { session ->
            mutex.withLock {
                val cached = cachedAssignments
                if (!force && cached != null && cached.first == session.userId) return@withLock cached.second
                client.assignments(session.token, courses)
                    .also { cachedAssignments = session.userId to it }
            }
        }
    }

    /**
     * Задания со сроком в ближайшие [days] дней (просроченные не попадают),
     * от ближайшего к дальнему.
     */
    suspend fun upcomingDeadlines(days: Long = 14, force: Boolean = false): Result<List<LmsAssignment>> =
        assignments(force).map { list ->
            val now = System.currentTimeMillis() / 1000
            val until = now + days * 24 * 3600
            list.filter { val due = it.dueDate; due != null && due in now..until }
                .sortedBy { it.dueDate }
        }

    /** Отправлена ли работа. Ошибка проверки = «не знаем», то есть не сдано. */
    suspend fun isSubmitted(assignId: Int): Boolean =
        authorized { session -> client.isSubmitted(session.token, assignId) }.getOrDefault(false)

    /** Курс для предмета из расписания: сначала ручной выбор пользователя, потом автоподбор. */
    suspend fun courseForSubject(subject: String): Result<LmsCourse?> =
        courses().map { list ->
            val overrideId = store.courseOverrides()[CourseMatcher.subjectKey(subject)]
            list.firstOrNull { it.id == overrideId }
                ?: CourseMatcher.bestMatch(subject, list) { it.name }
        }

    suspend fun rememberCourseForSubject(subject: String, courseId: Int) {
        store.setCourseOverride(CourseMatcher.subjectKey(subject), courseId)
    }

    private suspend fun <T> authorized(block: suspend (LmsSession) -> T): Result<T> = withContext(Dispatchers.IO) {
        val session = store.session.first()
            ?: return@withContext Result.failure(LmsAuthException("Войдите в СДО СВФУ"))
        try {
            Result.success(block(session))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: LmsAuthException) {
            Log.w(tag, "Токен СДО недействителен, выходим", e)
            logout()
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(tag, "Ошибка СДО", e)
            Result.failure(IllegalStateException(humanError(e)))
        }
    }

    private fun clearCache() {
        cachedCourses = null
        cachedContents.clear()
        cachedAssignments = null
    }

    private fun humanError(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "Нет связи с СДО СВФУ. Проверьте интернет"
        is IOException -> "Не удалось связаться с СДО СВФУ"
        else -> e.message ?: "Ошибка СДО СВФУ"
    }

    companion object {
        /** Признак лекционного материала — по названию модуля или раздела. */
        fun isLecture(sectionName: String, moduleName: String): Boolean {
            val text = "$sectionName $moduleName".lowercase()
            return text.contains("лекц") || text.contains("lecture")
        }
    }
}
