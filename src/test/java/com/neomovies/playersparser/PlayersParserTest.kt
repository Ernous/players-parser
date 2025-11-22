package com.neomovies.playersparser

import com.neomovies.playersparser.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Интеграционные тесты для парсеров видео плееров
 * Тестирует парсинг фильма и сериала со всех доступных источников
 */
class PlayersParserTest {
    private lateinit var manager: PlayersParserManager

    @Before
    fun setUp() {
        manager = PlayersParserManager()
    }

    /**
     * Тест парсинга фильма "Inception" (2010) со всех плееров
     * IMDB ID: tt1375666
     */
    @Test
    fun testMovieParsing() = runBlocking {
        System.out.println("\n=== Testing Movie Parsing ===")
        System.out.println("Movie: Inception (2010)")
        System.out.println("IMDB ID: tt1375666\n")

        val sources = listOf("rezka", "collaps", "videohub")
        val movieId = "tt1375666"

        for (source in sources) {
            System.out.println("Testing $source...")
            try {
                val result = manager.getPlayer(
                    source = source,
                    id = movieId,
                    type = "movie"
                )

                if (result.success && (result.url != null || (result.playlist != null && result.playlist.isNotEmpty()))) {
                    System.out.println("✓ $source: SUCCESS")
                    System.out.println("  ========================================")
                    if (result.url != null) {
                        System.out.println("  Основной URL: ${result.url}")
                        System.out.println("  Тип потока: ${detectStreamType(result.url)}")
                        System.out.println("  Длина URL: ${result.url.length} символов")
                    }
                    if (result.playlist != null && result.playlist.isNotEmpty()) {
                        System.out.println("  Плейлист содержит ${result.playlist.size} элементов:")
                        result.playlist.forEachIndexed { index, item ->
                            System.out.println("    [${index + 1}] Качество/Озвучка: ${item.quality ?: "Не указано"}")
                            System.out.println("        Тип: ${item.type ?: "Не указано"}")
                            System.out.println("        URL: ${item.url}")
                            System.out.println("        Длина URL: ${item.url.length} символов")
                            if (item.url.contains("http")) {
                                try {
                                    val urlObj = java.net.URI(item.url).toURL()
                                    System.out.println("        Домен: ${urlObj.host}")
                                    System.out.println("        Протокол: ${urlObj.protocol}")
                                } catch (e: Exception) {
                                    System.out.println("        (Не удалось распарсить URL)")
                                }
                            }
                            System.out.println()
                        }
                    } else {
                        System.out.println("  ⚠ ПРЕДУПРЕЖДЕНИЕ: Плейлист пуст или не предоставлен")
                    }
                    System.out.println("  ========================================")
                } else {
                    System.out.println("✗ $source: FAILED")
                    System.out.println("  Ошибка: ${result.error ?: "Неизвестная ошибка"}")
                    if (result.url == null && (result.playlist == null || result.playlist.isEmpty())) {
                        System.out.println("  Детали: URL и плейлист отсутствуют")
                    }
                    System.out.println("  ========================================")
                }
            } catch (e: Exception) {
                System.out.println("✗ $source: ERROR - ${e.message}")
            }
            System.out.println()
        }
    }

    /**
     * Тест парсинга сериала "Breaking Bad" со всех плееров
     * IMDB ID: tt0903747
     * Тестирует разные озвучки (voiceStudio)
     */
    @Test
    fun testSeriesParsing() = runBlocking {
        System.out.println("\n=== Testing Series Parsing ===")
        System.out.println("Series: Breaking Bad")
        System.out.println("IMDB ID: tt0903747\n")

        val sources = listOf("rezka", "collaps", "videohub")
        val seriesId = "tt0903747"
        val season = 1
        val episode = 1

        for (source in sources) {
            System.out.println("Testing $source for S${season}E${episode}...")
            try {
                val result = manager.getPlayer(
                    source = source,
                    id = seriesId,
                    type = "series",
                    season = season,
                    episode = episode
                )

                if (result.success) {
                    System.out.println("✓ $source: SUCCESS")
                    System.out.println("  ========================================")
                    System.out.println("  Сериал: Breaking Bad")
                    System.out.println("  Сезон: $season, Эпизод: $episode")
                    if (result.url != null) {
                        System.out.println("  Основной URL: ${result.url}")
                        System.out.println("  Тип потока: ${detectStreamType(result.url)}")
                        System.out.println("  Длина URL: ${result.url.length} символов")
                    }
                    if (result.playlist != null && result.playlist.isNotEmpty()) {
                        System.out.println("  Плейлист содержит ${result.playlist.size} элементов:")
                        result.playlist.forEachIndexed { index, item ->
                            System.out.println("    [${index + 1}] Озвучка/Качество: ${item.quality ?: "Не указано"}")
                            System.out.println("        Тип: ${item.type ?: "Не указано"}")
                            System.out.println("        URL: ${item.url}")
                            System.out.println("        Длина URL: ${item.url.length} символов")
                            if (item.url.contains("http")) {
                                try {
                                    val urlObj = java.net.URI(item.url).toURL()
                                    System.out.println("        Домен: ${urlObj.host}")
                                    System.out.println("        Протокол: ${urlObj.protocol}")
                                } catch (e: Exception) {
                                    System.out.println("        (Не удалось распарсить URL)")
                                }
                            }
                            System.out.println()
                        }
                    } else {
                        System.out.println("  Плейлист: пуст или не предоставлен")
                    }
                    System.out.println("  ========================================")
                } else {
                    System.out.println("✗ $source: FAILED")
                    System.out.println("  Ошибка: ${result.error ?: "Неизвестная ошибка"}")
                    System.out.println("  ========================================")
                }
            } catch (e: Exception) {
                System.out.println("✗ $source: ERROR - ${e.message}")
            }
            System.out.println()
        }
    }

    /**
     * Тест получения информации о сериале (сезоны и эпизоды)
     */
    @Test
    fun testSeriesInfo() = runBlocking {
        System.out.println("\n=== Testing Series Info ===")
        System.out.println("Series: Breaking Bad (tt0903747)\n")

        val sources = listOf("rezka", "collaps", "videohub")
        val seriesId = "tt0903747"

        for (source in sources) {
            System.out.println("Getting series info from $source...")
            try {
                val info = manager.getSeriesInfo(source, seriesId)
                if (info != null) {
                    System.out.println("✓ $source: SUCCESS")
                    System.out.println("  ========================================")
                    System.out.println("  Сериал: Breaking Bad (tt0903747)")
                    System.out.println("  Всего сезонов: ${info.size}")
                    System.out.println("  Сезоны: ${info.keys.sorted().joinToString(", ")}")
                    System.out.println()
                    info.entries.sortedBy { it.key }.take(5).forEach { (season, episodes) ->
                        System.out.println("    Сезон $season: ${episodes.size} эпизодов")
                        System.out.println("      Эпизоды: ${episodes.sorted().joinToString(", ")}")
                        System.out.println()
                    }
                    if (info.size > 5) {
                        System.out.println("    ... и еще ${info.size - 5} сезонов")
                    }
                    System.out.println("  ========================================")
                } else {
                    System.out.println("✗ $source: Информация о сериале недоступна")
                }
            } catch (e: Exception) {
                System.out.println("✗ $source: ERROR - ${e.message}")
            }
            System.out.println()
        }
    }

    /**
     * Тест поиска контента
     */
    @Test
    fun testSearch() = runBlocking {
        System.out.println("\n=== Testing Search ===")
        System.out.println("Query: Inception\n")

        val sources = listOf("rezka", "collaps", "videohub")
        val query = "Inception"

        for (source in sources) {
            System.out.println("Searching in $source...")
            try {
                val response = manager.search(source, query)
                if (response.results.isNotEmpty()) {
                    System.out.println("✓ $source: Найдено результатов: ${response.results.size}")
                    System.out.println("  ========================================")
                    response.results.take(5).forEachIndexed { index, result ->
                        System.out.println("    [${index + 1}] Название: ${result.name}")
                        System.out.println("        Год: ${result.year ?: "Не указан"}")
                        System.out.println("        Тип: ${result.type}")
                        System.out.println("        ID: ${result.id}")
                        if (result.poster != null && result.poster.isNotEmpty()) {
                            System.out.println("        Постер: ${result.poster}")
                        }
                        System.out.println()
                    }
                    if (response.results.size > 5) {
                        System.out.println("    ... и еще ${response.results.size - 5} результатов")
                    }
                    System.out.println("  ========================================")
                } else {
                    System.out.println("✗ $source: Результаты не найдены")
                    if (response.error != null) {
                        System.out.println("  Ошибка: ${response.error}")
                    }
                }
            } catch (e: Exception) {
                System.out.println("✗ $source: ERROR - ${e.message}")
            }
            System.out.println()
        }
    }

    /**
     * Тест fallback стратегии - попытка получить плеер из нескольких источников
     */
    @Test
    fun testFallbackStrategy() = runBlocking {
        System.out.println("\n=== Testing Fallback Strategy ===")
        System.out.println("Movie: Inception (tt1375666)")
        System.out.println("Sources: rezka -> collaps -> videohub\n")

        val result = manager.getPlayerFromMultipleSources(
            sources = listOf("rezka", "collaps", "videohub"),
            id = "tt1375666",
            type = "movie"
        )

            if (result.success) {
                System.out.println("✓ SUCCESS: Получен плеер из одного из источников")
                System.out.println("  ========================================")
                if (result.url != null) {
                    System.out.println("  Основной URL: ${result.url}")
                    System.out.println("  Тип потока: ${detectStreamType(result.url)}")
                    System.out.println("  Длина URL: ${result.url.length} символов")
                    if (result.url.contains("http")) {
                        try {
                            val urlObj = java.net.URI(result.url).toURL()
                            System.out.println("  Домен: ${urlObj.host}")
                            System.out.println("  Протокол: ${urlObj.protocol}")
                        } catch (e: Exception) {
                            System.out.println("  (Не удалось распарсить URL)")
                        }
                    }
                }
                if (result.playlist != null && result.playlist.isNotEmpty()) {
                    System.out.println("  Плейлист содержит ${result.playlist.size} элементов:")
                    result.playlist.forEachIndexed { index, item ->
                        System.out.println("    [${index + 1}] Качество: ${item.quality ?: "Не указано"}")
                        System.out.println("        Тип: ${item.type ?: "Не указано"}")
                        System.out.println("        URL: ${item.url}")
                        System.out.println("        Длина URL: ${item.url.length} символов")
                    }
                } else {
                    System.out.println("  Плейлист: пуст или не предоставлен")
                }
                System.out.println("  ========================================")
            } else {
                System.out.println("✗ FAILED")
                System.out.println("  Ошибка: ${result.error ?: "Неизвестная ошибка"}")
                System.out.println("  ========================================")
            }
    }

    /**
     * Тест парсинга с разными озвучками для VideoHUB (voiceStudio)
     */
    @Test
    fun testVideoHubVoiceStudios() = runBlocking {
        System.out.println("\n=== Testing VideoHUB Voice Studios ===")
        System.out.println("Series: Breaking Bad (tt0903747)")
        System.out.println("Season 1, Episode 1\n")

        try {
            val result = manager.getPlayer(
                source = "videohub",
                id = "tt0903747",
                type = "series",
                season = 1,
                episode = 1
            )

            if (result.success) {
                System.out.println("✓ VideoHUB: SUCCESS")
                System.out.println("  ========================================")
                System.out.println("  Сериал: Breaking Bad")
                System.out.println("  Сезон: 1, Эпизод: 1")
                if (result.url != null) {
                    System.out.println("  Основной URL: ${result.url}")
                    System.out.println("  Тип потока: ${detectStreamType(result.url)}")
                    System.out.println("  Длина URL: ${result.url.length} символов")
                }
                if (result.playlist != null && result.playlist.isNotEmpty()) {
                    System.out.println("  Доступные озвучки/качества (${result.playlist.size}):")
                    result.playlist.forEachIndexed { index, item ->
                        System.out.println("    [${index + 1}] Озвучка: ${item.quality ?: "Не указано"}")
                        System.out.println("        Тип: ${item.type ?: "Не указано"}")
                        System.out.println("        URL: ${item.url}")
                        System.out.println("        Длина URL: ${item.url.length} символов")
                        if (item.url.contains("http")) {
                            try {
                                val urlObj = java.net.URI(item.url).toURL()
                                System.out.println("        Домен: ${urlObj.host}")
                                System.out.println("        Протокол: ${urlObj.protocol}")
                            } catch (e: Exception) {
                                System.out.println("        (Не удалось распарсить URL)")
                            }
                        }
                        System.out.println()
                    }
                } else {
                    System.out.println("  Плейлист: пуст или не предоставлен")
                }
                System.out.println("  ========================================")
            } else {
                System.out.println("✗ VideoHUB: FAILED")
                System.out.println("  Ошибка: ${result.error ?: "Неизвестная ошибка"}")
                System.out.println("  ========================================")
            }
        } catch (e: Exception) {
            System.out.println("✗ VideoHUB: ERROR - ${e.message}")
        }
    }

    /**
     * Тест парсинга с режимом DASH для Collaps
     */
    @Test
    fun testCollapsDashMode() = runBlocking {
        System.out.println("\n=== Testing Collaps DASH Mode ===")
        System.out.println("Movie: Inception (tt1375666)\n")

        try {
            // Создаем парсер с режимом DASH
            val collapsSettings = CollapsSettings(
                useDash = true,
                two = true
            )
            manager.registerCollaps(collapsSettings, isDash = true)

            val result = manager.getPlayer(
                source = "collaps_dash",
                id = "tt1375666",
                type = "movie"
            )

            if (result.success) {
                System.out.println("✓ Collaps DASH: SUCCESS")
                System.out.println("  ========================================")
                System.out.println("  Фильм: Inception (2010)")
                if (result.url != null) {
                    System.out.println("  Основной URL: ${result.url}")
                    System.out.println("  Тип потока: ${detectStreamType(result.url)}")
                    System.out.println("  Длина URL: ${result.url.length} символов")
                    if (result.url.contains("http")) {
                        try {
                            val urlObj = java.net.URI(result.url).toURL()
                            System.out.println("  Домен: ${urlObj.host}")
                            System.out.println("  Протокол: ${urlObj.protocol}")
                        } catch (e: Exception) {
                            System.out.println("  (Не удалось распарсить URL)")
                        }
                    }
                }
                if (result.playlist != null && result.playlist.isNotEmpty()) {
                    System.out.println("  Плейлист содержит ${result.playlist.size} элементов:")
                    result.playlist.forEachIndexed { index, item ->
                        System.out.println("    [${index + 1}] Тип: ${item.type ?: "Не указано"}")
                        System.out.println("        Качество/Озвучка: ${item.quality ?: "Не указано"}")
                        System.out.println("        URL: ${item.url}")
                        System.out.println("        Длина URL: ${item.url.length} символов")
                        System.out.println()
                    }
                } else {
                    System.out.println("  Плейлист: пуст или не предоставлен")
                }
                System.out.println("  ========================================")
            } else {
                System.out.println("✗ Collaps DASH: FAILED")
                System.out.println("  Ошибка: ${result.error ?: "Неизвестная ошибка"}")
                System.out.println("  ========================================")
            }
        } catch (e: Exception) {
            System.out.println("✗ Collaps DASH: ERROR - ${e.message}")
        }
    }

    /**
     * Тест получения доступных парсеров
     */
    @Test
    fun testAvailableParsers() {
        System.out.println("\n=== Available Parsers ===\n")
        val parsers = manager.getAvailableParsers()
        System.out.println("Registered parsers: ${parsers.size}")
        parsers.forEach { parser ->
            System.out.println("  - $parser")
        }
    }

    /**
     * Helper функция для определения типа потока по URL
     */
    private fun detectStreamType(url: String): String {
        return when {
            url.contains(".m3u8") -> "HLS (M3U8)"
            url.contains(".mpd") -> "DASH (MPD)"
            url.contains(".mp4") -> "MP4"
            url.contains("hlsUrl") || url.contains("hls") -> "HLS"
            url.contains("dashUrl") || url.contains("dash") -> "DASH"
            else -> "Unknown"
        }
    }
}
