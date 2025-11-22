package com.neomovies.playersparser.models

/**
 * Модели для VideoHUB парсера
 */

data class VideoHubSettings(
    val apiHost: String = "https://plapi.cdnvideohub.com", // Updated to match Lampac implementation
    val pubId: String = "12",  // Публикатор ID из Lampac
    val corsHost: String? = "https://cors.apn.monster", // default CORS proxy used in Lampac
    val proxies: List<String> = emptyList()
) {
    fun corsHostOrDefault(): String {
        return if (corsHost != null) {
            val clean = apiHost.removePrefix("https://").removePrefix("http://")
            "${corsHost}/https://${clean}"
        } else apiHost
    }
}

data class VideoHubPlaylistResponse(
    val success: Boolean = false,
    val isSerial: Boolean = false,
    val items: List<VideoHubItem>? = null,
    val error: String? = null
)

data class VideoHubItem(
    val id: String,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val quality: String? = null,
    val hlsUrl: String? = null,
    val dashUrl: String? = null,
    val voiceStudio: String? = null
)
