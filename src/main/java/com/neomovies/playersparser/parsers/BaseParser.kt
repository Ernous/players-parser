package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.models.PlayerResponse
import com.neomovies.playersparser.models.SearchResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Proxy
import java.net.SocketAddress
import java.util.concurrent.TimeUnit

/**
 * Базовый класс для всех парсеров плееров
 */
abstract class BaseParser(
    protected val client: OkHttpClient = createDefaultClient()
) {
    companion object {
        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .hostnameVerifier { _, _ -> true } // Отключаем проверку hostname для SSL
                .build()
        }

        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Получить плеер по ID
     */
    abstract suspend fun getPlayer(
        id: String,
        type: String = "movie",
        season: Int? = null,
        episode: Int? = null
    ): PlayerResponse

    /**
     * Поиск контента
     */
    abstract suspend fun search(query: String): SearchResponse

    /**
     * Выполнить GET запрос с поддержкой прокси и расширенных headers
     */
    protected suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        proxy: String? = null,
        realIp: String? = null,
        xApp: Boolean = false
    ): String? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)

            // Добавляем стандартные headers
            headers.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    requestBuilder.header(key, value)
                }
            }

            // Добавляем расширенные headers
            if (!realIp.isNullOrEmpty()) {
                requestBuilder.header("X-Real-IP", realIp)
                requestBuilder.header("X-Forwarded-For", realIp)
            }
            if (xApp) {
                requestBuilder.header("X-App-Hdrezka-App", "1")
            }

            // Создаем клиент с прокси если нужно
            val httpClient = if (!proxy.isNullOrEmpty()) {
                createClientWithProxy(proxy)
            } else {
                client
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Выполнить POST запрос с поддержкой прокси
     */
    protected suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        proxy: String? = null,
        realIp: String? = null
    ): String? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")

            headers.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    requestBuilder.header(key, value)
                }
            }

            if (!realIp.isNullOrEmpty()) {
                requestBuilder.header("X-Real-IP", realIp)
            }

            val httpClient = if (!proxy.isNullOrEmpty()) {
                createClientWithProxy(proxy)
            } else {
                client
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Создать OkHttpClient с прокси
     */
    private fun createClientWithProxy(proxyUrl: String): OkHttpClient {
        return try {
            val uri = java.net.URI(proxyUrl)
            val socketAddress = java.net.InetSocketAddress(uri.host, uri.port)
            val proxy = Proxy(Proxy.Type.HTTP, socketAddress)

            OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            client
        }
    }
}
