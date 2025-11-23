package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.models.CollapsSearchItem
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class CollapsParserTest {

    private val parser = CollapsParser()

    @Test
    fun testGetSeriesInfoAndPlayer() = runBlocking {
        // "Игра престолов" (KP: 464963)
        val kpId = "464963"

        println("Fetching series info for KP: $kpId...")
        val seriesInfo = parser.getSeriesInfo(kpId)

        assertNotNull("Series info should not be null", seriesInfo)
        assertTrue("Series should have seasons", seriesInfo!!.isNotEmpty())

        println("\n=== SERIES INFO ($kpId) ===")
        seriesInfo.forEach { (season, episodes) ->
            println("Season $season: ${episodes.size} episodes")
        }

        // Берем 1 сезон 1 серию
        val response = parser.getPlayer(kpId, "series", 1, 1)

        println("\n=== SERIES PLAYER (S1E1) ===")
        println("Success: ${response.success}")
        println("Main URL: ${response.url}")

        if (response.playlist != null) {
            response.playlist!!.forEach {
                println("- [${it.type}] ${it.url}")
            }
        }

        assertTrue("Player response should be successful", response.success)
        assertNotNull("URL should not be null", response.url)
    }

    @Test
    fun testSearch() = runBlocking {
        val query = "Рик и Морти"
        val response = parser.search(query)
        val items = response.results.filterIsInstance<CollapsSearchItem>()

        println("\n=== SEARCH RESULTS ($query) ===")
        items.forEach {
            println("${it.name} (${it.year}) [${it.type}] - KP: ${it.kinopoiskId}")
        }
        assertTrue(items.isNotEmpty())
    }
}