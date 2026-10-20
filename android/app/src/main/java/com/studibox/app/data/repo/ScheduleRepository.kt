package com.studibox.app.data.repo

import android.util.Log
import com.studibox.app.data.local.GroupEntity
import com.studibox.app.data.local.LessonEntity
import com.studibox.app.data.local.MetaKeys
import com.studibox.app.data.local.StudiboxDatabase
import com.studibox.app.data.prefs.SettingsStore
import com.studibox.app.data.remote.ApiMapper
import com.studibox.app.data.remote.NetworkModule
import com.studibox.app.data.local.TeacherSubgroupRow
import com.studibox.app.data.remote.ScheduleApi
import com.studibox.app.util.DateUtils
import com.studibox.app.util.SubgroupFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

/**
 * Группа сравнивается не побайтово, а по нормализованной форме: источник —
 * XLSX, где от импорта к импорту у одного и того же названия группы может
 * чуть плавать пробел/регистр/вид тире. Без этого безобидная мелочь в
 * исходнике выглядела бы как «группа пропала» и сбрасывала выбор пользователя.
 */
private fun normalizeGroupKey(name: String): String =
    name.trim()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[\u2010-\u2015]"), "-") // разные виды тире/дефиса -> '-'
        .uppercase()
        // Хвост "- 30 СТУДЕНТОВ" из заголовка Excel: раньше он попадал в имя
        // группы, теперь backend хранит чистый код ("КИТ-ВР-26-1"). Чтобы
        // сохранённый на телефоне выбор "КИТ-ВР-26-1 - 30 СТУДЕНТОВ" сам
        // сопоставился с "КИТ-ВР-26-1" (а не сбросился, из-за чего новые дни,
        // лежащие под чистым именем, не доходили до приложения), хвост
        // отрезается и при сравнении. uppercase() выше — чтобы не зависеть
        // от флагов регистра для кириллицы.
        .replace(Regex("""[\s,;(\-]+\d+\s*(?:СТУДЕНТ|ЧЕЛ).*$"""), "")
        .trim()

class ScheduleRepository(
    private val db: StudiboxDatabase,
    private val api: ScheduleApi,
    private val settings: SettingsStore
) {

    private val tag = NetworkModule.TAG

    /** Сколько дней хранить прошлое расписание локально (п. 4 задачи). */
    private val historyRetentionDays = 7

    private fun historyCutoffIso(): String =
        DateUtils.iso(LocalDate.now().minusDays(historyRetentionDays.toLong()))

    // ------------------------------------------------------------------ reads

    fun observeGroups(): Flow<List<GroupEntity>> = db.groupDao().observeAll()

    fun observeLessonsForDate(group: String, date: LocalDate): Flow<List<LessonEntity>> =
        db.lessonDao().observeByDate(group, DateUtils.iso(date))

    fun observeSubgroups(group: String): Flow<List<String>> = db.lessonDao().observeSubgroups(group)

    /** Только фильтр отображения: не трогает ни Room, ни сервер. */
    fun applySubgroupFilter(lessons: List<LessonEntity>, subgroup: String?): List<LessonEntity> =
        if (subgroup.isNullOrBlank()) lessons
        else lessons.filter { SubgroupFormat.matches(it.subgroup, subgroup) }

    fun observeAvailableDates(group: String): Flow<List<LocalDate>> =
        db.lessonDao().observeDates(group).map { list ->
            list.mapNotNull { DateUtils.parseDate(it) }.sorted()
        }

    fun observeVersion(): Flow<String?> = db.metaDao().observe(MetaKeys.SCHEDULE_VERSION)

    fun observeLastSync(): Flow<String?> = db.metaDao().observe(MetaKeys.LAST_SYNC_AT)

    suspend fun lessonsForDate(group: String, date: LocalDate): List<LessonEntity> =
        runCatching { db.lessonDao().getByDate(group, DateUtils.iso(date)) }
            .onFailure { Log.e(tag, "Ошибка Room при чтении занятий", it) }
            .getOrDefault(emptyList())

    suspend fun hasCachedSchedule(group: String): Boolean =
        runCatching { db.lessonDao().countForGroup(group) > 0 }.getOrDefault(false)

    // --------------------------------------------------------- режим преподавателя

    /**
     * Пусто, пока ни разу не выполнялся refreshAllGroupsForTeacherMode().
     * Отдаёт сырые пары (преподаватель, подгруппа) — ФИО уже нормализовано
     * и разведено на отдельных людей при сохранении (см. ApiMapper/
     * TeacherNameFormat.splitRawField), группировка в список ФИО для экрана
     * выбора делается во ViewModel (см. ui.common.aggregateTeacherNames).
     */
    fun observeTeacherSubgroupRows(): Flow<List<TeacherSubgroupRow>> = db.lessonDao().observeTeacherSubgroupRows()

    fun observeLessonsForTeacherAndDate(teacher: String, date: LocalDate): Flow<List<LessonEntity>> =
        db.lessonDao().observeByTeacherAndDate(teacher, DateUtils.iso(date))

    fun observeLessonsForTeacherInRange(teacher: String, from: LocalDate, to: LocalDate): Flow<List<LessonEntity>> =
        db.lessonDao().observeByTeacherInRange(teacher, DateUtils.iso(from), DateUtils.iso(to))

    fun observeAvailableDatesForTeacher(teacher: String): Flow<List<LocalDate>> =
        db.lessonDao().observeDatesForTeacher(teacher).map { list ->
            list.mapNotNull { DateUtils.parseDate(it) }.sorted()
        }

    suspend fun hasCachedTeacherData(): Boolean =
        runCatching { db.lessonDao().observeTeacherSubgroupRows() }.isSuccess &&
            runCatching { db.groupDao().count() }.getOrDefault(0) > 0

    /**
     * Собирает расписание СРАЗУ ВСЕХ групп в ту же таблицу lessons — API
     * отдаёт расписание только по одной группе за раз (`/api/schedule/{group}`),
     * поэтому список преподавателей и их занятий строится на стороне приложения,
     * как и было указано в задаче. `ApiMapper` при этом не меняется вообще.
     *
     * Каждая группа заменяется независимо (delete только своих строк), поэтому
     * уже загруженные группы не портятся, если для какой-то из них запрос упал —
     * тот факт просто попадает в лог, а импорт продолжается дальше.
     */
    suspend fun refreshAllGroupsForTeacherMode(force: Boolean = false): SyncResult {
        var groups = runCatching { db.groupDao().getAll() }.getOrDefault(emptyList())

        if (groups.isEmpty()) {
            when (val result = refreshGroups()) {
                is SyncResult.Failed -> return result
                else -> groups = runCatching { db.groupDao().getAll() }.getOrDefault(emptyList())
            }
        }
        if (groups.isEmpty()) {
            return SyncResult.Failed("Список групп пуст", offline = false)
        }

        val okGroups = java.util.concurrent.atomic.AtomicInteger(0)
        val failedGroups = java.util.concurrent.atomic.AtomicInteger(0)
        val totalLessons = java.util.concurrent.atomic.AtomicInteger(0)
        val sawNetworkFailure = java.util.concurrent.atomic.AtomicBoolean(false)

        // Ограничиваем параллелизм, чтобы не заваливать API десятками
        // одновременных запросов сразу для всех групп.
        val semaphore = Semaphore(5)
        coroutineScope {
            groups.map { group ->
                async {
                    semaphore.withPermit {
                        try {
                            val lessons = ApiMapper.lessons(api.schedule(group.name), group.name)
                            db.lessonDao().replaceForGroup(group.name, lessons, historyCutoffIso())
                            okGroups.incrementAndGet()
                            totalLessons.addAndGet(lessons.size)
                        } catch (e: Exception) {
                            failedGroups.incrementAndGet()
                            if (e is IOException) sawNetworkFailure.set(true)
                            Log.w(tag, "Преподаватель-режим: не удалось загрузить ${group.name}: ${e.message}")
                        }
                    }
                }
            }.forEach { it.await() }
        }

        Log.i(
            tag,
            "Преподаватель-режим: собрано групп=${okGroups.get()}, ошибок=${failedGroups.get()}, занятий=${totalLessons.get()}"
        )

        if (okGroups.get() == 0) {
            return if (sawNetworkFailure.get()) {
                SyncResult.Failed("Нет подключения к серверу", offline = true)
            } else {
                SyncResult.Failed("Не удалось загрузить расписание ни одной группы", offline = false)
            }
        }

        markSynced("teacher-mode")
        return SyncResult.Success(okGroups.get(), totalLessons.get(), "")
    }

    // ------------------------------------------------------------------- sync

    /**
     * Полная синхронизация.
     *
     * Порядок гарантирует сохранность данных при сбоях:
     *  1. /api/version — если версия не менялась и кэш есть, выходим;
     *  2. /api/groups — локальный список заменяется ТОЛЬКО после успешного ответа;
     *  3. /api/schedule/{group} — расписание группы заменяется атомарно.
     *
     * Любая ошибка оставляет предыдущие рабочие данные нетронутыми.
     * Таблица заметок не участвует в синхронизации вообще.
     */
    suspend fun sync(force: Boolean = false): SyncResult {
        val metaDao = db.metaDao()
        val storedVersion = runCatching { metaDao.get(MetaKeys.SCHEDULE_VERSION) }.getOrNull()

        val remoteVersion = try {
            ApiMapper.version(api.version())
        } catch (e: Exception) {
            return failure(e, "/api/version")
        }

        Log.i(tag, "Версия: локальная=$storedVersion, серверная=${remoteVersion.version}")

        val selectedGroup = settings.settings.first().selectedGroup
        val hasCache = selectedGroup != null && hasCachedSchedule(selectedGroup)
        val groupsCount = runCatching { db.groupDao().count() }.getOrDefault(0)

        if (!force &&
            storedVersion != null &&
            storedVersion == remoteVersion.version &&
            groupsCount > 0 &&
            (selectedGroup == null || hasCache)
        ) {
            markSynced("up-to-date")
            Log.i(tag, "Расписание актуально, загрузка не требуется")
            return SyncResult.UpToDate
        }

        // --- группы ---------------------------------------------------------
        val groups = try {
            ApiMapper.groups(api.groups())
        } catch (e: Exception) {
            return failure(e, "/api/groups")
        }

        if (groups.isEmpty()) {
            Log.w(tag, "Сервер вернул пустой список групп — старый список сохранён")
            return SyncResult.Failed("Сервер вернул пустой список групп", offline = false)
        }

        try {
            db.groupDao().replaceAll(groups)
        } catch (e: Exception) {
            Log.e(tag, "Ошибка Room при обновлении групп", e)
            return SyncResult.Failed("Ошибка локальной базы данных", offline = false)
        }

        // Выбранная группа отсутствует В ТОЧНОСТИ в новом списке — прежде чем
        // считать её пропавшей и сбрасывать выбор, ищем нормализованное
        // совпадение (мелкая разница в пробелах/регистре/тире между
        // импортами из XLSX не должна выглядеть как «группа удалена»).
        var effectiveGroup = selectedGroup
        if (selectedGroup != null && groups.none { it.name == selectedGroup }) {
            val match = groups.firstOrNull { normalizeGroupKey(it.name) == normalizeGroupKey(selectedGroup) }
            if (match != null) {
                Log.i(
                    tag,
                    "Название группы изменилось незначительно: \"$selectedGroup\" -> \"${match.name}\" — обновляем выбор"
                )
                settings.setSelectedGroup(match.name)
                effectiveGroup = match.name
            } else {
                Log.w(tag, "Группа $selectedGroup отсутствует в новом списке — сброс выбора")
                settings.setSelectedGroup(null)
                markSynced("groups-only")
                return SyncResult.Success(groups.size, 0, remoteVersion.version)
            }
        }

        // --- расписание -----------------------------------------------------
        var lessonsCount = 0
        if (effectiveGroup != null) {
            val groupForRequest = effectiveGroup
            Log.i(tag, "SCHEDULE_REQUEST_GROUP = $groupForRequest")
            val lessons = try {
                ApiMapper.lessons(api.schedule(groupForRequest), groupForRequest)
            } catch (e: Exception) {
                return failure(e, "/schedule/$groupForRequest")
            }
            Log.i(tag, "SCHEDULE_RESPONSE_GROUP = $groupForRequest")
            Log.i(tag, "SCHEDULE_RESPONSE_DAYS = ${lessons.map { it.date }.distinct().size}")
            Log.i(tag, "SCHEDULE_RESPONSE_LESSONS = ${lessons.size}")

            if (lessons.isEmpty() && hasCache) {
                Log.w(tag, "Пустое расписание от API — сохранён предыдущий кэш")
            } else {
                try {
                    db.lessonDao().replaceForGroup(groupForRequest, lessons, historyCutoffIso())
                    lessonsCount = lessons.size
                } catch (e: Exception) {
                    Log.e(tag, "Ошибка Room при обновлении расписания", e)
                    return SyncResult.Failed("Ошибка локальной базы данных", offline = false)
                }
            }
        }

        runCatching {
            metaDao.put(MetaKeys.SCHEDULE_VERSION, remoteVersion.version)
            remoteVersion.updatedAt?.let { metaDao.put(MetaKeys.SCHEDULE_UPDATED_AT, it) }
        }
        markSynced("ok")

        Log.i(tag, "Sync успешен: групп=${groups.size}, занятий=$lessonsCount")
        return SyncResult.Success(groups.size, lessonsCount, remoteVersion.version)
    }

    /** Загрузка только списка групп — нужна на экране первого запуска. */
    suspend fun refreshGroups(): SyncResult {
        return try {
            val groups = ApiMapper.groups(api.groups())
            if (groups.isEmpty()) {
                SyncResult.Failed("Сервер вернул пустой список групп", offline = false)
            } else {
                db.groupDao().replaceAll(groups)
                SyncResult.Success(groups.size, 0, "")
            }
        } catch (e: Exception) {
            failure(e, "/api/groups")
        }
    }

    /** Загрузка расписания конкретной группы (после выбора группы пользователем). */
    suspend fun refreshSchedule(group: String): SyncResult {
        return try {
            Log.i(tag, "SCHEDULE_REQUEST_GROUP = $group")
            val lessons = ApiMapper.lessons(api.schedule(group), group)
            Log.i(tag, "SCHEDULE_RESPONSE_GROUP = $group")
            Log.i(tag, "SCHEDULE_RESPONSE_DAYS = ${lessons.map { it.date }.distinct().size}")
            Log.i(tag, "SCHEDULE_RESPONSE_LESSONS = ${lessons.size}")
            if (lessons.isEmpty() && hasCachedSchedule(group)) {
                SyncResult.Failed("Сервер вернул пустое расписание", offline = false)
            } else {
                db.lessonDao().replaceForGroup(group, lessons, historyCutoffIso())
                markSynced("ok")
                SyncResult.Success(0, lessons.size, "")
            }
        } catch (e: Exception) {
            failure(e, "/schedule/$group")
        }
    }

    private suspend fun markSynced(result: String) {
        runCatching {
            db.metaDao().put(MetaKeys.LAST_SYNC_AT, System.currentTimeMillis().toString())
            db.metaDao().put(MetaKeys.LAST_SYNC_RESULT, result)
        }
    }

    private fun failure(e: Exception, endpoint: String): SyncResult.Failed = when (e) {
        is IOException -> {
            Log.w(tag, "Сеть недоступна ($endpoint): ${e.message}")
            SyncResult.Failed("Нет подключения к серверу", offline = true)
        }
        is HttpException -> {
            val text = when (e.code()) {
                400 -> "Некорректный запрос (400)"
                401, 403 -> "Нет доступа к API (${e.code()})"
                404 -> "Данные не найдены (404)"
                in 500..599 -> "Сервер временно недоступен (${e.code()})"
                else -> "Ошибка HTTP ${e.code()}"
            }
            Log.e(tag, "HTTP ошибка $endpoint: ${e.code()}")
            SyncResult.Failed(text, offline = false)
        }
        is SerializationException -> {
            Log.e(tag, "Ошибка разбора ответа $endpoint", e)
            SyncResult.Failed("Некорректный ответ сервера", offline = false)
        }
        else -> {
            Log.e(tag, "Непредвиденная ошибка $endpoint", e)
            SyncResult.Failed(e.message ?: "Неизвестная ошибка", offline = false)
        }
    }
}
