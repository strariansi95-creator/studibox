package com.studibox.app.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK = "studibox_periodic_sync"
    private const val ONE_SHOT_WORK = "studibox_manual_sync"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Периодическая проверка обновлений примерно раз в 30 минут —
     * это минимальный интервал, разрешённый WorkManager.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(SyncWorker.TAG, "Периодическая синхронизация запланирована (30 мин)")
        }.onFailure { Log.e(SyncWorker.TAG, "Не удалось запланировать синхронизацию", it) }
    }

    /** Разовая синхронизация — используется при старте приложения. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT_WORK, ExistingWorkPolicy.REPLACE, request)
        }.onFailure { Log.e(SyncWorker.TAG, "Не удалось запустить синхронизацию", it) }
    }
}
