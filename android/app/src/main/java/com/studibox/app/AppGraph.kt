package com.studibox.app

import android.content.Context
import com.studibox.app.data.local.StudiboxDatabase
import com.studibox.app.data.prefs.LmsStore
import com.studibox.app.data.prefs.SettingsStore
import com.studibox.app.data.remote.NetworkModule
import com.studibox.app.data.remote.ScheduleApi
import com.studibox.app.data.repo.LectureStorage
import com.studibox.app.data.repo.LmsRepository
import com.studibox.app.data.repo.NotesRepository
import com.studibox.app.data.repo.ScheduleRepository

/** Простой контейнер зависимостей без сторонних DI-библиотек. */
object AppGraph {

    @Volatile
    private var initialized = false

    lateinit var appContext: Context
        private set
    lateinit var database: StudiboxDatabase
        private set
    lateinit var api: ScheduleApi
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var scheduleRepository: ScheduleRepository
        private set
    lateinit var notesRepository: NotesRepository
        private set
    lateinit var lmsRepository: LmsRepository
        private set
    lateinit var lectureStorage: LectureStorage
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            database = StudiboxDatabase.get(appContext)
            api = NetworkModule.createApi()
            settings = SettingsStore(appContext)
            scheduleRepository = ScheduleRepository(database, api, settings)
            notesRepository = NotesRepository(database.noteDao())
            lmsRepository = LmsRepository(LmsStore(appContext))
            lectureStorage = LectureStorage(appContext)
            initialized = true
        }
    }

    /** Безопасная точка входа для WorkManager и BroadcastReceiver'ов. */
    fun ensure(context: Context) = init(context)
}
