package com.neomovies.playersparser

import com.neomovies.playersparser.models.*
import kotlinx.coroutines.runBlocking

/**
 * Пример использования PlayersParserManager
 */
object Example {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        // Инициализируем менеджер парсеров
        val manager = PlayersParserManager()

        // Пример 1: Поиск контента на Rezka
        println("=== Rezka Search ===")
        val rezkaResults = manager.search("rezka", "Inception")
        rezkaResults.results.forEach { result ->
            println("${result.name} (${result.year}) - ${result.type}")
        }

        // Пример 2: Получить плеер из конкретного источника
        println("\n=== Get Player from Rezka ===")
        val rezkaPlayer = manager.getPlayer("rezka", "inception-2010", "movie")
        if (rezkaPlayer.success) {
            println("URL: ${rezkaPlayer.url}")
        } else {
            println("Error: ${rezkaPlayer.error}")
        }

        // Пример 3: Поиск во всех источниках
        println("\n=== Search in All Sources ===")
        val allResults = manager.searchInAllSources("Breaking Bad")
        allResults.forEach { (source, response) ->
            println("$source: ${response.results.size} results")
        }

        // Пример 4: Получить плеер из первого доступного источника
        println("\n=== Get Player from Multiple Sources ===")
        val player = manager.getPlayerFromMultipleSources(
            sources = listOf("rezka", "collaps", "videohub"),
            id = "tt0903747",
            type = "series",
            season = 1,
            episode = 1
        )
        if (player.success) {
            println("Found on one of the sources!")
            println("URL: ${player.url}")
        } else {
            println("Error: ${player.error}")
        }

        // Пример 5: Получить информацию о сериале
        println("\n=== Get Series Info ===")
        val seriesInfo = manager.getSeriesInfo("rezka", "breaking-bad")
        seriesInfo?.forEach { (season, episodes) ->
            println("Season $season: ${episodes.size} episodes")
        }

        // Пример 6: Кастомные настройки для Collaps
        println("\n=== Custom Collaps Settings ===")
        val collapsSettings = CollapsSettings(
            apiHost = "https://api.namy.ws",
            token = "YOUR_TOKEN_HERE",
            useDash = false
        )
        manager.registerCollaps(collapsSettings)
        val collapsPlayer = manager.getPlayer("collaps", "tt1305826", "series", 1, 1)
        println("Collaps player: ${collapsPlayer.url ?: collapsPlayer.error}")

        // Пример 7: Список доступных парсеров
        println("\n=== Available Parsers ===")
        manager.getAvailableParsers().forEach { parser ->
            println("- $parser")
        }
    }
}
