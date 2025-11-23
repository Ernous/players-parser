package com.neomovies.playersparser.models

import org.json.JSONObject

// Settings
data class CollapsSettings(
    val apiHost: String = "https://apicollaps.cc",
    val token: String = "eedefb541aeba871dcfc756e6b31c02e"
)

// --- API Models ---

data class CollapsSearchItem(
    val id: String,
    val name: String,
    val originalName: String?,
    val year: Int,
    val kinopoiskId: String?,
    val imdbId: String?,
    val poster: String?,
    val type: String // "series", "cartoon-series", "movie", etc.
)

data class CollapsDetailsResponse(
    val id: String?,
    val type: String,
    val kinopoiskId: String?,
    val imdbId: String?,
    val iframeUrl: String?,
    val seasons: List<CollapsSeason>
) {
    // Удобный метод проверки, сериал ли это
    val isSerial: Boolean
        get() = seasons.isNotEmpty() || type.contains("series") || type.contains("serial")
}

data class CollapsSeason(
    val seasonNumber: Int,
    val episodes: List<CollapsEpisode>
)

data class CollapsEpisode(
    val episodeNumber: Int,
    val name: String?,
    val iframeUrl: String
)

// --- JSON Parsers ---

object CollapsJsonParser {

    fun parseSearchResponse(json: String): List<CollapsSearchItem> {
        val list = mutableListOf<CollapsSearchItem>()
        val obj = JSONObject(json)

        val results = obj.optJSONArray("results") ?: return emptyList()

        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            list.add(
                CollapsSearchItem(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    originalName = item.optString("origin_name"),
                    year = item.optInt("year"),
                    kinopoiskId = item.optString("kinopoisk_id").takeIf { it != "null" && it.isNotEmpty() },
                    imdbId = item.optString("imdb_id").takeIf { it != "null" && it.isNotEmpty() },
                    poster = item.optString("poster"),
                    type = item.optString("type", "movie")
                )
            )
        }
        return list
    }

    fun parseDetailsResponse(json: String): CollapsDetailsResponse {
        val obj = JSONObject(json)

        val type = obj.optString("type", "movie")
        val iframeUrl = obj.optString("iframe_url")

        val seasonsList = mutableListOf<CollapsSeason>()

        // Парсинг сериала
        val seasonsArray = obj.optJSONArray("seasons")
        if (seasonsArray != null) {
            for (i in 0 until seasonsArray.length()) {
                val sObj = seasonsArray.getJSONObject(i)
                val seasonNum = sObj.optInt("season")

                val epList = mutableListOf<CollapsEpisode>()
                val episodesArray = sObj.optJSONArray("episodes")
                if (episodesArray != null) {
                    for (j in 0 until episodesArray.length()) {
                        val eObj = episodesArray.getJSONObject(j)
                        epList.add(
                            CollapsEpisode(
                                episodeNumber = eObj.optInt("episode"),
                                name = eObj.optString("name"),
                                iframeUrl = eObj.optString("iframe_url")
                            )
                        )
                    }
                }
                seasonsList.add(CollapsSeason(seasonNum, epList))
            }
        }

        return CollapsDetailsResponse(
            id = obj.optString("id"),
            type = type,
            kinopoiskId = obj.optString("kinopoisk_id"),
            imdbId = obj.optString("imdb_id"),
            iframeUrl = iframeUrl,
            seasons = seasonsList
        )
    }
}