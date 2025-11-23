package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.models.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class CollapsParser(
    private val settings: CollapsSettings = CollapsSettings(),
    client: OkHttpClient? = null
) : BaseParser(client ?: createDefaultClient()) {

    companion object {
        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
    }

    override suspend fun search(query: String): SearchResponse {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "${settings.apiHost}/list?token=${settings.token}&name=$encodedQuery"
            val json = get(url) ?: return SearchResponse(emptyList(), "Failed to fetch search results")
            val items = CollapsJsonParser.parseSearchResponse(json)
            SearchResponse(results = items)
        } catch (e: Exception) {
            SearchResponse(emptyList(), "Collaps Error: ${e.message}")
        }
    }

    override suspend fun getPlayer(
        id: String,
        type: String,
        season: Int?,
        episode: Int?
    ): PlayerResponse {
        return try {
            // 1. Получаем данные через API
            val url = "${settings.apiHost}/franchise/details?token=${settings.token}&kinopoisk_id=$id"
            val json = get(url) ?: return PlayerResponse(success = false, error = "Failed to fetch details")
            val details = CollapsJsonParser.parseDetailsResponse(json)

            // 2. Определяем нужный Iframe URL
            // Используем свойство isSerial, которое мы добавили в модель (оно проверяет seasons.isNotEmpty)
            val iframeUrl = if (details.isSerial) {
                if (season == null || episode == null) {
                    return PlayerResponse(success = false, error = "Series detected (${details.type}). Please specify season and episode")
                }
                val foundSeason = details.seasons.find { it.seasonNumber == season }
                    ?: return PlayerResponse(success = false, error = "Season $season not found")
                val foundEpisode = foundSeason.episodes.find { it.episodeNumber == episode }
                    ?: return PlayerResponse(success = false, error = "Episode $episode not found")
                foundEpisode.iframeUrl
            } else {
                details.iframeUrl
            }

            if (iframeUrl.isNullOrEmpty()) {
                return PlayerResponse(success = false, error = "Iframe URL not found")
            }

            // 3. Парсим HTML iframe-а
            val streams = extractStreamsFromIframe(iframeUrl)

            if (streams.isEmpty()) {
                // Если не смогли вытащить потоки (капча или защита), возвращаем хотя бы iframe
                return PlayerResponse(
                    success = true,
                    url = iframeUrl, // Возвращаем iframe как fallback
                    playlist = null,
                    error = "Stream extraction failed, returned iframe"
                )
            }

            val mainUrl = streams.find { it.type == "dash" }?.url ?: streams.first().url

            PlayerResponse(
                success = true,
                url = mainUrl,
                playlist = streams
            )

        } catch (e: Exception) {
            e.printStackTrace()
            PlayerResponse(success = false, error = "Collaps Error: ${e.message}")
        }
    }

    private fun extractStreamsFromIframe(iframeUrl: String): List<PlaylistItem> {
        return try {
            val request = Request.Builder()
                .url(iframeUrl)
                .header("Referer", "https://kinopoisk.ru/")
                .build()

            val html = client.newCall(request).execute().use { it.body?.string() } ?: return emptyList()
            val playlist = mutableListOf<PlaylistItem>()

            // Ищем HLS
            val hlsMatcher = Pattern.compile("[\"']?hls[\"']?\\s*:\\s*[\"']([^\"']+)[\"']").matcher(html)
            if (hlsMatcher.find()) {
                playlist.add(
                    PlaylistItem(
                        url = hlsMatcher.group(1).replace("\\/", "/"), // Фикс экранированных слешей
                        type = "hls",
                        quality = "Auto",
                        headers = mapOf("Referer" to iframeUrl)
                    )
                )
            }

            // Ищем DASH
            val dashMatcher = Pattern.compile("[\"']?dash[\"']?\\s*:\\s*[\"']([^\"']+)[\"']").matcher(html)
            if (dashMatcher.find()) {
                playlist.add(
                    PlaylistItem(
                        url = dashMatcher.group(1).replace("\\/", "/"),
                        type = "dash",
                        quality = "Auto (DASH)",
                        headers = mapOf("Referer" to iframeUrl)
                    )
                )
            }

            playlist
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSeriesInfo(kpId: String): Map<Int, List<Int>>? {
        return try {
            val url = "${settings.apiHost}/franchise/details?token=${settings.token}&kinopoisk_id=$kpId"
            val json = get(url) ?: return null
            val details = CollapsJsonParser.parseDetailsResponse(json)

            if (!details.isSerial) {
                println("CollapsParser: Item $kpId is not a serial (type=${details.type})")
                return null
            }

            val result = mutableMapOf<Int, List<Int>>()
            details.seasons.forEach { season ->
                result[season.seasonNumber] = season.episodes.map { it.episodeNumber }.sorted()
            }
            if (result.isEmpty()) return null

            result.toSortedMap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}