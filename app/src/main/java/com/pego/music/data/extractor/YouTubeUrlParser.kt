package com.pego.music.data.extractor

import java.net.URI

sealed class ParsedYouTubeInput {
    data class PlaylistUrl(val url: String, val listId: String) : ParsedYouTubeInput()
    data class VideoUrl(val url: String) : ParsedYouTubeInput()
    data class SearchQuery(val query: String) : ParsedYouTubeInput()
}

object YouTubeUrlParser {

    private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    fun extractFirstUrl(text: String): String? {
        return urlRegex.find(text)?.value
    }

    fun parse(rawInput: String): ParsedYouTubeInput {
        val trimmed = rawInput.trim()
        val candidateUrl = extractFirstUrl(trimmed) ?: return ParsedYouTubeInput.SearchQuery(trimmed)

        return runCatching {
            val uri = URI(candidateUrl)
            val host = uri.host?.lowercase().orEmpty()
            val isYouTubeHost = host.contains("youtube.com") || host.contains("youtu.be")
            if (!isYouTubeHost) {
                return@runCatching ParsedYouTubeInput.SearchQuery(trimmed)
            }

            val queryParams = parseQueryParams(uri.rawQuery)
            val listId = queryParams["list"]
            val path = uri.path?.lowercase().orEmpty()

            if (!listId.isNullOrBlank() && (path.contains("/playlist") || !queryParams.containsKey("v"))) {
                val normalized = "https://www.youtube.com/playlist?list=$listId"
                ParsedYouTubeInput.PlaylistUrl(url = normalized, listId = listId)
            } else if (!listId.isNullOrBlank() && queryParams.containsKey("v")) {
                // Watch link with embedded list parameter: let UI inspect playlist cleanly
                val normalized = "https://www.youtube.com/playlist?list=$listId"
                ParsedYouTubeInput.PlaylistUrl(url = normalized, listId = listId)
            } else {
                ParsedYouTubeInput.VideoUrl(url = candidateUrl)
            }
        }.getOrElse {
            ParsedYouTubeInput.SearchQuery(trimmed)
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null
                else part.substring(0, idx) to part.substring(idx + 1)
            }
            .toMap()
    }
}
