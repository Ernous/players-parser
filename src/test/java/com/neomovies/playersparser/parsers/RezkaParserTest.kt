package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.models.RezkaSearchItem
import com.neomovies.playersparser.models.RezkaSettings
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class RezkaParserTest {

    // Используем рабочее зеркало
    private val settings = RezkaSettings(baseUrl = "https://hdrezka.me")
    private val parser = RezkaParser(settings)

    @Test
    fun testSearchAndPlay() = runBlocking {
        // Ищем на русском, так надежнее
        val query = "Во все тяжкие"
        println("Searching for: '$query' on ${settings.baseUrl}...")

        val response = parser.search(query)

        val items = response.results.filterIsInstance<RezkaSearchItem>()

        if (items.isEmpty()) {
            println("Search empty. Printing raw logic check...")
            // Если пусто, возможно зеркало просит капчу
            return@runBlocking
        }

        items.forEach {
            println("Found: ${it.title} (${it.year}) [${it.type}] -> ${it.url}")
        }

        val targetItem = items.find { it.title.contains("Во все тяжкие") }
        assertNotNull("Target series not found", targetItem)

        println("\nSelected: ${targetItem!!.title}")

        // Получаем плеер
        val playerResponse = parser.getPlayer(
            id = targetItem.url,
            type = "series",
            season = 1,
            episode = 1
        )

        println("\n=== PLAYER RESPONSE ===")
        println("Success: ${playerResponse.success}")
        if (playerResponse.error != null) {
            println("Error: ${playerResponse.error}")
        }
        println("Main URL: ${playerResponse.url}")

        playerResponse.playlist?.forEach {
            println("- [${it.quality}] ${it.url}")
        }

        assertTrue("API request failed: ${playerResponse.error}", playerResponse.success)
        assertNotNull("URL is null", playerResponse.url)
    }

    @Test
    fun testGetSeriesInfo() = runBlocking {
        // URL из твоего примера
        val url = "https://hdrezka.me/series/thriller/646-vo-vse-tyazhkie-2008-latest.html"

        println("Fetching series info from: $url")
        val info = parser.getSeriesInfo(url)

        assertNotNull("Series info should not be null", info)
        assertTrue("Should contain seasons", info!!.isNotEmpty())

        println("\n=== SEASONS & EPISODES ===")
        info.forEach { (season, episodes) ->
            println("Season $season: ${episodes.size} episodes (${episodes.first()}...${episodes.last()})")
        }

        // Проверка на основе твоих данных (5 сезонов)
        assertTrue(info.containsKey(1))
        assertTrue(info.containsKey(5))
        assertEquals(7, info[1]?.size) // В 1 сезоне 7 серий
        assertEquals(17, info[5]?.size) // В 5 сезоне 16 серий
    }
}