package com.marea.music

import com.marea.music.data.metadata.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    private val lrc = """
        [00:01.50] Primera
        [00:05.00]Segunda
        [00:10.123][00:20.00] Coro
        sin tiempo
    """.trimIndent()

    @Test
    fun parsesTimesAndRepeatedTags() {
        val lines = LrcParser.parse(lrc)
        assertEquals(listOf(1_500L, 5_000L, 10_123L, 20_000L), lines.map { it.timeMs })
        assertEquals(listOf("Primera", "Segunda", "Coro", "Coro"), lines.map { it.text })
    }

    @Test
    fun findsActiveLine() {
        val lines = LrcParser.parse(lrc)
        assertEquals(-1, LrcParser.activeIndex(lines, 1_000L))
        assertEquals(0, LrcParser.activeIndex(lines, 1_500L))
        assertEquals(1, LrcParser.activeIndex(lines, 9_999L))
        assertEquals(3, LrcParser.activeIndex(lines, 60_000L))
    }
}
