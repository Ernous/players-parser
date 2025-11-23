package com.neomovies.playersparser.models

import java.util.Base64
import java.nio.charset.StandardCharsets

data class RezkaSettings(
    val baseUrl: String = "https://hdrezka.me",
    val login: String? = null,
    val password: String? = null
)

data class RezkaSearchItem(
    val url: String,
    val title: String,
    val year: String?,
    val poster: String?,
    val type: String
)

object RezkaDecoder {
    private val TRASH_STRINGS = listOf(
        "IyMjI14hISMjIUBA",
        "QEBAQEAhIyMhXl5e",
        "JCQhIUAkJEBeIUAjJCRA",
        "JCQjISFAIyFAIyM=",
        "Xl5eIUAjIyEhIyM="
    )

    fun decode(data: String): String {
        if (!data.startsWith("#h")) return data
        try {
            var url = data.removePrefix("#h")
            for (i in 0 until 2) {
                url = url.replace("//_//", "")
                TRASH_STRINGS.forEach { trash ->
                    url = url.replace(trash, "")
                }
            }

            // Пытаемся декодировать результат очистки
            val decoded = decodeBase64Safe(url)
            if (decoded.contains("http")) return decoded

        } catch (e: Exception) {

        }
        try {
            val trashString = data.removePrefix("#h")
            if (trashString.contains("//_//")) {
                val parts = trashString.split("//_//")
                val sb = StringBuilder()
                for (part in parts) {
                    sb.append(decodeBase64Safe(part))
                }
                return sb.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return data
    }

    private fun decodeBase64Safe(input: String): String {
        return try {
            val decoder = Base64.getDecoder()
            val bytes = decoder.decode(input)
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun parseQualities(decodedUrl: String): List<PlaylistItem> {
        val result = mutableListOf<PlaylistItem>()
        val regex = "\\[([^\\]]+)\\](https?://[^\\s,]+)".toRegex()

        regex.findAll(decodedUrl).forEach { match ->
            val qualityLabel = match.groupValues[1]
            val streamUrl = match.groupValues[2]
            val type = if (streamUrl.contains(".m3u8")) "hls" else "video"
            result.add(PlaylistItem(streamUrl, type, qualityLabel))
        }
        return result
    }
}