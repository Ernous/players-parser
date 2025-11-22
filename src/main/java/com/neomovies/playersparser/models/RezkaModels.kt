package com.neomovies.playersparser.models

/**
 * Модели для Rezka парсера
 */

data class RezkaSearchModel(
    val href: String? = null,
    val searchUri: String? = null,
    val similar: List<SimilarModel>? = null,
    val isEmpty: Boolean = false,
    val content: String? = null
)

data class SimilarModel(
    val title: String,
    val year: String,
    val href: String,
    val img: String
)

data class RezkaMovieModel(
    val streams: Map<String, String>? = null, // quality -> url
    val defaultQuality: String? = null,
    val error: String? = null
)

data class RezkaEpisodes(
    val seasons: Map<Int, List<RezkaEpisode>>? = null,
    val error: String? = null
)

data class RezkaEpisode(
    val episode: Int,
    val title: String? = null,
    val streams: Map<String, String>? = null
)

data class RezkaSettings(
    val host: String = "https://hdrezka.ag",
    val login: String? = null,
    val passwd: String? = null,
    val premium: Boolean = false,
    val reserve: Boolean = false,
    val uacdn: String? = null,
    val forceua: Boolean = false,
    val xrealip: Boolean = false,
    val xapp: Boolean = false,
    val hls: Boolean = true,
    val corsHost: String? = "https://cors.apn.monster",
    val realIp: String? = null,
    val proxies: List<String> = emptyList()
) {
    fun corsHostOrDefault(): String {
        return if (corsHost != null) {
            val clean = host.removePrefix("https://").removePrefix("http://")
            "${corsHost}/https://${clean}"
        } else host
    }
}
