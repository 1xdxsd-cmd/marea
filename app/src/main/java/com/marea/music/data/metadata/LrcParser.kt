package com.marea.music.data.metadata

/** Una línea de letra sincronizada: desde [timeMs] se canta [text]. */
data class LyricLine(val timeMs: Long, val text: String)

/** Lee letras en formato LRC ("[01:23.45] texto") como las que entrega LRCLIB. */
object LrcParser {
    private val TIME_TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lines()) {
            val tags = TIME_TAG.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val text = raw.substring(tags.last().range.last + 1).trim()
            for (tag in tags) {
                val min = tag.groupValues[1].toLong()
                val sec = tag.groupValues[2].toLong()
                val fraction = tag.groupValues[3]
                val ms = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                lines.add(LyricLine(min * 60_000 + sec * 1_000 + ms, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** Índice de la línea que suena en [positionMs], o -1 antes de la primera. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}
