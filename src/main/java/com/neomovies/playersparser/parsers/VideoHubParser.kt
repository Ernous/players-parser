package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.core.MemoryCache
import com.neomovies.playersparser.core.ProxyManager
import com.neomovies.playersparser.models.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Парсер для VideoHUB (CDN VideoHUB)
 * Полная реализация логики из Lampac
 * Поддерживает фильмы и сериалы с HLS потоками, voiceStudio и кэшем
 */
class VideoHubParser(
    private val settings: VideoHubSettings = VideoHubSettings(),
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
     * Реализует логику из Lampac/Online/Controllers/CDNvideohub.cs с поддержкой voiceStudio и кэша
     */
    override suspend fun getPlayer(
        id: String,
        type: String,
        season: Int?,
        episode: Int?
    ): PlayerResponse {
        return try {
            val proxy = proxyManager.get()
            val cacheKey = "videohub:player:$id:$season:$episode"

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
     * Внутренняя логика получения плеера с поддержкой voiceStudio
     * Реализует логику из CDNvideohub.cs
     */
    private suspend fun getPlayerInternal(
        id: String,
        type: String,
        season: Int?,
        episode: Int?,
        proxy: String?
    ): PlayerResponse {
        val corsHost = settings.corsHostOrDefault()
        val url = "$corsHost/api/v1/player/sv/playlist?pub=${settings.pubId}&aggr=kp&id=$id"

        val response = try {
            get(url, proxy = proxy)
        } catch (e: javax.net.ssl.SSLException) {
            return PlayerResponse(success = false, error = "SSL error connecting to VideoHUB: ${e.message}")
        } catch (e: Exception) {
            return PlayerResponse(success = false, error = "Error fetching from VideoHUB: ${e.message}")
        } ?: return PlayerResponse(success = false, error = "Failed to fetch data from VideoHUB. URL: $url")
        val json = try {
            JSONObject(response)
        } catch (e: Exception) {
            return PlayerResponse(success = false, error = "Failed to parse JSON response: ${e.message}. Response: ${response.take(200)}")
        }

        if (!json.optBoolean("success", false)) {
            return PlayerResponse(success = false, error = json.optString("message", "Unknown error from VideoHUB API"))
        }

        val items = json.optJSONArray("items")
        if (items == null || items.length() == 0) {
            return PlayerResponse(success = false, error = "No items found in VideoHUB response")
        }

        val isSerial = json.optBoolean("isSerial", false)

        if (isSerial && season != null && episode != null) {
            // Для сериалов ищем нужный сезон и эпизод
            // Собираем все доступные voiceStudio для этого эпизода
            val playlist = mutableListOf<PlaylistItem>()

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.optInt("season") == season && item.optInt("episode") == episode) {
                    val vkId = item.optString("vkId")
                    if (vkId.isNotEmpty()) {
                        // Получаем HLS URL через video API как в CDNvideohub.Video()
                        val videoUrl = "$corsHost/api/v1/player/sv/video/$vkId"
                        val videoResponse = try {
                            get(videoUrl, proxy = proxy)
                        } catch (e: javax.net.ssl.SSLException) {
                            continue // Пропускаем этот элемент при SSL ошибке
                        } catch (e: Exception) {
                            continue // Пропускаем при других ошибках
                        }
                        if (videoResponse != null) {
                            val hlsMatch = Regex(""""hlsUrl":"([^"]+)""").find(videoResponse)
                            if (hlsMatch != null) {
                                val hlsUrl = hlsMatch.groupValues[1]
                                    .replace("u0026", "&")
                                    .replace("\\", "")
                                val voiceStudio = item.optString("voiceStudio")
                                playlist.add(PlaylistItem(
                                    url = hlsUrl,
                                    type = "hls",
                                    quality = voiceStudio.ifEmpty { "По умолчанию" }
                                ))
                            }
                        }
                    }
                }
            }

            if (playlist.isNotEmpty()) {
                return PlayerResponse(
                    url = playlist[0].url,
                    playlist = playlist,
                    success = true
                )
            }
            return PlayerResponse(success = false, error = "Episode not found for season $season, episode $episode")
        } else {
            // Для фильмов берем все доступные озвучки
            val playlist = mutableListOf<PlaylistItem>()

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val vkId = item.optString("vkId")
                if (vkId.isNotEmpty()) {
                    val videoUrl = "$corsHost/api/v1/player/sv/video/$vkId"
                    val videoResponse = try {
                        get(videoUrl, proxy = proxy)
                    } catch (e: javax.net.ssl.SSLException) {
                        continue // Пропускаем этот элемент при SSL ошибке
                    } catch (e: Exception) {
                        continue // Пропускаем при других ошибках
                    }
                    if (videoResponse != null) {
                        val hlsMatch = Regex(""""hlsUrl":"([^"]+)""").find(videoResponse)
                        if (hlsMatch != null) {
                            val hlsUrl = hlsMatch.groupValues[1]
                                .replace("u0026", "&")
                                .replace("\\", "")
                            val voiceStudio = item.optString("voiceStudio")
                                .ifEmpty { item.optString("voiceType") }
                                .ifEmpty { "По умолчанию" }
                            playlist.add(PlaylistItem(
                                url = hlsUrl,
                                type = "hls",
                                quality = voiceStudio
                            ))
                        }
                    }
                }
            }

            if (playlist.isNotEmpty()) {
                return PlayerResponse(
                    url = playlist[0].url,
                    playlist = playlist,
                    success = true
                )
            } else {
                return PlayerResponse(success = false, error = "No HLS URL found in VideoHUB response")
            }
        }
    }

    /**
     * Поиск контента на VideoHUB
     */
    override suspend fun search(query: String): SearchResponse {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "${settings.corsHostOrDefault()}/api/v1/search?q=$encodedQuery"

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
                        name = item.optString("title"),
                        type = if (item.optBoolean("isSerial")) "series" else "movie",
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
     * Получить информацию о сериале (сезоны и эпизоды)
     */
    suspend fun getSeriesInfo(id: String): Map<Int, List<Int>>? {
        return try {
            val url = "${settings.corsHost ?: settings.apiHost}/api/v1/player/sv/playlist?pub=${settings.pubId}&aggr=kp&id=$id"
            val response = get(url) ?: return null

            val json = JSONObject(response)
            val seasons = mutableMapOf<Int, List<Int>>()

            val items = json.optJSONArray("items") ?: return null

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val seasonNum = item.optInt("season")
                val episodeNum = item.optInt("episode")

                if (seasonNum > 0 && episodeNum > 0) {
                    if (!seasons.containsKey(seasonNum)) {
                        seasons[seasonNum] = mutableListOf()
                    }
                    (seasons[seasonNum] as? MutableList)?.add(episodeNum)
                }
            }

            // Сортируем эпизоды в каждом сезоне
            seasons.forEach { (_, episodes) ->
                (episodes as? MutableList)?.sort()
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
            val url = "${settings.corsHost ?: settings.apiHost}/api/v1/player/sv/playlist?pub=${settings.pubId}&aggr=kp&id=$id"
            val response = get(url) ?: return null

            val json = JSONObject(response)
            val qualities = mutableSetOf<String>()

            val items = json.optJSONArray("items") ?: return null

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.optInt("season") == season && item.optInt("episode") == episode) {
                    val quality = item.optString("quality")
                    if (quality.isNotEmpty()) {
                        qualities.add(quality)
                    }
                }
            }

            qualities.toList().sorted()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получить переводы для сезона
     */
    suspend fun getVoiceStudios(id: String, season: Int): List<String>? {
        return try {
            val url = "${settings.corsHost ?: settings.apiHost}/api/v1/player/sv/playlist?pub=${settings.pubId}&aggr=kp&id=$id"
            val response = get(url) ?: return null

            val json = JSONObject(response)
            val voiceStudios = mutableSetOf<String>()

            val items = json.optJSONArray("items") ?: return null

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.optInt("season") == season) {
                    val voiceStudio = item.optString("voiceStudio")
                    if (voiceStudio.isNotEmpty()) {
                        voiceStudios.add(voiceStudio)
                    }
                }
            }

            voiceStudios.toList().sorted()
        } catch (e: Exception) {
            null
        }
    }
}
