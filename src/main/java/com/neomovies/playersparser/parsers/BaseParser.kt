package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.models.PlayerResponse
import com.neomovies.playersparser.models.SearchResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

abstract class BaseParser(protected val client: OkHttpClient) {

    /**
     * Основной метод получения плеера
     */
    abstract suspend fun getPlayer(
        id: String,
        type: String,
        season: Int? = null,
        episode: Int? = null
    ): PlayerResponse

    /**
     * Метод поиска (если поддерживается)
     */
    abstract suspend fun search(query: String): SearchResponse

    /**
     * Вспомогательный метод для GET запросов
     * Использует OkHttp, который передан в конструктор
     */
    protected fun get(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return response.body?.string()
                } else {
                    println("Request failed: ${response.code} for $url")
                    null
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}