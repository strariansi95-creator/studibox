package com.studibox.app.data.remote

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.studibox.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Сеть Studibox.
 *
 * У части операторов РФ (Билайн и другие) нестабильно блокируется/режется
 * трафик к зарубежным облакам, включая Supabase/Cloudflare. Чтобы приложение
 * работало у всех, добавлены ДВА независимых слоя устойчивости — оба только
 * в транспорте, формат запросов/ответов и вся бизнес-логика (ScheduleApi,
 * ApiMapper, ScheduleRepository) не менялись ни на строчку:
 *
 * 1. [failoverInterceptor] — каждый запрос сначала уходит на RU-зеркало
 *    (см. backend/mirror/README.md), и только если зеркало недоступно или
 *    ответило ошибкой — напрямую на BuildConfig.BASE_URL (как раньше).
 * 2. [dohDns] — резолвинг доменов через DNS-over-HTTPS Яндекса, чтобы
 *    DNS-блокировка/подмена ответа у провайдера не мешала достучаться даже
 *    до самого зеркала. При сбое DoH используется обычный системный DNS.
 */
object NetworkModule {

    const val TAG = "StudiboxNet"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Сколько символов тела ответа печатать в Logcat при диагностике. */
    private const val BODY_LOG_LIMIT = 2000L

    private val mirrorUrl = BuildConfig.MIRROR_BASE_URL.toHttpUrlOrNull()

    /**
     * Диагностическое логирование сети. Работает и в Release, и в Debug —
     * без него в Release-сборке тело ответа не видно вообще (см. ниже).
     * peekBody() снимает копию тела, не мешая Retrofit прочитать оригинал.
     */
    private val statusInterceptor = Interceptor { chain ->
        val request = chain.request()
        val started = System.nanoTime()

        val response = chain.proceed(request)

        val tookMs = (System.nanoTime() - started) / 1_000_000
        val fullSize = response.peekBody(Long.MAX_VALUE).contentLength()

        val bodyPreview = runCatching {
            response.peekBody(BODY_LOG_LIMIT).string()
        }.getOrElse {
            "<не удалось прочитать body: ${it.message}>"
        }

        Log.i(
            TAG,
            "${request.method} ${request.url}\n" +
                "-> HTTP ${response.code}, ${fullSize}B, ${tookMs}ms\n" +
                "-> Body (первые $BODY_LOG_LIMIT симв.): $bodyPreview"
        )

        response
    }

    /**
     * Оригинальный путь запроса (всё после хоста, включая query) переносится
     * в параметр ?p= — зеркало просто перекладывает его на BASE_URL и отдаёт
     * ответ как есть. Так зеркало ничего не знает о структуре API и не
     * ломается при её изменении.
     */
    private fun toMirrorRequest(
        original: okhttp3.Request,
        mirror: okhttp3.HttpUrl
    ): okhttp3.Request {
        val originalUrl = original.url

        val forwardedPath = buildString {
            append(originalUrl.encodedPath)
            originalUrl.encodedQuery?.let {
                append('?').append(it)
            }
        }

        val mirrorUrlWithPath = mirror.newBuilder()
            .addQueryParameter("p", forwardedPath)
            .build()

        return original.newBuilder()
            .url(mirrorUrlWithPath)
            .build()
    }

    /**
     * Приложение-интерсептор, поэтому proceed() допустимо вызвать больше
     * одного раза — сначала на зеркало, при неудаче на исходный запрос.
     */
    private val failoverInterceptor = Interceptor { chain ->
        val original = chain.request()
        val mirror = mirrorUrl

        if (mirror == null || original.url.host == mirror.host) {
            // Зеркало не настроено, либо это уже запрос к нему (повторный проход) —
            // идём как обычно, напрямую.
            return@Interceptor chain.proceed(original)
        }

        val mirrorRequest = toMirrorRequest(original, mirror)

        val mirrorResponse: Response? = try {
            chain.proceed(mirrorRequest)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Зеркало недоступно (${e.message}), пробуем напрямую"
            )
            null
        }

        if (mirrorResponse != null && mirrorResponse.isSuccessful) {
            return@Interceptor mirrorResponse
        }

        mirrorResponse?.close()

        chain.proceed(original)
    }

    /**
     * DNS-over-HTTPS через Яндекс — устойчив к подмене/блокировке DNS-ответов
     * у части операторов РФ. IP самого DoH-сервера захардкожен, чтобы не
     * зависеть от резолва dns.yandex.net обычным (потенциально
     * подменяемым) DNS.
     */
    private fun buildDohDns(client: OkHttpClient): Dns? = try {
        val bootstrapClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.yandex.net/dns-query".toHttpUrlOrNull()!!)
            .bootstrapDnsHosts(
                InetAddress.getByName("77.88.8.8"),
                InetAddress.getByName("77.88.8.1")
            )
            .build()
    } catch (e: Exception) {
        Log.w(
            TAG,
            "DoH недоступен (${e.message}), используем системный DNS"
        )
        null
    }

    /** Системный DNS как надёжный запасной вариант, если DoH сам недоступен. */
    private fun fallbackDns(doh: Dns?): Dns {
        if (doh == null) return Dns.SYSTEM

        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return try {
                    doh.lookup(hostname)
                } catch (e: UnknownHostException) {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }
    }

    private fun okHttp(): OkHttpClient {
        // Базовый клиент — без DoH, используется и как рабочий, и как
        // "загрузчик" для самого DoH-резолвера.
        val baseBuilder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        val bootstrapClient = baseBuilder.build()

        val dns = fallbackDns(
            buildDohDns(bootstrapClient)
        )

        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dns(dns)
            .addInterceptor(failoverInterceptor)
            .addInterceptor(statusInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor { message ->
                    Log.d(TAG, message)
                }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return builder.build()
    }

    fun createApi(): ScheduleApi {
        Log.i(
            TAG,
            "BASE_URL = ${BuildConfig.BASE_URL}, " +
                "MIRROR_BASE_URL = ${BuildConfig.MIRROR_BASE_URL}"
        )

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttp())
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
            .create(ScheduleApi::class.java)
    }
}