package com.neomovies.playersparser.parsers

// Импортируем все модели: и специфичные для VideoHub, и общие (PlayerResponse, etc.)
import com.neomovies.playersparser.models.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Парсер для VideoHub (CDN Video Hub)
 * API: https://plapi.cdnvideohub.com/api/v1/player/sv
 */
class VideoHubParser(
    private val settings: VideoHubSettings = VideoHubSettings(),
    client: OkHttpClient? = null
) : BaseParser(client ?: createDefaultClient()) {

    companion object {
        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Получить плеер для фильма/сериала
     */
    override suspend fun getPlayer(
        id: String,
        type: String,
        season: Int?,
        episode: Int?
    ): PlayerResponse {
        return try {
            // Шаг 1: Получаем плейлист с информацией о сезонах/озвучках
            val playlistUrl = "${settings.apiHost}/playlist?pub=${settings.pub}&aggr=${settings.aggr}&id=$id"
            
            // Метод get() берется из BaseParser
            val playlistJson = get(playlistUrl) ?: return PlayerResponse(
                success = false,
                error = "Failed to fetch playlist from VideoHub"
            )

            val playlist = VideoHubJsonParser.parsePlaylistResponse(playlistJson)

            // Если это сериал и не указаны season/episode, возвращаем ошибку
            if (playlist.isSerial && (season == null || episode == null)) {
                return PlayerResponse(
                    success = false,
                    error = "Series detected. Please specify season and episode"
                )
            }

            // Выбираем нужный элемент
            val selectedItem = if (playlist.isSerial) {
                playlist.items.firstOrNull { it.season == season && it.episode == episode }
                    ?: return PlayerResponse(
                        success = false,
                        error = "Episode S${season}E${episode} not found"
                    )
            } else {
                // Для фильма берем первый элемент (обычно основная озвучка)
                playlist.items.firstOrNull()
                    ?: return PlayerResponse(
                        success = false,
                        error = "No items found in playlist"
                    )
            }

            // Шаг 2: Получаем видео по vkId
            val videoUrl = "${settings.apiHost}/video/${selectedItem.vkId}"
            val videoJson = get(videoUrl) ?: return PlayerResponse(
                success = false,
                error = "Failed to fetch video from VideoHub"
            )

            val video = VideoHubJsonParser.parseVideoResponse(videoJson)

            // Шаг 3: Формируем плейлист с доступными качествами
            val playlistItems = buildPlaylist(video)

            // Выбираем основной URL (HLS или DASH)
            val mainUrl = video.sources.hlsUrl.takeIf { it.isNotEmpty() }
                ?: video.sources.dashUrl.takeIf { it.isNotEmpty() }
                ?: video.sources.mpegFullHdUrl.takeIf { it.isNotEmpty() }
                ?: video.sources.mpegHighUrl.takeIf { it.isNotEmpty() }
                ?: return PlayerResponse(
                    success = false,
                    error = "No valid stream URLs found"
                )

            PlayerResponse(
                success = true,
                url = mainUrl,
                playlist = playlistItems
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PlayerResponse(success = false, error = "VideoHub error: ${e.message}")
        }
    }

    /**
     * Поиск контента (не реализовано для VideoHub)
     */
    override suspend fun search(query: String): SearchResponse {
        return SearchResponse(
            results = emptyList(),
            error = "Search not implemented for VideoHub"
        )
    }

    // --- Дополнительные публичные методы (не из BaseParser, но полезные) ---

    /**
     * Получить информацию о сериале (сезоны и эпизоды)
     */
    suspend fun getSeriesInfo(id: String): Map<Int, List<Int>>? {
        return try {
            val playlistUrl = "${settings.apiHost}/playlist?pub=${settings.pub}&aggr=${settings.aggr}&id=$id"
            val playlistJson = get(playlistUrl) ?: return null

            val playlist = VideoHubJsonParser.parsePlaylistResponse(playlistJson)

            if (!playlist.isSerial) return null

            // Группируем эпизоды по сезонам
            val seasons = mutableMapOf<Int, MutableList<Int>>()
            playlist.items.forEach { item ->
                if (item.season != null && item.episode != null) {
                    seasons.getOrPut(item.season) { mutableListOf() }
                        .add(item.episode)
                }
            }

            // Сортируем эпизоды в каждом сезоне
            seasons.forEach { (_, episodes) ->
                episodes.sort()
            }

            seasons.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получить доступные озвучки для фильма
     */
    suspend fun getVoices(id: String): List<String>? {
        return try {
            val playlistUrl = "${settings.apiHost}/playlist?pub=${settings.pub}&aggr=${settings.aggr}&id=$id"
            val playlistJson = get(playlistUrl) ?: return null

            val playlist = VideoHubJsonParser.parsePlaylistResponse(playlistJson)

            // Собираем уникальные озвучки
            playlist.items
                .mapNotNull { item ->
                    when {
                        item.voiceType.isNotEmpty() -> item.voiceType
                        item.voiceStudio.isNotEmpty() -> item.voiceStudio
                        else -> null
                    }
                }
                .distinct()
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Построить плейлист с доступными качествами
     */
    private fun buildPlaylist(video: VideoHubVideoResponse): List<PlaylistItem> {
        val playlist = mutableListOf<PlaylistItem>()

        // Добавляем HLS (основной формат)
        if (video.sources.hlsUrl.isNotEmpty()) {
            playlist.add(
                PlaylistItem(
                    url = video.sources.hlsUrl,
                    type = "hls",
                    quality = "HLS"
                )
            )
        }

        // Добавляем DASH
        if (video.sources.dashUrl.isNotEmpty()) {
            playlist.add(
                PlaylistItem(
                    url = video.sources.dashUrl,
                    type = "dash",
                    quality = "DASH"
                )
            )
        }

        // Добавляем прогрессивные потоки по качеству
        val qualityMap = listOf(
            "4K" to video.sources.mpeg4kUrl,
            "2K" to video.sources.mpeg2kUrl,
            "Full HD" to video.sources.mpegFullHdUrl,
            "HD" to video.sources.mpegHighUrl,
            "Medium" to video.sources.mpegMediumUrl,
            "Low" to video.sources.mpegLowUrl,
            "Lowest" to video.sources.mpegLowestUrl
        )

        qualityMap.forEach { (quality, url) ->
            if (url.isNotEmpty()) {
                playlist.add(
                    PlaylistItem(
                        url = url,
                        type = "video",
                        quality = quality
                    )
                )
            }
        }

        return playlist
    }
}