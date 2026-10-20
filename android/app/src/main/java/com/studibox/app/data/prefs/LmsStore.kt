package com.studibox.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.studibox.app.data.remote.LmsSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.lmsDataStore: DataStore<Preferences> by preferencesDataStore(name = "studibox_lms")

/**
 * Сессия СДО СВФУ. Хранится только токен мобильного API Moodle —
 * пароль никогда не сохраняется. Отдельный файл от настроек расписания,
 * чтобы выход из СДО ничего не трогал в выборе группы/преподавателя.
 */
class LmsStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = intPreferencesKey("user_id")
        val FULL_NAME = stringPreferencesKey("full_name")
        val USER_NAME = stringPreferencesKey("user_name")
        /** Ручной выбор курса для предмета: "ключ предмета\tid курса". */
        val COURSE_OVERRIDES = stringSetPreferencesKey("course_overrides")
    }

    private val data: Flow<Preferences> = context.lmsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val session: Flow<LmsSession?> = data.map { prefs ->
        val token = prefs[Keys.TOKEN]?.takeIf { it.isNotBlank() } ?: return@map null
        LmsSession(
            token = token,
            userId = prefs[Keys.USER_ID] ?: return@map null,
            fullName = prefs[Keys.FULL_NAME].orEmpty(),
            userName = prefs[Keys.USER_NAME].orEmpty()
        )
    }

    suspend fun save(session: LmsSession) {
        context.lmsDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = session.token
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.FULL_NAME] = session.fullName
            prefs[Keys.USER_NAME] = session.userName
        }
    }

    suspend fun clear() {
        context.lmsDataStore.edit { it.clear() }
    }

    suspend fun courseOverrides(): Map<String, Int> =
        data.first()[Keys.COURSE_OVERRIDES].orEmpty().mapNotNull { entry ->
            val key = entry.substringBefore('\t')
            val id = entry.substringAfter('\t', "").toIntOrNull() ?: return@mapNotNull null
            key to id
        }.toMap()

    suspend fun setCourseOverride(subjectKey: String, courseId: Int) {
        context.lmsDataStore.edit { prefs ->
            val rest = prefs[Keys.COURSE_OVERRIDES].orEmpty().filterNot { it.substringBefore('\t') == subjectKey }
            prefs[Keys.COURSE_OVERRIDES] = rest.toSet() + "$subjectKey\t$courseId"
        }
    }
}
