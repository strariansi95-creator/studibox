package com.studibox.app.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Реальный API проекта:
 * https://gdjpevzioenbeyammukm.supabase.co/functions/v1/schedule-api/
 *
 * Ответы принимаются как [JsonElement] и разбираются в [ApiMapper].
 * Так приложение переживает любые допустимые вариации формата ответа
 * и не падает с SerializationException на неизвестном поле.
 */
interface ScheduleApi {

    @GET("api/health")
    suspend fun health(): JsonElement

    @GET("api/version")
    suspend fun version(): JsonElement

    @GET("api/groups")
    suspend fun groups(): JsonElement

    // ВАЖНО: без префикса "api/" — проверено вручную на реальном
    // развёрнутом backend: .../schedule-api/schedule/{group} отдаёт
    // настоящие данные, а .../schedule-api/api/schedule/{group} отвечает
    // "Not found". То есть именно этот один маршрут на сервере не имеет
    // префикса "api/", в отличие от /api/groups и /api/version, которые
    // как раз его используют и уже работают — их не трогал.
    @GET("schedule/{group}")
    suspend fun schedule(@Path(value = "group", encoded = false) group: String): JsonElement
}
