package com.marea.music.data.metadata

import java.text.Normalizer

object TitleCleaner {

    private val noisePatterns = listOf(
        Regex("""\((?:official\s*(?:music\s*)?video|official\s*audio|lyric\s*video|lyrics?|audio|visualizer|hd|hq|4k|remastered.*?|explicit|clean|live.*?)\)""", RegexOption.IGNORE_CASE),
        Regex("""\[(?:official\s*(?:music\s*)?video|official\s*audio|lyric\s*video|lyrics?|audio|visualizer|hd|hq|4k|remastered.*?|explicit|clean|live.*?)\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*\|\s*.*$"""),
        Regex("""\s+(?:ft\.?|feat\.?)\s+.*$""", RegexOption.IGNORE_CASE)
    )

    private val artistSuffixPattern = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)

    private val nonOriginalKeywords = listOf(
        "cover",
        "karaoke",
        "instrumental",
        "remix",
        "8d audio",
        "8d",
        "nightcore",
        "slowed",
        "reverb",
        "sped up",
        "mashup",
        "tribute",
        "parody",
        "parodia",
        "reaction",
        "reaccion",
        "tutorial",
        "piano version",
        "guitar cover",
        "drum cover",
        "bass cover",
        "acoustic cover",
        "en vivo",
        "concierto",
        "live at",
        "bootleg",
        "acapella",
        "a cappella"
    )

    fun cleanArtist(rawArtist: String): String {
        return rawArtist
            .replace(artistSuffixPattern, "")
            .replace("VEVO", "", ignoreCase = true)
            .trim()
    }

    fun extractPrimaryArtist(rawArtist: String): String {
        val cleaned = cleanArtist(rawArtist)
        return cleaned
            .split(Regex("""\s*(?:,|feat\.?|ft\.?)\s*""", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            ?.ifBlank { cleaned }
            ?: cleaned
    }

    fun cleanTitle(rawTitle: String): String {
        var result = rawTitle.trim()
        for (pattern in noisePatterns) {
            result = pattern.replace(result, "")
        }
        return result.replace(Regex("""\s{2,}"""), " ").trim()
    }

    /**
     * Extracts (artist, trackTitle) from a raw YouTube title & uploader name.
     * If the title follows "Artist - Track", splits cleanly.
     */
    fun extractArtistAndTrack(rawTitle: String, rawUploader: String): Pair<String, String> {
        val cleanedTitle = cleanTitle(rawTitle)
        val cleanedUploader = cleanArtist(rawUploader)

        val dashIndex = cleanedTitle.indexOf(" - ")
        if (dashIndex > 0 && dashIndex < cleanedTitle.length - 3) {
            val possibleArtist = cleanedTitle.substring(0, dashIndex).trim()
            val possibleTrack = cleanedTitle.substring(dashIndex + 3).trim()
            if (possibleArtist.isNotEmpty() && possibleTrack.isNotEmpty()) {
                return possibleArtist to possibleTrack
            }
        }
        return cleanedUploader to cleanedTitle
    }

    fun normalizeForMatching(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun tokenOverlapScore(expected: String, candidate: String): Double {
        val expectedTokens = normalizeForMatching(expected)
            .split(" ")
            .filter { it.length >= 2 }
            .toSet()
        val candidateTokens = normalizeForMatching(candidate)
            .split(" ")
            .filter { it.length >= 2 }
            .toSet()

        if (expectedTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
        val intersection = expectedTokens.intersect(candidateTokens).size
        return intersection.toDouble() / expectedTokens.size.toDouble()
    }

    fun isGoodMetadataMatch(
        expectedTitle: String,
        expectedArtist: String,
        candidateTitle: String,
        candidateArtist: String
    ): Boolean {
        val cleanExpectedTitle = cleanTitle(expectedTitle)
        val titleScore = tokenOverlapScore(cleanExpectedTitle, cleanTitle(candidateTitle))
        if (titleScore < 0.6) return false

        val cleanExpectedArtist = cleanArtist(expectedArtist)
        if (cleanExpectedArtist.isBlank()) return titleScore >= 0.8

        val artistScore = tokenOverlapScore(cleanExpectedArtist, cleanArtist(candidateArtist))
        val artistInTitleScore = tokenOverlapScore(cleanExpectedArtist, expectedTitle)
        return artistScore >= 0.5 || artistInTitleScore >= 0.5
    }

    /**
     * Returns true if a YouTube track is likely an original release rather than a cover,
     * remix, karaoke, slowed/reverb, reaction, or live bootleg (unless explicitly searched).
     */
    fun isLikelyOriginalTrack(
        rawTitle: String,
        rawArtist: String,
        userQuery: String
    ): Boolean {
        val normalizedQuery = normalizeForMatching(userQuery)
        val combinedNormalized = normalizeForMatching("$rawTitle $rawArtist")

        for (keyword in nonOriginalKeywords) {
            if (normalizedQuery.contains(keyword)) continue
            val wordBoundary = Regex("""\b${Regex.escape(keyword)}\b""")
            if (wordBoundary.containsMatchIn(combinedNormalized)) {
                return false
            }
        }
        return true
    }

    /**
     * Strict artist matcher: returns true ONLY if [targetArtist] appears explicitly as the artist
     * or as an explicit featured/collaborating artist in [candidateArtist] or [candidateTitle].
     */
    fun isExplicitArtistMatch(
        targetArtist: String,
        candidateArtist: String,
        candidateTitle: String
    ): Boolean {
        val normTarget = normalizeForMatching(extractPrimaryArtist(targetArtist))
        if (normTarget.isBlank()) return false

        val normCandArtist = normalizeForMatching(cleanArtist(candidateArtist))
        val normCandTitle = normalizeForMatching(candidateTitle)

        val boundaryRegex = Regex("""\b${Regex.escape(normTarget)}\b""")
        if (boundaryRegex.containsMatchIn(normCandArtist)) return true

        // Check explicit collaborations in title: "feat X", "ft X", "with X", "x X"
        val collabRegex = Regex("""\b(?:feat|ft|with|con|y|x)\s+[^-]*\b${Regex.escape(normTarget)}\b""")
        if (collabRegex.containsMatchIn(normCandTitle)) return true

        // Check "Artist - Track" format in raw title
        val rawDash = candidateTitle.indexOf(" - ")
        if (rawDash > 0) {
            val leftPart = normalizeForMatching(candidateTitle.substring(0, rawDash))
            if (boundaryRegex.containsMatchIn(leftPart)) return true
        }

        return false
    }
}
