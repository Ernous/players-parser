package com.neomovies.playersparser

import com.neomovies.playersparser.models.*
import com.neomovies.playersparser.parsers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Главный менеджер для управления всеми парсерами.
 * Предоставляет единую точку входа для поиска и получения ссылок.
 */
class PlayersParserManager {

    // Экземпляры парсеров
    private var rezkaParser: RezkaParser = RezkaParser()
    private var collapsParser: CollapsParser = CollapsParser()
    private var videoHubParser: VideoHubParser = VideoHubParser()

    // --- Конфигурация ---

    /**
     * Обновить настройки для HDRezka
     */
    fun registerRezka(settings: RezkaSettings) {
        this.rezkaParser = RezkaParser(settings)
    }

    /**
     * Обновить настройки для Collaps
     */
    fun registerCollaps(settings: CollapsSettings) {
        this.collapsParser = CollapsParser(settings)
    }

    /**
     * Обновить настройки для VideoHub
     */
    fun registerVideoHub(settings: VideoHubSettings) {
        this.videoHubParser = VideoHubParser(settings)
    }

    // --- Поиск ---

    /**
     * Поиск контента в конкретном источнике
     * @param source Название источника ("rezka", "collaps", "videohub")
     */
    suspend fun search(source: String, query: String): SearchResponse = withContext(Dispatchers.IO) {
        when (source.lowercase()) {
            "rezka" -> rezkaParser.search(query)
            "collaps" -> collapsParser.search(query)
            "videohub" -> videoHubParser.search(query)
            else -> SearchResponse(emptyList(), "Unknown source: $source")
        }
    }

    /**
     * Поиск сразу во всех источниках параллельно.
     * @return Map, где ключ - название источника, значение - результат поиска.
     */
    suspend fun searchInAllSources(query: String): Map<String, SearchResponse> = coroutineScope {
        // Запускаем запросы параллельно
        val rezkaDeferred = async(Dispatchers.IO) { "rezka" to rezkaParser.search(query) }
        val collapsDeferred = async(Dispatchers.IO) { "collaps" to collapsParser.search(query) }
        val videoHubDeferred = async(Dispatchers.IO) { "videohub" to videoHubParser.search(query) }

        // Собираем результаты
        mapOf(
            rezkaDeferred.await(),
            collapsDeferred.await(),
            videoHubDeferred.await()
        )
    }

    // --- Получение плеера ---

    /**
     * Получить плеер из конкретного источника.
     * @param id ID контента (Для Rezka это URL, для Collaps - Kinopoisk ID)
     */
    suspend fun getPlayer(
        source: String,
        id: String,
        type: String = "movie",
        season: Int? = null,
        episode: Int? = null
    ): PlayerResponse = withContext(Dispatchers.IO) {
        when (source.lowercase()) {
            "rezka" -> rezkaParser.getPlayer(id, type, season, episode)
            "collaps" -> collapsParser.getPlayer(id, type, season, episode)
            "videohub" -> videoHubParser.getPlayer(id, type, season, episode)
            else -> PlayerResponse(success = false, error = "Unknown source: $source")
        }
    }

    /**
     * Пытается получить плеер, перебирая список источников по очереди.
     * Полезно, если один источник не работает или заблокирован.
     *
     * @param sources Список источников в порядке приоритета (например: ["collaps", "rezka"])
     * @param idMap Map с ID для разных источников. Например: {"collaps": "KP_ID", "rezka": "URL"}
     *              Если передать просто строку в 'defaultId', она будет использоваться для всех,
     *              но это сработает только если ID совместимы (например, KP ID для Collaps и VideoHub).
     */
    suspend fun getPlayerFromMultipleSources(
        sources: List<String>,
        defaultId: String,
        type: String = "movie",
        season: Int? = null,
        episode: Int? = null,
        specificIds: Map<String, String> = emptyMap() // Переопределение ID для конкретных источников
    ): PlayerResponse {
        var lastError: String? = null

        for (source in sources) {
            // Выбираем ID: либо специфичный для источника, либо общий
            val id = specificIds[source] ?: defaultId

            // Rezka требует URL, а не KP ID. Если передан KP ID в rezka, пропускаем, чтобы не получить ошибку 404,
            // если только пользователь явно не передал URL в specificIds.
            if (source == "rezka" && !id.contains("http") && !id.contains(".html")) {
                // Можно добавить логику авто-поиска здесь, но пока просто пропустим
                // или попробуем, вдруг ID это URL
            }

            val response = getPlayer(source, id, type, season, episode)
            if (response.success) {
                return response // Успех! Возвращаем результат
            } else {
                lastError = "$source error: ${response.error}"
            }
        }

        return PlayerResponse(success = false, error = lastError ?: "All sources failed")
    }

    // --- Информация о сериалах ---

    /**
     * Получить список сезонов и эпизодов (поддерживается Collaps и VideoHub, частично Rezka)
     */
    suspend fun getSeriesInfo(source: String, id: String): Map<Int, List<Int>>? = withContext(Dispatchers.IO) {
        when (source.lowercase()) {
            "collaps" -> collapsParser.getSeriesInfo(id)
            "videohub" -> videoHubParser.getSeriesInfo(id)
            "rezka" -> rezkaParser.getSeriesInfo(id)
            else -> null
        }
    }


    fun getRezkaParser() = rezkaParser
    fun getCollapsParser() = collapsParser
    fun getVideoHubParser() = videoHubParser
}