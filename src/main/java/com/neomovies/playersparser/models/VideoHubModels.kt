package com.neomovies.playersparser.models

import org.json.JSONObject

// --- VideoHub Specific Models ---

// API Response для плейлиста (шаг 1)
data class VideoHubPlaylistResponse(
    val titleName: String,
    val isSerial: Boolean,
    val items: List<VideoHubItem>
)

data class VideoHubItem(
    val cvhId: String,
    val name: String?,
    val vkId: String,
    val voiceStudio: String,
    val voiceType: String,
    val season: Int? = null,
    val episode: Int? = null
)

// API Response для видео (шаг 3)
data class VideoHubVideoResponse(
    val unitedVideoId: Long,
    val duration: Int,
    val failoverHost: String,
    val thumbUrl: String,
    val sources: VideoHubSources
)

data class VideoHubSources(
    val hlsUrl: String,
    val dashUrl: String,
    val mpegQhdUrl: String,
    val mpeg2kUrl: String,
    val mpeg4kUrl: String,
    val mpegHighUrl: String,
    val mpegFullHdUrl: String,
    val mpegMediumUrl: String,
    val mpegLowUrl: String,
    val mpegLowestUrl: String,
    val mpegTinyUrl: String
)

// Settings
data class VideoHubSettings(
    val apiHost: String = "https://plapi.cdnvideohub.com/api/v1/player/sv",
    val pub: String = "12",
    val aggr: String = "kp"
)

// --- JSON Parsers implementation ---

object VideoHubJsonParser {
    fun parsePlaylistResponse(json: String): VideoHubPlaylistResponse {
        val obj = JSONObject(json)
        val titleName = obj.optString("titleName", "Unknown")
        val isSerial = obj.optBoolean("isSerial", false)
        
        val itemsList = mutableListOf<VideoHubItem>()
        val itemsArray = obj.optJSONArray("items")
        
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                itemsList.add(
                    VideoHubItem(
                        cvhId = item.getString("cvhId"),
                        name = item.optString("name", null),
                        vkId = item.getString("vkId"),
                        voiceStudio = item.optString("voiceStudio", ""),
                        voiceType = item.optString("voiceType", ""),
                        season = if (item.has("season") && !item.isNull("season")) item.getInt("season") else null,
                        episode = if (item.has("episode") && !item.isNull("episode")) item.getInt("episode") else null
                    )
                )
            }
        }
        
        return VideoHubPlaylistResponse(titleName, isSerial, itemsList)
    }
    
    fun parseVideoResponse(json: String): VideoHubVideoResponse {
        val obj = JSONObject(json)
        val unitedVideoId = obj.optLong("unitedVideoId", 0)
        val duration = obj.optInt("duration", 0)
        val failoverHost = obj.optString("failoverHost", "")
        val thumbUrl = obj.optString("thumbUrl", "")
        
        val sourcesObj = obj.optJSONObject("sources") ?: JSONObject()
        val sources = VideoHubSources(
            hlsUrl = sourcesObj.optString("hlsUrl", ""),
            dashUrl = sourcesObj.optString("dashUrl", ""),
            mpegQhdUrl = sourcesObj.optString("mpegQhdUrl", ""),
            mpeg2kUrl = sourcesObj.optString("mpeg2kUrl", ""),
            mpeg4kUrl = sourcesObj.optString("mpeg4kUrl", ""),
            mpegHighUrl = sourcesObj.optString("mpegHighUrl", ""),
            mpegFullHdUrl = sourcesObj.optString("mpegFullHdUrl", ""),
            mpegMediumUrl = sourcesObj.optString("mpegMediumUrl", ""),
            mpegLowUrl = sourcesObj.optString("mpegLowUrl", ""),
            mpegLowestUrl = sourcesObj.optString("mpegLowestUrl", ""),
            mpegTinyUrl = sourcesObj.optString("mpegTinyUrl", "")
        )
        
        return VideoHubVideoResponse(unitedVideoId, duration, failoverHost, thumbUrl, sources)
    }
}