package com.studibox.app

import android.app.Application
import android.util.Log
import com.studibox.app.notifications.NotificationHelper
import com.studibox.app.sync.SyncScheduler

class StudiboxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        NotificationHelper.createChannels(this)
        SyncScheduler.schedulePeriodic(this)
        Log.i("StudiboxNet", "Приложение запущено, BASE_URL=${BuildConfig.BASE_URL}")
    }
}
