package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.core.MemoryCache
import com.neomovies.playersparser.core.ProxyManager
import com.neomovies.playersparser.models.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Парсер для HDRezka
 * Поддерживает фильмы и сериалы с поиском, авторизацией, прокси и кэшем
 */
class RezkaParser(
    private val settings: RezkaSettings = RezkaSettings(),
    client: OkHttpClient? = null
) : BaseParser(client ?: createDefaultClient()) {

    private val proxyManager = ProxyManager(settings.proxies)

    companion object {
        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }

        private fun normalizeSearchName(name: String): String {
            return name.lowercase()
                .replace(Regex("[^a-zа-яё0-9]"), "")
                .trim()
        }
    }

    /**
     * Поиск контента на Rezka
     * Реализует полную логику из Lampac/Shared/Engine/Online/Rezka.cs
     */
    override suspend fun search(query: String): SearchResponse {
        return try {
            val searchUri = "${settings.corsHostOrDefault()}/search/?do=search&subaction=search&q=${URLEncoder.encode(query, "UTF-8")}"

            val headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                "Cache-Control" to "no-cache",
                "DNT" to "1",
                "Pragma" to "no-cache",
                "Referer" to "${settings.corsHost ?: settings.host}/"
            )

            val searchHtml = get(searchUri, headers) ?: return SearchResponse(
                results = emptyList(),
                error = "Failed to fetch search results"
            )

            // Проверка на ошибки доступа
            if (searchHtml.contains("class=\"error-code\"") && searchHtml.lowercase().contains("ошибка доступа")) {
                val errorMsg = when {
                    searchHtml.contains("(105)") || searchHtml.contains(">105<") || searchHtml.contains("(403)") -> 
                        "Ошибка доступа (105) - IP-адрес заблокирован"
                    searchHtml.contains("(101)") || searchHtml.contains(">101<") -> 
                        "Ошибка доступа (101) - Аккаунт заблокирован"
                    else -> "Ошибка доступа"
                }
                return SearchResponse(results = emptyList(), error = errorMsg)
            }

            val results = mutableListOf<SearchResult>()
            val normalizedQuery = normalizeSearchName(query)

            // Парсим результаты поиска из HTML
            val rows = searchHtml.split("\"b-content__inline_item\"")
            for (row in rows.drop(1)) {
                val hrefMatch = Regex("href=\"https?://[^/]+/([^\"]+)\">([^<]+)</a> ?<div>([0-9]{4})").find(row)
                if (hrefMatch != null) {
                    val href = hrefMatch.groupValues[1]
                    val title = hrefMatch.groupValues[2].trim()
                    val year = hrefMatch.groupValues[3].toIntOrNull() ?: 0

                    if (href.isNotEmpty() && title.isNotEmpty()) {
                        val imgMatch = Regex("<img src=\"([^\"]+)\"").find(row)
                        val poster = imgMatch?.groupValues?.get(1) ?: ""

                        // Проверяем совпадение названия
                        val normalizedTitle = normalizeSearchName(title)
                        if (normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle)) {
                            results.add(
                                SearchResult(
                                    id = href,
                                    name = title,
                                    type = if (row.contains("series") || row.contains("сериал")) "series" else "movie",
                                    year = year,
                                    poster = poster
                                )
                            )
                        }
                    }
                }
            }

            SearchResponse(results = results, total = results.size)
        } catch (e: Exception) {
            SearchResponse(
                results = emptyList(),
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Получить плеер для фильма/сериала
     * Реализует логику получения потоков из Lampac с поддержкой ajax и кэша
     * Использует метод Movie из RezkaInvoke.cs
     */
    override suspend fun getPlayer(
        id: String,
        type: String,
        season: Int?,
        episode: Int?
    ): PlayerResponse {
        return try {
            val proxy = proxyManager.get()
            val cacheKey = "rezka:player:$id:$type:$season:$episode"

            // Используем кэш для ускорения
            val result = MemoryCache.get(cacheKey, ttlSeconds = 600) {
                getPlayerInternal(id, type, season, episode, proxy)
            }

            result
        } catch (e: Exception) {
            PlayerResponse(error = e.message ?: "Unknown error")
        }
    }

    /**
     * Внутренняя логика получения плеера
     * Реализует логику из RezkaInvoke.Movie()
     */
    private suspend fun getPlayerInternal(
        id: String,
        type: String,
        season: Int?,
        episode: Int?,
        proxy: String?
    ): PlayerResponse {
        val corsHost = settings.corsHostOrDefault()
        val timestamp = System.currentTimeMillis() / 1000
        val random = (101..999).random()
        val apiUrl = "$corsHost/ajax/get_cdn_series/?t=${timestamp}${random}"

        // Формируем данные для POST запроса как в Lampac
        val data = when {
            type == "series" && season != null && episode != null -> {
                "id=$id&translator_id=1&season=$season&episode=$episode&favs=&action=get_stream"
            }
            type == "series" && season != null -> {
                "id=$id&translator_id=1&season=$season&favs=&action=get_stream"
            }
            else -> {
                "id=$id&translator_id=1&is_camrip=0&is_ads=0&is_director=0&favs=&action=get_movie"
            }
        }

        val headers = mapOf(
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "Cache-Control" to "no-cache",
            "DNT" to "1",
            "Origin" to corsHost,
            "Pragma" to "no-cache",
            "Referer" to "$corsHost/$id",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "X-Requested-With" to "XMLHttpRequest",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
        )

        val response = post(
            apiUrl,
            data,
            headers,
            proxy = proxy,
            realIp = settings.realIp
        ) ?: return PlayerResponse(success = false, error = "Failed to fetch data from Rezka API")

        try {
            val json = JSONObject(response)
            
            // Проверяем наличие ошибок в ответе
            if (json.has("error") && !json.isNull("error")) {
                val errorMsg = json.optString("error")
                return PlayerResponse(success = false, error = "Rezka API error: $errorMsg")
            }
            
            val url = json.optString("url")

            if (url.isEmpty() || url.lowercase() == "false") {
                return PlayerResponse(success = false, error = "No URL in response. Response: ${response.take(200)}")
            }

            // Парсим потоки из URL (как в getStreamLink)
            val streams = parseStreamLinks(url)

            if (streams.isEmpty()) {
                return PlayerResponse(success = false, error = "No streams found in URL. Decoded data length: ${url.length}")
            }

            // Формируем плейлист с разными качествами
            val playlist = streams.map { (quality, streamUrl) ->
                PlaylistItem(
                    url = streamUrl,
                    quality = quality,
                    type = if (streamUrl.contains(".m3u8") || streamUrl.contains(":hls:")) "hls" else "video"
                )
            }

            // Основной URL - первый поток
            val mainUrl = streams.values.firstOrNull() ?: return PlayerResponse(success = false, error = "No valid stream URL")

            return PlayerResponse(
                url = mainUrl,
                playlist = playlist,
                success = true
            )
        } catch (e: Exception) {
            return PlayerResponse(success = false, error = "Parse error: ${e.message}. Response: ${response.take(200)}")
        }
    }

    /**
     * Парсить потоки из закодированного URL
     * Реализует логику из getStreamLink в RezkaInvoke.cs
     */
    private fun parseStreamLinks(encodedData: String): Map<String, String> {
        val streams = mutableMapOf<String, String>()

        try {
            // Декодируем base64 если нужно
            val data = decodeBase64(encodedData)

            // Парсим потоки по качеству [2160p], [1080p], [720p], [480p], [360p]
            val qualities = listOf("2160p", "1080p", "720p", "480p", "360p")
            for (quality in qualities) {
                val pattern = Regex("""\[($quality|[^\]]+$quality[^\]]+)\]([^,\[]+)""")
                val match = pattern.find(data)
                if (match != null) {
                    val urlLine = match.groupValues[2]
                    if (urlLine.contains(".mp4") || urlLine.contains(".m3u8")) {
                        val urlMatch = Regex("""(https?://[^\[\n\r, ]+)""").find(urlLine)
                        if (urlMatch != null) {
                            var streamUrl = urlMatch.groupValues[1]

                            // Обработка HLS
                            if (settings.hls) {
                                if (!streamUrl.endsWith(".m3u8")) {
                                    streamUrl += ":hls:manifest.m3u8"
                                }
                            } else {
                                streamUrl = streamUrl.replace(":hls:manifest.m3u8", "")
                            }

                            streams[quality] = streamUrl
                        }
                    }
                }
            }

            // Если ничего не найдено, пытаемся найти любой URL
            if (streams.isEmpty()) {
                val anyUrlMatch = Regex("""(https?://[^\[\n\r, ]+)""").find(data)
                if (anyUrlMatch != null) {
                    var streamUrl = anyUrlMatch.groupValues[1]
                    if (settings.hls && !streamUrl.endsWith(".m3u8")) {
                        streamUrl += ":hls:manifest.m3u8"
                    }
                    streams["auto"] = streamUrl
                }
            }
        } catch (e: Exception) {
            // Если не удалось распарсить, возвращаем исходный URL
            streams["auto"] = encodedData
        }

        return streams
    }

    /**
     * Декодировать base64 данные
     * Реализует логику из decodeBase64 в RezkaInvoke.cs
     */
    private fun decodeBase64(data: String): String {
        if (!data.startsWith("#")) {
            return data
        }

        try {
            var decoded = data.removePrefix("#").removePrefix("#")

            // Удаляем мусорные строки
            val trashList = listOf(
                "JCQhIUAkJEBeIUAjJCRA", "QEBAQEAhIyMhXl5e", "IyMjI14hISMjIUBA",
                "Xl5eIUAjIyEhIyM=", "JCQjISFAIyFAIyM="
            )

            for (trash in trashList) {
                decoded = decoded.replace("//_//$trash", "")
            }

            try {
                val bytes = java.util.Base64.getDecoder().decode(decoded)
                return String(bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                decoded = Regex("//[^/]+_//").replace(decoded, "").replace("//_//", "")
                val bytes = java.util.Base64.getDecoder().decode(decoded)
                return String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            return data
        }
    }

    /**
     * Получить информацию о сериале (сезоны и эпизоды)
     */
    suspend fun getSeriesInfo(id: String): Map<Int, List<Int>>? {
        return try {
            val contentUrl = "${settings.corsHostOrDefault()}/$id"
            val contentHtml = get(contentUrl) ?: return null

            val seasons = mutableMapOf<Int, List<Int>>()

            // Ищем информацию о сезонах в HTML
            val seasonPattern = Regex("""<a href="[^"]*\?s=(\d+)[^"]*">([^<]+)</a>""")
            val seasonMatches = seasonPattern.findAll(contentHtml)

            for (match in seasonMatches) {
                val seasonNum = match.groupValues[1].toIntOrNull() ?: continue
                val episodes = mutableListOf<Int>()

                // Для каждого сезона ищем эпизоды
                val episodePattern = Regex("""<a href="[^"]*\?s=$seasonNum&e=(\d+)[^"]*">([^<]+)</a>""")
                val episodeMatches = episodePattern.findAll(contentHtml)

                for (episodeMatch in episodeMatches) {
                    val episodeNum = episodeMatch.groupValues[1].toIntOrNull() ?: continue
                    episodes.add(episodeNum)
                }

                if (episodes.isNotEmpty()) {
                    seasons[seasonNum] = episodes.sorted()
                }
            }

            if (seasons.isNotEmpty()) seasons else null
        } catch (e: Exception) {
            null
        }
    }

}
