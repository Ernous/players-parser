package com.neomovies.playersparser.models

// Ответ парсера при поиске плеера
data class PlayerResponse(
    val success: Boolean,
    val url: String? = null,
    val playlist: List<PlaylistItem>? = null,
    val error: String? = null
)

data class PlaylistItem(
    val url: String,
    val type: String,    // "hls", "dash", "video"
    val quality: String, // "1080p", "Auto", etc.
    // Добавляем заголовки, они нужны для Collaps (Referer)
    val headers: Map<String, String> = emptyMap()
)

// Ответ парсера при поиске (search)
data class SearchResponse(
    val results: List<Any>, // Тут можно заменить Any на конкретную модель SearchItem, если она есть
    val error: String? = null
)