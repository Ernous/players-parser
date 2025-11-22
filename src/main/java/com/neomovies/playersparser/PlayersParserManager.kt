package com.neomovies.playersparser

import com.neomovies.playersparser.models.*
import com.neomovies.playersparser.parsers.BaseParser
import com.neomovies.playersparser.parsers.CollapsParser
import com.neomovies.playersparser.parsers.RezkaParser
import com.neomovies.playersparser.parsers.VideoHubParser
import okhttp3.OkHttpClient

/**
 * Менеджер для управления всеми парсерами плееров
 * Предоставляет единый интерфейс для работы с разными источниками
 */
class PlayersParserManager(
    private val client: OkHttpClient? = null
) {
    private val parsers = mutableMapOf<String, BaseParser>()

    init {
        // Инициализируем парсеры с настройками по умолчанию
        registerParser("rezka", RezkaParser(client = client))
        registerParser("collaps", CollapsParser(settings = CollapsSettings(useDash = false), client = client))
        registerParser("collaps_dash", CollapsParser(settings = CollapsSettings(useDash = true), client = client))
        registerParser("videohub", VideoHubParser(client = client))
    }

    /**
     * Зарегистрировать парсер Rezka с кастомными настройками
     */
    fun registerRezka(settings: RezkaSettings) {
        registerParser("rezka", RezkaParser(settings = settings))
    }

    /**
     * Зарегистрировать парсер Collaps с кастомными настройками
     */
    fun registerCollaps(settings: CollapsSettings, isDash: Boolean = false) {
        val key = if (isDash) "collaps_dash" else "collaps"
        registerParser(key, CollapsParser(settings = settings))
    }

    /**
     * Зарегистрировать парсер VideoHUB с кастомными настройками
     */
    fun registerVideoHub(settings: VideoHubSettings) {
        registerParser("videohub", VideoHubParser(settings = settings))
    }

    /**
     * Зарегистрировать новый парсер
     */
    fun registerParser(name: String, parser: BaseParser) {
        parsers[name.lowercase()] = parser
    }

    /**
     * Получить парсер по имени
     */
    fun getParser(name: String): BaseParser? {
        return parsers[name.lowercase()]
    }

    /**
     * Получить плеер из конкретного источника
     */
    suspend fun getPlayer(
        source: String,
        id: String,
        type: String = "movie",
        season: Int? = null,
        episode: Int? = null
    ): PlayerResponse {
        val parser = getParser(source)
            ?: return PlayerResponse(error = "Parser '$source' not found")

        return parser.getPlayer(id, type, season, episode)
    }

    /**
     * Получить плеер из первого доступного источника
     * Пытается несколько источников по порядку
     */
    suspend fun getPlayerFromMultipleSources(
        sources: List<String>,
        id: String,
        type: String = "movie",
        season: Int? = null,
        episode: Int? = null
    ): PlayerResponse {
        for (source in sources) {
            val result = getPlayer(source, id, type, season, episode)
            if (result.success && (result.url != null || result.playlist != null)) {
                return result
            }
        }

        return PlayerResponse(error = "No working sources found")
    }

    /**
     * Поиск контента в конкретном источнике
     */
    suspend fun search(
        source: String,
        query: String
    ): SearchResponse {
        val parser = getParser(source)
            ?: return SearchResponse(
                results = emptyList(),
                error = "Parser '$source' not found"
            )

        return parser.search(query)
    }

    /**
     * Поиск контента во всех источниках
     */
    suspend fun searchInAllSources(query: String): Map<String, SearchResponse> {
        val results = mutableMapOf<String, SearchResponse>()

        for ((name, parser) in parsers) {
            try {
                results[name] = parser.search(query)
            } catch (e: Exception) {
                results[name] = SearchResponse(
                    results = emptyList(),
                    error = e.message ?: "Unknown error"
                )
            }
        }

        return results
    }

    /**
     * Получить список доступных парсеров
     */
    fun getAvailableParsers(): List<String> {
        return parsers.keys.toList()
    }

    /**
     * Получить информацию о сериале (сезоны и эпизоды)
     */
    suspend fun getSeriesInfo(
        source: String,
        id: String
    ): Map<Int, List<Int>>? {
        val parser = getParser(source) ?: return null

        return when (parser) {
            is RezkaParser -> parser.getSeriesInfo(id)
            is CollapsParser -> parser.getSeriesInfo(id)
            is VideoHubParser -> parser.getSeriesInfo(id)
            else -> null
        }
    }
}
