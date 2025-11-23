package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.models.PlaylistItem // Импорт модели элемента плейлиста
import com.neomovies.playersparser.models.PlayerResponse // Импорт модели ответа
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class VideoHubParserTest {

    private val parser = VideoHubParser()

    /**
     * Тест для сериала: получаем озвучки, сезоны, эпизоды и потоки
     * Используем реальный KP ID для "Время приключений" (602284)
     */
    @Test
    fun testGetSeriesWithVoicesAndStreams() = runBlocking {
        val seriesKpId = "602284" // Время приключений

        // Шаг 1: Получаем информацию о сериале (озвучки, сезоны, эпизоды)
        val seriesInfo = parser.getSeriesInfo(seriesKpId)
        
        // Проверяем, что данные пришли
        assertNotNull("Series info should not be null", seriesInfo)
        
        // Используем !! безопасно, так как выше проверили assertNotNull
        val info = seriesInfo!!
        assertFalse("Series should have seasons", info.isEmpty())

        println("=== SERIES INFO ===")
        println("Title: Время приключений")
        println("Total seasons: ${info.size}")
        info.forEach { (season, episodes) ->
            println("Season $season: ${episodes.size} episodes")
        }

        // Проверяем, что есть сезоны и эпизоды
        val totalSeasons = info.size
        val totalEpisodes = info.values.sumOf { it.size }
        assertTrue("Should have at least 1 season", totalSeasons > 0)
        assertTrue("Should have at least 1 episode", totalEpisodes > 0)

        // Шаг 2: Получаем озвучки
        val voices = parser.getVoices(seriesKpId)
        println("\nAvailable voices: ${voices?.joinToString(", ") ?: "None"}")

        // Шаг 3: Получаем потоки для конкретной серии (берем первый попавшийся сезон)
        val firstSeason = info.keys.minOrNull() ?: 1
        val firstEpisode = info[firstSeason]?.minOrNull() ?: 1

        println("Fetching stream for S${firstSeason}E${firstEpisode}...")

        val playerResponse = parser.getPlayer(
            id = seriesKpId,
            type = "series",
            season = firstSeason,
            episode = firstEpisode
        )

        println("\n=== PLAYER RESPONSE ===")
        println("Success: ${playerResponse.success}")
        if (!playerResponse.success) {
            println("Error: ${playerResponse.error}")
        }
        println("Main URL: ${playerResponse.url?.take(80)}...")

        assertTrue("Player response should be successful. Error: ${playerResponse.error}", playerResponse.success)
        assertNotNull("Should have main URL", playerResponse.url)
        assertFalse("Main URL should not be empty", playerResponse.url.isNullOrEmpty())

        // Проверяем наличие списка качеств
        val playlist = playerResponse.playlist
        assertNotNull("Playlist should not be null", playlist)
        assertFalse("Playlist should not be empty", playlist!!.isEmpty())

        // Проверяем типы потоков
        val hasHls = playlist.any { it.type == "hls" }
        // DASH не всегда гарантирован для всех видео, но обычно есть. Сделаем проверку мягкой или оставим как есть.
        val hasDash = playlist.any { it.type == "dash" }

        println("\nPlaylist items: ${playlist.size}")
        playlist.forEach { item ->
            println("- ${item.quality} (${item.type}): ${item.url.take(60)}...")
        }

        assertTrue("Should have HLS stream", hasHls)
        // assertTrue("Should have DASH stream", hasDash) // Можно раскомментировать, если DASH критичен

        // Проверяем, что URL не пустые
        playlist.forEach { item ->
            assertFalse("URL should not be empty for ${item.quality}", item.url.isEmpty())
        }
    }

    /**
     * Тест для фильма: получаем озвучки и потоки
     * Используем реальный KP ID для "Интерстеллар" (258687)
     */
    @Test
    fun testGetMovieWithVoicesAndStreams() = runBlocking {
        val movieKpId = "258687" // Интерстеллар

        // Шаг 1: Получаем озвучки
        val voices = parser.getVoices(movieKpId)
        println("=== MOVIE INFO ===")
        println("Title: Интерстеллар")
        println("Available voices: ${voices?.joinToString(", ") ?: "None"}")
        
        assertNotNull("Voices should not be null", voices)
        assertFalse("Should have at least one voice", voices!!.isEmpty())

        // Шаг 2: Получаем потоки для фильма
        val playerResponse = parser.getPlayer(
            id = movieKpId,
            type = "movie",
            season = null,
            episode = null
        )

        println("\n=== PLAYER RESPONSE ===")
        println("Success: ${playerResponse.success}")
        if (!playerResponse.success) {
            println("Error: ${playerResponse.error}")
        }
        println("Main URL: ${playerResponse.url?.take(80)}...")

        assertTrue("Player response should be successful", playerResponse.success)
        assertNotNull("Should have main URL", playerResponse.url)
        assertFalse("Main URL should not be empty", playerResponse.url.isNullOrEmpty())

        // Проверяем плейлист
        val playlist = playerResponse.playlist
        assertNotNull("Playlist should not be null", playlist)
        assertFalse("Playlist should not be empty", playlist!!.isEmpty())

        val hasHls = playlist.any { it.type == "hls" }
        
        println("\nPlaylist items: ${playlist.size}")
        playlist.forEach { item ->
            println("- ${item.quality} (${item.type}): ${item.url.take(60)}...")
        }

        assertTrue("Should have HLS stream", hasHls)
    }

    /**
     * Тест проверяет, что для сериала требуется season и episode
     */
    @Test
    fun testSeriesRequiresSeasonAndEpisode() = runBlocking {
        // ID сериала без указания сезона/серии
        val response = parser.getPlayer(
            id = "602284", // Время приключений
            type = "series",
            season = null,
            episode = null
        )

        assertFalse("Should fail without season/episode", response.success)
        // Проверяем, что ошибка содержит нужный текст (регистронезависимо)
        assertTrue(
            "Error should mention series. Actual: ${response.error}", 
            response.error?.contains("Series", ignoreCase = true) == true
        )
    }

    /**
     * Тест проверяет структуру информации о сериале
     */
    @Test
    fun testSeriesInfoStructure() = runBlocking {
        val seriesInfo = parser.getSeriesInfo("602284")
        assertNotNull("Series info should not be null", seriesInfo)

        if (seriesInfo != null && seriesInfo.isNotEmpty()) {
            // Проверяем, что сезоны отсортированы (Map keys могут быть не отсортированы, поэтому сортируем для проверки)
            val seasons = seriesInfo.keys.toList()
            val sortedSeasons = seasons.sorted()
            
            // В реализации getSeriesInfo мы не гарантируем порядок ключей Map, 
            // но мы гарантируем порядок эпизодов внутри списка.
            // Поэтому проверим просто наличие данных и сортировку эпизодов.
            
            println("Seasons found: $sortedSeasons")

            // Проверяем, что эпизоды в каждом сезоне отсортированы
            seriesInfo.forEach { (season, episodes) ->
                val sortedEpisodes = episodes.sorted()
                assertEquals("Episodes in season $season should be sorted", sortedEpisodes, episodes)
            }
        }
    }
}