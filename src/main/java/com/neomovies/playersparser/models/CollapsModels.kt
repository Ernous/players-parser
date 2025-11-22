package com.neomovies.playersparser.models

/**
 * Модели для Collaps парсера
 */

data class CollapsSettings(
    val apiHost: String = "https://api.bhcesh.me",
    val token: String = "eedefb541aeba871dcfc756e6b31c02e",  // Токен из Lampac
    val useDash: Boolean = false,
    val corsHost: String? = "https://cors.apn.monster",
    val two: Boolean = true,  // Режим two по умолчанию включен
    val reserve: Boolean = false,
    val vast: Boolean = false,
    val proxies: List<String> = emptyList()
) {
    fun corsHostOrDefault(): String {
        return if (corsHost != null) {
            val clean = apiHost.removePrefix("https://").removePrefix("http://")
            "${corsHost}/https://${clean}"
        } else apiHost
    }
}

data class CollapsListResponse(
    val results: List<CollapsResult>? = null,
    val error: String? = null
)

data class CollapsResult(
    val id: String,
    val name: String,
    val type: String, // "movie" или "series"
    val year: Int? = null,
    val poster: String? = null,
    val iframeUrl: String? = null
)

data class CollapsEmbedResponse(
    val url: String? = null,
    val dashUrl: String? = null,
    val hlsUrl: String? = null,
    val error: String? = null
)

data class CollapsSearchResult(
    val id: String,
    val name: String,
    val type: String,
    val year: Int? = null,
    val poster: String? = null
)
