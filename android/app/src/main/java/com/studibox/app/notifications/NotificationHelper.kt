package com.studibox.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object NotificationHelper {

    const val CHANNEL_REMINDERS = "studibox_lesson_reminders"
    const val CHANNEL_DEADLINES = "studibox_deadlines"
    const val TAG = "StudiboxNotif"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(com.studibox.app.R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.studibox.app.R.string.notif_channel_reminders_desc)
            enableVibration(true)
        }
        val deadlines = NotificationChannel(
            CHANNEL_DEADLINES,
            context.getString(com.studibox.app.R.string.notif_channel_deadlines),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(com.studibox.app.R.string.notif_channel_deadlines_desc)
        }
        runCatching { manager.createNotificationChannels(listOf(channel, deadlines)) }
            .onFailure { Log.e(TAG, "Не удалось создать канал уведомлений", it) }
    }

    /** На Android 13+ уведомления требуют разрешения; его отсутствие не должно ломать приложение. */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
