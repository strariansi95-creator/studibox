package com.studibox.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.studibox.app.AppGraph
import com.studibox.app.data.prefs.UserRole
import com.studibox.app.data.repo.SyncResult
import com.studibox.app.notifications.DeadlineNotificationScheduler
import com.studibox.app.notifications.LessonNotificationScheduler
import kotlinx.coroutines.flow.first

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppGraph.ensure(applicationContext)
        // Дедлайны СДО не зависят от расписания — обновляем при каждом запуске.
        DeadlineNotificationScheduler.reschedule(applicationContext)
        return try {
            val role = AppGraph.settings.settings.first().userRole
            val result = if (role == UserRole.TEACHER) {
                AppGraph.scheduleRepository.refreshAllGroupsForTeacherMode(force = false)
            } else {
                AppGraph.scheduleRepository.sync(force = false)
            }
            when (result) {
                is SyncResult.Success -> {
                    Log.i(TAG, "Фоновая синхронизация: групп=${result.groups}, занятий=${result.lessons}")
                    LessonNotificationScheduler.rescheduleToday(applicationContext)
                    Result.success()
                }
                SyncResult.UpToDate -> {
                    Log.i(TAG, "Фоновая синхронизация: изменений нет")
                    LessonNotificationScheduler.rescheduleToday(applicationContext)
                    Result.success()
                }
                is SyncResult.Failed -> {
                    Log.w(TAG, "Фоновая синхронизация не удалась: ${result.reason}")
                    if (result.offline) Result.retry() else Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Непредвиденная ошибка в SyncWorker", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "StudiboxSync"
    }
}
