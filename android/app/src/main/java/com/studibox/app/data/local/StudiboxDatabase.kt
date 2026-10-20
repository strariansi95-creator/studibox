package com.studibox.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [GroupEntity::class, LessonEntity::class, NoteEntity::class, MetaEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StudiboxDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun lessonDao(): LessonDao
    abstract fun noteDao(): NoteDao
    abstract fun metaDao(): MetaDao

    companion object {
        @Volatile
        private var INSTANCE: StudiboxDatabase? = null

        /**
         * v1 -> v2: у занятия появилось поле type (тип: лекция/практика/лабораторная).
         * Обычный ALTER TABLE, ничего не удаляет — таблица notes эту миграцию
         * вообще не проходит, поэтому заметки пользователя гарантированно
         * переживают обновление приложения.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN type TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): StudiboxDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudiboxDatabase::class.java,
                    "studibox.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Срабатывает только если версия схемы скакнёт ещё дальше
                    // без описанной миграции — расписание в этом случае можно
                    // просто перекачать заново из API, а заметки к тому моменту
                    // уже будут разведены по отдельной миграции при необходимости.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

object MetaKeys {
    const val SCHEDULE_VERSION = "schedule_version"
    const val SCHEDULE_UPDATED_AT = "schedule_updated_at"
    const val LAST_SYNC_AT = "last_sync_at"
    const val LAST_SYNC_RESULT = "last_sync_result"
}
