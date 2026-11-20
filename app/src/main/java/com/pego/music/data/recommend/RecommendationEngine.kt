package com.pego.music.data.recommend

import com.pego.music.data.extractor.YouTubeExtractorRepository
import com.pego.music.data.metadata.TitleCleaner
import com.pego.music.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-signal taste-matching recommendation engine combining:
 * 1. Track-level co-listening graph via YouTube Music (`StreamInfo.relatedItems`)
 * 2. Genre & style profiling via iTunes / MusicBrainz tags
 * 3. Collaborative similar-artist discovery via MetaBrainz ListenBrainz Labs
 *    (`metabrainz/listenbrainz-local`) + Deezer Related Artists, strictly filtered by genre & originality.
 */
class RecommendationEngine(
    private val httpClient: OkHttpClient,
    private val extractorRepo: YouTubeExtractorRepository
) {

    private data class ScoredCandidate(
        val track: TrackItem,
        var score: Double,
        val genreTokens: Set<String> = emptySet()
    )

    private val trackGenreCache = ConcurrentHashMap<String, Set<String>>()
    private val artistGenreCache = ConcurrentHashMap<String, Set<String>>()

    suspend fun recommendForLibrary(
        downloadedTracks: List<TrackItem>,
        limit: Int = 16
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        if (downloadedTracks.isEmpty()) {
            return@withContext fetchGlobalDiscoveryTracks(limit)
        }

        val ownedIds = downloadedTracks.map { it.id }.toSet()
        val ownedKeys = downloadedTracks.map {
            normalizeKey(it.artist, it.cleanTitle.ifBlank { it.title })
        }.toSet()

        // 1. Build Taste Profile from downloaded library (top artists + genres)
        val artistCounts = downloadedTracks
            .map { TitleCleaner.cleanArtist(it.artist) }
            .filter { it.isNotBlank() && !it.equals("Artista desconocido", ignoreCase = true) }
            .groupingBy { it.lowercase() }
            .eachCount()

        val topArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        val seedTracks = downloadedTracks
            .filter { it.sourceUrl.isNotBlank() }
            .take(4)

        val preferredGenres = coroutineScope {
            downloadedTracks.take(8).map { track ->
                async { fetchTrackGenres(track.artist, track.cleanTitle.ifBlank { track.title }) }
            }.awaitAll().flatten().toSet()
        }

        val userAllowsRemixOrLive = downloadedTracks.any {
            val lower = it.title.lowercase()
            lower.contains("remix") || lower.contains("live") || lower.contains("en vivo")
        }

        // 2. Gather candidates concurrently:
        //    A) YouTube Music track-level relatedItems (same vibe, language, subgenre)
        //    B) ListenBrainz Labs + Deezer similar artists filtered by genre
        val (ytRelatedLists, artistDiscoveryLists) = coroutineScope {
            val ytDeferred = async {
                seedTracks.take(3).map { seed ->
                    async {
                        runCatching { extractorRepo.getRelatedTracks(seed.sourceUrl) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
            }

            val discoveryDeferred = async {
                val selectedSeeds = topArtists.shuffled().take(3)
                selectedSeeds.map { seedArtist ->
                    async {
                        fetchGenreMatchedArtistCandidates(
                            seedArtist = seedArtist,
                            preferredGenres = preferredGenres
                        )
                    }
                }.awaitAll().flatten()
            }

            ytDeferred.await() to discoveryDeferred.await()
        }

        val candidateMap = LinkedHashMap<String, ScoredCandidate>()

        // Score A: YouTube track-level co-listening items (strongest taste signal)
        ytRelatedLists.forEachIndexed { seedIdx, list ->
            val recencyWeight = if (seedIdx == 0) 3.0 else 2.2
            list.forEachIndexed { pos, track ->
                val key = normalizeKey(track.artist, track.cleanTitle)
                if (track.id in ownedIds || key in ownedKeys) return@forEachIndexed
                if (!userAllowsRemixOrLive && !TitleCleaner.isLikelyOriginalTrack(track.title, track.artist, "")) {
                    return@forEachIndexed
                }

                val existing = candidateMap[key]
                val posScore = (1.0 - (pos * 0.04)).coerceAtLeast(0.2)
                val artistBonus = if (track.artist.lowercase() in artistCounts) 1.2 else 0.0
                if (existing != null) {
                    // Co-occurrence across multiple downloaded songs = high confidence taste match
                    existing.score += recencyWeight + 2.0 + posScore
                } else {
                    candidateMap[key] = ScoredCandidate(
                        track = track,
                        score = recencyWeight + posScore + artistBonus
                    )
                }
            }
        }

        // Score B: Genre-filtered ListenBrainz + Deezer tracks
        for (scored in artistDiscoveryLists) {
            val key = normalizeKey(scored.track.artist, scored.track.cleanTitle)
            if (scored.track.id in ownedIds || key in ownedKeys) continue
            if (!userAllowsRemixOrLive && !TitleCleaner.isLikelyOriginalTrack(scored.track.title, scored.track.artist, "")) {
                continue
            }

            val existing = candidateMap[key]
            if (existing != null) {
                existing.score += scored.score + 1.5
            } else {
                candidateMap[key] = scored
            }
        }

        // 3. Rank by taste score & cap per-artist repetition (max 2 per artist) for clean variety
        val ranked = candidateMap.values
            .sortedByDescending { it.score }

        val perArtistCount = mutableMapOf<String, Int>()
        val finalSelection = mutableListOf<TrackItem>()
        for (entry in ranked) {
            val artistKey = TitleCleaner.normalizeForMatching(entry.track.artist)
            val count = perArtistCount.getOrDefault(artistKey, 0)
            if (count >= 2) continue
            perArtistCount[artistKey] = count + 1
            finalSelection.add(entry.track)
            if (finalSelection.size >= limit) break
        }

        if (finalSelection.isNotEmpty()) finalSelection else fetchGlobalDiscoveryTracks(limit)
    }

    /**
     * Strictly fetches tracks belonging to [rawArtist] (or collaborations explicitly naming [rawArtist]),
     * excluding tracks already downloaded by the user.
     */
    suspend fun recommendStrictForArtist(
        rawArtist: String,
        downloadedTracks: List<TrackItem>,
        limit: Int = 20
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        val targetArtist = TitleCleaner.extractPrimaryArtist(rawArtist)
        if (targetArtist.isBlank()) return@withContext emptyList()

        val ownedIds = downloadedTracks.map { it.id }.toSet()
        val ownedKeys = downloadedTracks.map {
            normalizeKey(it.artist, it.cleanTitle.ifBlank { it.title })
        }.toSet()

        val (deezerArtistTracks, deezerCollabTracks, ytTracks) = coroutineScope {
            val d1 = async { fetchDeezerArtistTopTracks(targetArtist) }
            val d2 = async { fetchDeezerExplicitCollabs(targetArtist) }
            val y1 = async {
                runCatching {
                    extractorRepo.searchMusic(targetArtist, originalsOnly = true)
                }.getOrDefault(emptyList())
            }
            Triple(d1.await(), d2.await(), y1.await())
        }

        val combined = deezerArtistTracks + deezerCollabTracks + ytTracks
        combined
            .filter { track ->
                val key = normalizeKey(track.artist, track.cleanTitle.ifBlank { track.title })
                if (track.id in ownedIds || key in ownedKeys) return@filter false
                val explicit = TitleCleaner.isExplicitArtistMatch(
                    targetArtist = targetArtist,
                    candidateArtist = track.artist,
                    candidateTitle = track.title
                )
                val original = TitleCleaner.isLikelyOriginalTrack(
                    rawTitle = track.title,
                    rawArtist = track.artist,
                    userQuery = targetArtist
                )
                explicit && original
            }
            .distinctBy { normalizeKey(it.artist, it.cleanTitle.ifBlank { it.title }) }
            .take(limit)
    }

    private fun fetchDeezerArtistTopTracks(targetArtist: String): List<TrackItem> {
        return runCatching<List<TrackItem>> {
            val encoded = URLEncoder.encode(targetArtist, "UTF-8")
            val searchBody = executeGet("https://api.deezer.com/search/artist?q=$encoded&limit=3")
                ?: return@runCatching emptyList()
            val artists = JSONObject(searchBody).optJSONArray("data") ?: return@runCatching emptyList()

            for (i in 0 until artists.length()) {
                val obj = artists.getJSONObject(i)
                val name = obj.optString("name", "")
                if (TitleCleaner.isExplicitArtistMatch(targetArtist, name, name)) {
                    val artistId = obj.optLong("id", -1L)
                    if (artistId > 0L) {
                        val topJson = executeGet("https://api.deezer.com/artist/$artistId/top?limit=25")
                            ?: return@runCatching emptyList()
                        val data = JSONObject(topJson).optJSONArray("data") ?: return@runCatching emptyList()
                        return@runCatching parseDeezerTracks(data)
                    }
                }
            }
            emptyList()
        }.getOrDefault(emptyList())
    }

    private fun fetchDeezerExplicitCollabs(targetArtist: String): List<TrackItem> {
        return runCatching<List<TrackItem>> {
            val encoded = URLEncoder.encode(targetArtist, "UTF-8")
            val body = executeGet("https://api.deezer.com/search/track?q=$encoded&limit=20")
                ?: return@runCatching emptyList()
            val data = JSONObject(body).optJSONArray("data") ?: return@runCatching emptyList()
            parseDeezerTracks(data)
        }.getOrDefault(emptyList())
    }

    private fun fetchTrackGenres(artist: String, title: String): Set<String> {
        val cacheKey = normalizeKey(artist, title)
        trackGenreCache[cacheKey]?.let { return it }

        val genres = runCatching {
            val q = URLEncoder.encode("${TitleCleaner.cleanArtist(artist)} $title".trim(), "UTF-8")
            val body = executeGet("https://itunes.apple.com/search?term=$q&entity=song&limit=1")
                ?: return@runCatching emptySet()
            val results = JSONObject(body).optJSONArray("results") ?: return@runCatching emptySet()
            if (results.length() == 0) return@runCatching emptySet()

            val primaryGenre = results.getJSONObject(0).optString("primaryGenreName", "")
            parseGenreTokens(primaryGenre)
        }.getOrDefault(emptySet())

        trackGenreCache[cacheKey] = genres
        return genres
    }

    private fun fetchArtistGenres(artistName: String): Set<String> {
        val key = artistName.lowercase().trim()
        artistGenreCache[key]?.let { return it }

        val genres = runCatching {
            val q = URLEncoder.encode(artistName.trim(), "UTF-8")
            val body = executeGet("https://itunes.apple.com/search?term=$q&entity=musicArtist&limit=1")
                ?: return@runCatching emptySet()
            val results = JSONObject(body).optJSONArray("results") ?: return@runCatching emptySet()
            if (results.length() == 0) return@runCatching emptySet()

            val genre = results.getJSONObject(0).optString("primaryGenreName", "")
            parseGenreTokens(genre)
        }.getOrDefault(emptySet())

        artistGenreCache[key] = genres
        return genres
    }

    private fun parseGenreTokens(rawGenre: String): Set<String> {
        if (rawGenre.isBlank()) return emptySet()
        return TitleCleaner.normalizeForMatching(rawGenre)
            .split(" ")
            .filter { it.length >= 3 && it != "music" && it != "musica" }
            .toSet()
    }

    private fun fetchGenreMatchedArtistCandidates(
        seedArtist: String,
        preferredGenres: Set<String>
    ): List<ScoredCandidate> {
        val collected = mutableListOf<ScoredCandidate>()

        // 1. Deezer similar artists + top tracks, filtered by genre alignment
        runCatching {
            val encoded = URLEncoder.encode(seedArtist, "UTF-8")
            val searchBody = executeGet("https://api.deezer.com/search/artist?q=$encoded&limit=1") ?: return@runCatching
            val artistData = JSONObject(searchBody).optJSONArray("data") ?: return@runCatching
            if (artistData.length() == 0) return@runCatching

            val artistId = artistData.getJSONObject(0).optLong("id", -1L)
            if (artistId <= 0L) return@runCatching

            // Other popular tracks by the user's own liked artist
            executeGet("https://api.deezer.com/artist/$artistId/top?limit=4")?.let { topJson ->
                val arr = JSONObject(topJson).optJSONArray("data")
                if (arr != null) {
                    parseDeezerTracks(arr).forEach { track ->
                        collected.add(ScoredCandidate(track = track, score = 2.6))
                    }
                }
            }

            // Related artists strictly verified against preferredGenres
            executeGet("https://api.deezer.com/artist/$artistId/related?limit=6")?.let { relJson ->
                val relArtists = JSONObject(relJson).optJSONArray("data")
                if (relArtists != null) {
                    var matchedCount = 0
                    for (i in 0 until relArtists.length()) {
                        if (matchedCount >= 2) break
                        val relObj = relArtists.getJSONObject(i)
                        val relId = relObj.optLong("id", -1L)
                        val relName = relObj.optString("name", "")
                        if (relId <= 0L || relName.isBlank()) continue

                        val relGenres = fetchArtistGenres(relName)
                        val genreOverlap = if (preferredGenres.isEmpty() || relGenres.isEmpty()) {
                            0.5
                        } else if (preferredGenres.intersect(relGenres).isNotEmpty()) {
                            2.0
                        } else {
                            // Genre mismatch -> skip unrelated artist
                            continue
                        }

                        matchedCount++
                        executeGet("https://api.deezer.com/artist/$relId/top?limit=3")?.let { topJson ->
                            val arr = JSONObject(topJson).optJSONArray("data")
                            if (arr != null) {
                                parseDeezerTracks(arr).forEach { track ->
                                    collected.add(
                                        ScoredCandidate(
                                            track = track,
                                            score = 1.5 + genreOverlap,
                                            genreTokens = relGenres
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. ListenBrainz Labs collaborative similar artists (genre-verified)
        runCatching {
            val encoded = URLEncoder.encode("artist:\"$seedArtist\"", "UTF-8")
            val mbBody = executeGet(
                "https://musicbrainz.org/ws/2/artist/?query=$encoded&limit=1&fmt=json",
                mapOf("User-Agent" to "PEGO/0.1.0 (https://github.com/pego-music)")
            ) ?: return@runCatching
            val artists = JSONObject(mbBody).optJSONArray("artists") ?: return@runCatching
            if (artists.length() == 0) return@runCatching

            val mbid = artists.getJSONObject(0).optString("id", "")
            if (mbid.isBlank()) return@runCatching

            val lbUrl =
                "https://labs.api.listenbrainz.org/similar-artists/json?artist_mbid=$mbid&algorithm=session_based_days_7500_session_300_contribution_5_threshold_10_limiter_100_skip_normalized"
            val lbBody = executeGet(lbUrl) ?: return@runCatching
            val simArray = JSONArray(lbBody)

            var matched = 0
            for (i in 0 until simArray.length().coerceAtMost(5)) {
                if (matched >= 2) break
                val simName = simArray.getJSONObject(i).optString("name", "")
                if (simName.isBlank()) continue

                val simGenres = fetchArtistGenres(simName)
                if (preferredGenres.isNotEmpty() && simGenres.isNotEmpty() &&
                    preferredGenres.intersect(simGenres).isEmpty()
                ) {
                    continue
                }
                matched++

                val q = URLEncoder.encode(simName, "UTF-8")
                executeGet("https://api.deezer.com/search/track?q=artist:\"$q\"&limit=3")?.let { trJson ->
                    val arr = JSONObject(trJson).optJSONArray("data")
                    if (arr != null) {
                        parseDeezerTracks(arr).forEach { track ->
                            collected.add(
                                ScoredCandidate(
                                    track = track,
                                    score = 2.4,
                                    genreTokens = simGenres
                                )
                            )
                        }
                    }
                }
            }
        }

        return collected
    }

    private fun fetchGlobalDiscoveryTracks(limit: Int): List<TrackItem> {
        return runCatching {
            val body = executeGet("https://api.deezer.com/chart/0/tracks?limit=$limit") ?: return emptyList()
            val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
            parseDeezerTracks(data)
        }.getOrDefault(emptyList())
    }

    private fun parseDeezerTracks(array: JSONArray): List<TrackItem> {
        val list = mutableListOf<TrackItem>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optLong("id", 0L)
            val title = item.optString("title_short", "").ifBlank { item.optString("title", "") }
            val artist = item.optJSONObject("artist")?.optString("name", "").orEmpty()
            val albumObj = item.optJSONObject("album")
            val album = albumObj?.optString("title", "").orEmpty()
            val cover = albumObj?.optString("cover_xl", "")
                ?.ifBlank { albumObj.optString("cover_big", "") }
                ?.ifBlank { albumObj.optString("cover_medium", "") }
                .orEmpty()
            val duration = item.optLong("duration", 0L)

            if (title.isNotBlank() && artist.isNotBlank()) {
                list.add(
                    TrackItem(
                        id = "rec_$id",
                        title = "$artist - $title",
                        cleanTitle = title,
                        artist = artist,
                        album = album,
                        durationSeconds = duration,
                        thumbnailUrl = cover,
                        hdCoverUrl = cover,
                        sourceUrl = ""
                    )
                )
            }
        }
        return list
    }

    private fun normalizeKey(artist: String, title: String): String {
        return TitleCleaner.normalizeForMatching("${TitleCleaner.cleanArtist(artist)} ${TitleCleaner.cleanTitle(title)}")
    }

    private fun executeGet(url: String, headers: Map<String, String> = emptyMap()): String? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}
