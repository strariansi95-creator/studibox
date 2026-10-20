package com.studibox.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.studibox.app.AppGraph
import com.studibox.app.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

object LessonNotificationScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Планирует напоминания на все оставшиеся сегодня занятия выбранной группы.
     * Используется неточный будильник (setWindow), чтобы не требовать
     * разрешение SCHEDULE_EXACT_ALARM.
     */
    fun rescheduleToday(context: Context) {
        scope.launch {
            try {
                AppGraph.ensure(context)
                val settings = AppGraph.settings.settings.first()
                cancelAll(context)

                if (!settings.notificationsEnabled) {
                    Log.i(NotificationHelper.TAG, "Уведомления выключены пользователем")
                    return@launch
                }
                if (!NotificationHelper.hasPermission(context)) {
                    Log.w(NotificationHelper.TAG, "Нет разрешения POST_NOTIFICATIONS")
                    return@launch
                }
                val group = settings.selectedGroup ?: return@launch

                val today = LocalDate.now()
                val lessons = AppGraph.scheduleRepository.lessonsForDate(group, today)
                val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return@launch
                val now = LocalDateTime.now()
                var planned = 0

                lessons.forEach { lesson ->
                    val start = DateUtils.parseTime(lesson.startTime) ?: return@forEach
                    val triggerAt = LocalDateTime.of(today, start)
                        .minusMinutes(settings.reminderMinutes.toLong())
                    if (triggerAt.isBefore(now)) return@forEach

                    val details = buildString {
                        if (lesson.startTime.isNotBlank()) append("${lesson.startTime} ")
                        if (lesson.classroom.isNotBlank()) append("· ауд. ${lesson.classroom} ")
                        if (lesson.teacher.isNotBlank()) append("· ${lesson.teacher}")
                    }.trim()

                    val id = requestCode(lesson.id)
                    val intent = Intent(context, LessonReminderReceiver::class.java).apply {
                        putExtra(LessonReminderReceiver.EXTRA_SUBJECT, lesson.subject)
                        putExtra(LessonReminderReceiver.EXTRA_DETAILS, details)
                        putExtra(LessonReminderReceiver.EXTRA_MINUTES, settings.reminderMinutes)
                        putExtra(LessonReminderReceiver.EXTRA_ID, id)
                    }
                    val pending = PendingIntent.getBroadcast(
                        context,
                        id,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val triggerMillis = triggerAt.atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()

                    runCatching {
                        alarmManager.setWindow(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            60_000L,
                            pending
                        )
                        planned++
                        rememberId(context, id)
                    }.onFailure {
                        Log.e(NotificationHelper.TAG, "Не удалось поставить будильник", it)
                    }
                }
                Log.i(NotificationHelper.TAG, "Запланировано напоминаний: $planned")
            } catch (e: Exception) {
                Log.e(NotificationHelper.TAG, "Ошибка планирования уведомлений", e)
            }
        }
    }

    private fun requestCode(lessonId: String): Int = abs(lessonId.hashCode()) % 1_000_000

    private const val PREFS = "studibox_alarms"
    private const val KEY_IDS = "ids"

    private fun rememberId(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_IDS, emptySet())!!.toMutableSet()
        ids += id.toString()
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
    }

    private fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        ids.mapNotNull { it.toIntOrNull() }.forEach { id ->
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
