package com.studibox.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.studibox.app.AppGraph
import com.studibox.app.data.prefs.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Напоминания о сроках сдачи заданий СДО: за сутки до срока, а если до
 * срока уже меньше суток — за 3 часа. Уже отправленные работы пропускаются.
 * Только для студентов: преподаватель сам задания не сдаёт.
 */
object DeadlineNotificationScheduler {

    private const val DAY_MS = 24 * 3600 * 1000L
    private const val LAST_CALL_MS = 3 * 3600 * 1000L
    /** Коды будильников дедлайнов не пересекаются с кодами пар (< 1 000 000). */
    private const val CODE_OFFSET = 1_000_000
    private const val PREFS = "studibox_deadline_alarms"
    private const val KEY_IDS = "ids"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dueFormat = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))

    fun reschedule(context: Context) {
        scope.launch {
            mutex.withLock {
                try {
                    plan(context)
                } catch (e: Exception) {
                    Log.e(NotificationHelper.TAG, "Ошибка планирования дедлайнов", e)
                }
            }
        }
    }

    private suspend fun plan(context: Context) {
        AppGraph.ensure(context)
        val settings = AppGraph.settings.settings.first()
        val lms = AppGraph.lmsRepository
        val loggedIn = lms.session.first() != null

        if (!settings.notificationsEnabled || settings.userRole != UserRole.STUDENT || !loggedIn ||
            !NotificationHelper.hasPermission(context)
        ) {
            cancelAll(context)
            return
        }

        // Нет сети — оставляем уже поставленные будильники как есть.
        val deadlines = lms.upcomingDeadlines(days = 2).getOrElse {
            Log.w(NotificationHelper.TAG, "Дедлайны не обновлены: ${it.message}")
            return
        }
        cancelAll(context)

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        var planned = 0

        for (assignment in deadlines) {
            val dueMs = (assignment.dueDate ?: continue) * 1000
            val (triggerAt, label) = when {
                dueMs - DAY_MS > now -> dueMs - DAY_MS to "Остались сутки"
                dueMs - LAST_CALL_MS > now -> dueMs - LAST_CALL_MS to "Осталось 3 часа"
                else -> continue
            }
            if (lms.isSubmitted(assignment.id)) continue

            val code = CODE_OFFSET + abs("deadline|${assignment.id}".hashCode()) % CODE_OFFSET
            val due = Instant.ofEpochMilli(dueMs).atZone(ZoneId.systemDefault()).format(dueFormat)
            val intent = Intent(context, LessonReminderReceiver::class.java).apply {
                putExtra(LessonReminderReceiver.EXTRA_TITLE, "$label: ${assignment.name}")
                putExtra(LessonReminderReceiver.EXTRA_SUBJECT, assignment.name)
                putExtra(LessonReminderReceiver.EXTRA_DETAILS, "${assignment.courseName} · сдать до $due")
                putExtra(LessonReminderReceiver.EXTRA_ID, code)
                putExtra(LessonReminderReceiver.EXTRA_CHANNEL, NotificationHelper.CHANNEL_DEADLINES)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            runCatching {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pending)
                rememberId(context, code)
                planned++
            }.onFailure { Log.e(NotificationHelper.TAG, "Не удалось поставить будильник дедлайна", it) }
        }
        Log.i(NotificationHelper.TAG, "Запланировано напоминаний о дедлайнах: $planned")
    }

    private fun rememberId(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids += id.toString()
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
    }

    private fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.forEach { id ->
            val pending = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, LessonReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pending != null) {
                runCatching { alarmManager.cancel(pending) }
                pending.cancel()
            }
        }
        prefs.edit().remove(KEY_IDS).apply()
    }
}
