package com.studibox.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.studibox.app.MainActivity
import com.studibox.app.R

class LessonReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
        val details = intent.getStringExtra(EXTRA_DETAILS).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_ID, subject.hashCode())
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Через $minutes минут: $subject"
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: NotificationHelper.CHANNEL_REMINDERS

        if (subject.isBlank()) return
        if (!NotificationHelper.hasPermission(context)) {
            Log.w(NotificationHelper.TAG, "Нет разрешения POST_NOTIFICATIONS — уведомление пропущено")
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(details.ifBlank { "Занятие скоро начнётся" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
            .onFailure { Log.e(NotificationHelper.TAG, "Не удалось показать уведомление", it) }
    }

    companion object {
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_DETAILS = "extra_details"
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_ID = "extra_id"
        /** Готовый заголовок (для дедлайнов), иначе «Через N минут: …». */
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL = "extra_channel"
    }
}
