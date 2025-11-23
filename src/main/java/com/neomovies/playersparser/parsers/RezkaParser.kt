package com.neomovies.playersparser.parsers

import com.neomovies.playersparser.models.*
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class RezkaParser(
    private val settings: RezkaSettings = RezkaSettings(),
    client: OkHttpClient? = null
) : BaseParser(client ?: createDefaultClient()) {

    companion object {
        private val cookieStore = HashMap<String, MutableList<Cookie>>()

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .cookieJar(object : CookieJar {
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        val existing = cookieStore[url.host] ?: ArrayList()
                        for (cookie in cookies) {
                            existing.removeIf { it.name == cookie.name }
                            existing.add(cookie)
                        }
                        cookieStore[url.host] = existing
                    }

                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        val cookies = cookieStore[url.host] ?: ArrayList()
                        val result = ArrayList(cookies)

                        // Принудительные куки (как в Lampac)
                        val forcedCookies = listOf(
                            Cookie.Builder().name("hdmbbs").value("1").domain(url.host).build(),
                            Cookie.Builder().name("dle_user_taken").value("1").domain(url.host).build()
                        )
                        for (forced in forcedCookies) {
                            if (result.none { it.name == forced.name }) result.add(forced)
                        }
                        return result
                    }
                })
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private fun getPageHeaders(): Headers {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .add("X-App-Hdrezka-App", "1")
            .build()
    }

    private fun getAjaxHeaders(referer: String): Headers {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .add("Accept", "application/json, text/javascript, */*; q=0.01")
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .add("Origin", settings.baseUrl)
            .add("Referer", referer)
            .add("X-Requested-With", "XMLHttpRequest")
            .add("X-App-Hdrezka-App", "1")
            .build()
    }

    override suspend fun search(query: String): SearchResponse {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "${settings.baseUrl}/engine/ajax/search.php?q=$encodedQuery"
            val request = Request.Builder().url(url).headers(getPageHeaders()).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return SearchResponse(emptyList(), "Empty response")
            SearchResponse(parseAjaxSearchResults(html))
        } catch (e: Exception) {
            SearchResponse(emptyList(), "Error: ${e.message}")
        }
    }

    override suspend fun getPlayer(
        id: String,
        type: String,
        season: Int?,
        episode: Int?
    ): PlayerResponse {
        return try {
            val pageUrl = if (id.startsWith("http")) id else "${settings.baseUrl}$id"

            val pageRequest = Request.Builder().url(pageUrl).headers(getPageHeaders()).build()
            val pageResponse = client.newCall(pageRequest).execute()
            val pageHtml = pageResponse.body?.string() ?: return PlayerResponse(success = false, error = "Failed to load page")

            val initPattern = Pattern.compile("initCDN(?:Series|Movies)Events\\(\\d+,\\s*(\\d+),.+?(\\{.*?\\})\\);")
            val matcher = initPattern.matcher(pageHtml)

            var trashUrl: String = ""
            var translatorId: String = ""
            var postId: String = ""

            // Пытаемся найти данные на странице
            if (matcher.find()) {
                translatorId = matcher.group(1) // ID озвучки по умолчанию
                val jsonConfigString = matcher.group(2) // JSON конфиг прямо из HTML

                val isDefaultRequest = (season == null && episode == null) || (season == 1 && episode == 1)

                if (isDefaultRequest) {
                    try {
                        val jsonConfig = JSONObject(jsonConfigString)
                        trashUrl = jsonConfig.optString("streams") // В конфиге ключ 'streams'
                    } catch (e: Exception) {

                    }
                }
            }

            val postIdMatcher = Pattern.compile("initCDN(?:Series|Movies)Events\\(\\s*(\\d+)").matcher(pageHtml)
            if (postIdMatcher.find()) {
                postId = postIdMatcher.group(1)
            }

            if (trashUrl.isEmpty()) {
                if (postId.isEmpty() || translatorId.isEmpty()) {
                    if (pageHtml.contains("g-recaptcha")) return PlayerResponse(success = false, error = "Captcha detected")
                    return PlayerResponse(success = false, error = "Could not parse IDs")
                }

                val streamUrl = "${settings.baseUrl}/ajax/get_cdn_series/?t=$translatorId"
                val formBody = FormBody.Builder()
                    .add("id", postId)
                    .add("translator_id", translatorId)
                    .add("action", if (type == "series") "get_stream" else "get_movie")

                if (type == "series" && season != null && episode != null) {
                    formBody.add("s", season.toString())
                    formBody.add("e", episode.toString())
                }

                val streamRequest = Request.Builder()
                    .url(streamUrl)
                    .post(formBody.build())
                    .headers(getAjaxHeaders(pageUrl))
                    .build()

                val streamResponse = client.newCall(streamRequest).execute()
                val responseJson = streamResponse.body?.string() ?: return PlayerResponse(success = false, error = "Empty AJAX response")

                val jsonObject = JSONObject(responseJson)
                if (!jsonObject.getBoolean("success")) {
                    return PlayerResponse(success = false, error = "API Error: ${jsonObject.optString("message")}")
                }
                trashUrl = jsonObject.optString("url")
            }

            if (trashUrl.isEmpty()) {
                return PlayerResponse(success = false, error = "No video URL found")
            }

            // 4. Декодирование
            val decodedString = RezkaDecoder.decode(trashUrl)
            val playlist = RezkaDecoder.parseQualities(decodedString)

            if (playlist.isEmpty()) {
                if (decodedString.startsWith("http")) {
                    return PlayerResponse(true, decodedString, listOf(PlaylistItem(decodedString, "video", "Default")))
                }
                return PlayerResponse(success = false, error = "Failed to parse: $decodedString")
            }

            return PlayerResponse(true, playlist.last().url, playlist.reversed())

        } catch (e: Exception) {
            e.printStackTrace()
            return PlayerResponse(success = false, error = "Rezka Error: ${e.message}")
        }
    }
    /**
    * Получить информацию о сезонах и эпизодах со страницы сериала.
    * @param id URL страницы сериала (например, https://hdrezka.me/...html)
    */
    suspend fun getSeriesInfo(id: String): Map<Int, List<Int>>? {
        return try {
            val pageUrl = if (id.startsWith("http")) id else "${settings.baseUrl}$id"

            // 1. Загружаем страницу
            val request = Request.Builder().url(pageUrl).headers(getPageHeaders()).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            // 2. Парсим эпизоды регуляркой
            // Ищем теги <a class="b-simple_episode__item" ... data-season_id="X" data-episode_id="Y"
            // Используем [^>]* чтобы пропустить любые другие атрибуты между ними
            val regex = "class=\"b-simple_episode__item[^>]*data-season_id=\"(\\d+)\"[^>]*data-episode_id=\"(\\d+)\"".toRegex()

            val seasons = mutableMapOf<Int, MutableList<Int>>()

            regex.findAll(html).forEach { match ->
                val season = match.groupValues[1].toInt()
                val episode = match.groupValues[2].toInt()

                seasons.getOrPut(season) { mutableListOf() }.add(episode)
            }

            if (seasons.isEmpty()) return null

            // 3. Сортируем эпизоды и сезоны
            val sortedSeasons = mutableMapOf<Int, List<Int>>()
            seasons.toSortedMap().forEach { (season, episodes) ->
                sortedSeasons[season] = episodes.sorted()
            }

            return sortedSeasons

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseAjaxSearchResults(html: String): List<RezkaSearchItem> {
        val list = mutableListOf<RezkaSearchItem>()
        val items = html.split("</li>")
        for (item in items) {
            if (!item.contains("<a href=")) continue
            val urlMatcher = Pattern.compile("href=\"([^\"]+)\"").matcher(item)
            val url = if (urlMatcher.find()) urlMatcher.group(1) else ""
            val titleMatcher = Pattern.compile("<span class=\"enty\">([^<]+)</span>").matcher(item)
            val title = if (titleMatcher.find()) titleMatcher.group(1) else ""
            val yearMatcher = Pattern.compile("\\((\\d{4})").matcher(item)
            val year = if (yearMatcher.find()) yearMatcher.group(1) else null
            val type = if (item.contains("сериал") || url.contains("/series/") || item.contains("мульт")) "series" else "movie"
            if (url.isNotEmpty() && title.isNotEmpty()) {
                list.add(RezkaSearchItem(url, title, year, null, type))
            }
        }
        return list
    }
}