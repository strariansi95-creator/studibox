package com.studibox.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studibox.app.AppGraph
import com.studibox.app.sync.SyncScheduler

/** После перезагрузки будильники теряются — восстанавливаем их. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.ensure(context)
        NotificationHelper.createChannels(context)
        SyncScheduler.schedulePeriodic(context)
        LessonNotificationScheduler.rescheduleToday(context)
        DeadlineNotificationScheduler.reschedule(context)
    }
}
