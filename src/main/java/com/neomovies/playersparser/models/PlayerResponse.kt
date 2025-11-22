package com.neomovies.playersparser.models

/**
 * Базовый класс для ответа плеера
 */
data class PlayerResponse(
    val url: String? = null,
    val urls: List<String>? = null,
    val playlist: List<PlaylistItem>? = null,
    val error: String? = null,
    val success: Boolean = true
)

/**
 * Элемент плейлиста (для DASH/HLS)
 */
data class PlaylistItem(
    val url: String,
    val quality: String? = null,
    val type: String? = null // "video", "audio", "subtitle"
)

/**
 * Результат поиска
 */
data class SearchResult(
    val id: String,
    val name: String,
    val type: String, // "movie", "series"
    val year: Int? = null,
    val poster: String? = null
)

/**
 * Ответ поиска
 */
data class SearchResponse(
    val results: List<SearchResult>,
    val total: Int = 0,
    val error: String? = null
)
