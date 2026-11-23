package com.marea.music

import com.marea.music.data.extractor.ParsedYouTubeInput
import com.marea.music.data.extractor.YouTubeUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun `parses plain text as search query`() {
        val result = YouTubeUrlParser.parse("arctic monkeys 505")
        assertTrue(result is ParsedYouTubeInput.SearchQuery)
        assertEquals("arctic monkeys 505", (result as ParsedYouTubeInput.SearchQuery).query)
    }

    @Test
    fun `parses youtube playlist url`() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/playlist?list=PL1234567890")
        assertTrue(result is ParsedYouTubeInput.PlaylistUrl)
        assertEquals("PL1234567890", (result as ParsedYouTubeInput.PlaylistUrl).listId)
    }

    @Test
    fun `parses youtube music playlist url`() {
        val result = YouTubeUrlParser.parse("https://music.youtube.com/playlist?list=OLAK5uy_abc123&si=xyz")
        assertTrue(result is ParsedYouTubeInput.PlaylistUrl)
        assertEquals("OLAK5uy_abc123", (result as ParsedYouTubeInput.PlaylistUrl).listId)
    }

    @Test
    fun `parses single video url without list`() {
        val result = YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ")
        assertTrue(result is ParsedYouTubeInput.VideoUrl)
    }
}
