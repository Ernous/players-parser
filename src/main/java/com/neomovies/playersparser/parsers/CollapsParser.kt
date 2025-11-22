package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.core.MemoryCache
import com.neomovies.playersparser.core.ProxyManager
import com.neomovies.playersparser.models.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Парсер для Collaps (поддерживает DASH и HLS)
 */
class CollapsParser(
    private val settings: CollapsSettings = CollapsSettings(),
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
    }

    /**
     * Получить плеер для фильма/сериала
     * Поддерживает как DASH так и HLS потоки
     */
    override suspend fun getPlayer(
        id: String,
        type: String,
        season: Int?,
        episode: Int?
    ): PlayerResponse {
        return try {
            val proxy = proxyManager.get()
            val cacheKey = "collaps:player:$id"

            // Используем кэш для ускорения
            val result = MemoryCache.get(cacheKey, ttlSeconds = 600) {
                getPlayerInternal(id, proxy)
            }

            result
        } catch (e: Exception) {
            PlayerResponse(error = e.message ?: "Unknown error")
        }
    }

    /**
     * Внутренняя логика получения плеера с поддержкой reserve
     * Реализует логику из CollapsInvoke.Embed() и Html()
     */
    private suspend fun getPlayerInternal(id: String, proxy: String?): PlayerResponse {
        val corsHost = settings.corsHostOrDefault()
        
        // Определяем тип ID (IMDB, KP или ORID) как в CollapsInvoke.Embed()
        val embedUrl = when {
            id.startsWith("tt") -> "$corsHost/embed/imdb/$id"
            id.all { it.isDigit() } && id.length < 10 -> "$corsHost/embed/kp/$id"
            else -> "$corsHost/embed/movie/$id"
        }

        val content = get(embedUrl, proxy = proxy) ?: return PlayerResponse(success = false, error = "Failed to fetch embed from Collaps. URL: $embedUrl")

        // Проверяем, есть ли информация о сезонах (сериал)
        if (content.contains("seasons:")) {
            // Это сериал - парсим сезоны и эпизоды
            try {
                val seasonsMatch = Regex("""seasons:([^\n\r]+)""").find(content)
                if (seasonsMatch != null) {
                    // Для сериалов нужно использовать другой подход
                    // Пока возвращаем ошибку, но с информацией
                    return PlayerResponse(success = false, error = "Series detected. Use getSeriesInfo() first or specify season/episode")
                }
            } catch (e: Exception) {
                return PlayerResponse(success = false, error = "Failed to parse series data: ${e.message}")
            }
        }

        // Это фильм - парсим потоки как в CollapsInvoke.Html()
        // Пробуем разные варианты регулярных выражений
        var hlsMatch = Regex("""hls:\s+"(https?://[^"]+\.m3u[^"]+)""").find(content)
        if (hlsMatch == null) {
            hlsMatch = Regex("""hls:\s*"([^"]+)""").find(content)
        }
        if (hlsMatch == null) {
            hlsMatch = Regex("""["']hls["']:\s*["']([^"']+)""").find(content)
        }
        
        var dashMatch = Regex("""dasha?:\s+"(https?://[^"]+\.mp[^"]+)""").find(content)
        if (dashMatch == null) {
            dashMatch = Regex("""dasha?:\s*"([^"]+)""").find(content)
        }
        if (dashMatch == null) {
            dashMatch = Regex("""["']dash["']:\s*["']([^"']+)""").find(content)
        }

        val useDash = if (settings.two) settings.useDash else false

        val playlist = mutableListOf<PlaylistItem>()

        // Получаем название озвучки
        val audioMatch = Regex("""audio:\s*\{\s*"names"\s*:\s*\["([^"]+)""").find(content)
        val voiceName = audioMatch?.groupValues?.getOrNull(1) ?: "По умолчанию"

        when {
            useDash && dashMatch != null -> {
                val dashUrl = dashMatch.groupValues[1].replace("\\u0026", "&").replace("\\", "")
                playlist.add(PlaylistItem(url = dashUrl, type = "dash", quality = voiceName))
                return PlayerResponse(
                    url = dashUrl,
                    playlist = playlist,
                    success = true
                )
            }
            hlsMatch != null -> {
                val hlsUrl = hlsMatch.groupValues[1].replace("\\u0026", "&").replace("\\", "")
                playlist.add(PlaylistItem(url = hlsUrl, type = "hls", quality = voiceName))
                return PlayerResponse(
                    url = hlsUrl,
                    playlist = playlist,
                    success = true
                )
            }
            else -> {
                // Пробуем найти любой URL потока
                val anyUrlMatch = Regex("""(https?://[^"'\s]+\.(m3u8|mp4|mpd))""").find(content)
                if (anyUrlMatch != null) {
                    val streamUrl = anyUrlMatch.groupValues[1]
                    val type = when {
                        streamUrl.contains(".m3u8") -> "hls"
                        streamUrl.contains(".mpd") -> "dash"
                        else -> "video"
                    }
                    playlist.add(PlaylistItem(url = streamUrl, type = type, quality = voiceName))
                    return PlayerResponse(
                        url = streamUrl,
                        playlist = playlist,
                        success = true
                    )
                }
                return PlayerResponse(success = false, error = "No valid stream found in embed content. Content length: ${content.length}, Contains 'hls': ${content.contains("hls")}, Contains 'dash': ${content.contains("dash")}")
            }
        }
    }

    /**
     * Поиск контента на Collaps
     */
    override suspend fun search(query: String): SearchResponse {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "${settings.corsHostOrDefault()}/list?token=${settings.token}&name=$encodedQuery"

            val response = get(url) ?: return SearchResponse(
                results = emptyList(),
                error = "Failed to fetch search results"
            )

            val json = JSONObject(response)
            val results = mutableListOf<SearchResult>()

            val items = json.optJSONArray("results") ?: return SearchResponse(
                results = emptyList(),
                error = "No results found"
            )

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                results.add(
                    SearchResult(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        type = item.optString("type"), // "movie" или "series"
                        year = item.optInt("year"),
                        poster = item.optString("poster")
                    )
                )
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
     * Получить URL embed из iframe
     * Парсит DASH и HLS потоки из HTML
     */
    private suspend fun getEmbedUrl(iframeUrl: String): CollapsEmbedResponse {
        return try {
            val response = get(iframeUrl) ?: return CollapsEmbedResponse(error = "Failed to fetch iframe")

            // Ищем DASH manifest URL в HTML
            val dashMatch = Regex("""data-dash="([^"]+)""").find(response)
            val dashUrl = dashMatch?.groupValues?.getOrNull(1)

            // Ищем HLS manifest URL в HTML
            val hlsMatch = Regex("""data-hls="([^"]+)""").find(response)
            val hlsUrl = hlsMatch?.groupValues?.getOrNull(1)

            // Ищем обычный URL потока
            val urlMatch = Regex("""src="([^"]+)""").find(response)
            val url = urlMatch?.groupValues?.getOrNull(1)

            CollapsEmbedResponse(
                url = url,
                dashUrl = dashUrl,
                hlsUrl = hlsUrl
            )
        } catch (e: Exception) {
            CollapsEmbedResponse(error = e.message)
        }
    }

    /**
     * Получить информацию о сериале (сезоны и эпизоды)
     */
    suspend fun getSeriesInfo(id: String): Map<Int, List<Int>>? {
        return try {
            val url = "${settings.corsHostOrDefault()}/seasons?token=${settings.token}&id=$id"
            val response = get(url) ?: return null

            val json = JSONObject(response)
            val seasons = mutableMapOf<Int, List<Int>>()

            val seasonsArray = json.optJSONArray("seasons") ?: return null
            for (i in 0 until seasonsArray.length()) {
                val season = seasonsArray.getJSONObject(i)
                val seasonNum = season.optInt("number")
                val episodes = mutableListOf<Int>()

                val episodesArray = season.optJSONArray("episodes") ?: continue
                for (j in 0 until episodesArray.length()) {
                    episodes.add(episodesArray.getInt(j))
                }

                seasons[seasonNum] = episodes
            }

            if (seasons.isNotEmpty()) seasons else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получить качества видео для эпизода
     */
    suspend fun getQualities(id: String, season: Int, episode: Int): List<String>? {
        return try {
            val url = "${settings.corsHostOrDefault()}/qualities?token=${settings.token}&id=$id&season=$season&episode=$episode"
            val response = get(url) ?: return null

            val json = JSONObject(response)
            val qualities = mutableListOf<String>()

            val qualitiesArray = json.optJSONArray("qualities") ?: return null
            for (i in 0 until qualitiesArray.length()) {
                qualities.add(qualitiesArray.getString(i))
            }

            qualities.sorted()
        } catch (e: Exception) {
            null
        }
    }
}
