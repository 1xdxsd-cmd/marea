package com.marea.music.data.metadata

import com.marea.music.domain.model.EnrichedMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class CoverCandidate(
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String,
    val source: String
)

/**
 * Enriches track metadata and fetches high-resolution square album artwork using a fast parallel
 * cascade of keyless/open APIs: iTunes Search & Deezer concurrently -> MusicBrainz fallback.
 * Candidates are strictly validated against the expected track/artist to prevent false matches.
 */
class MetadataEnricher(
    private val httpClient: OkHttpClient
) {

    suspend fun enrich(rawTitle: String, rawArtist: String): EnrichedMetadata =
        withContext(Dispatchers.IO) {
            val (artist, track) = TitleCleaner.extractArtistAndTrack(rawTitle, rawArtist)
            val fallback = EnrichedMetadata(
                title = track.ifBlank { rawTitle },
                artist = artist.ifBlank { rawArtist },
                album = "",
                year = "",
                coverUrl = ""
            )

            // Query iTunes and Deezer in parallel for minimum latency
            val (itunesResult, deezerResult) = coroutineScope {
                val itunesDeferred = async { fetchFromItunes(artist, track) }
                val deezerDeferred = async { fetchFromDeezer(artist, track) }
                itunesDeferred.await() to deezerDeferred.await()
            }

            itunesResult?.let { return@withContext it }
            deezerResult?.let { return@withContext it }
            fetchFromMusicBrainz(artist, track)?.let { return@withContext it }

            fallback
        }

    /**
     * Searches internet cover art candidates (used in the manual Cover Editor).
     */
    suspend fun searchCoverCandidates(query: String): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")

            coroutineScope {
                val itunesDeferred = async {
                    runCatching {
                        val url = "https://itunes.apple.com/search?term=$encoded&entity=album&limit=8"
                        val body = executeGet(url) ?: return@runCatching emptyList()
                        val results = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
                        (0 until results.length()).mapNotNull { i ->
                            val item = results.getJSONObject(i)
                            val art100 = item.optString("artworkUrl100", "")
                            val artHd = art100
                                .replace("100x100bb", "600x600bb")
                                .replace("100x100", "600x600")
                            if (artHd.isBlank()) null
                            else CoverCandidate(
                                title = item.optString("collectionName", ""),
                                artist = item.optString("artistName", ""),
                                album = item.optString("collectionName", ""),
                                imageUrl = artHd,
                                source = "iTunes"
                            )
                        }
                    }.getOrDefault(emptyList())
                }

                val deezerDeferred = async {
                    runCatching {
                        val url = "https://api.deezer.com/search/album?q=$encoded&limit=8"
                        val body = executeGet(url) ?: return@runCatching emptyList()
                        val data = JSONObject(body).optJSONArray("data") ?: return@runCatching emptyList()
                        (0 until data.length()).mapNotNull { i ->
                            val item = data.getJSONObject(i)
                            val cover = item.optString("cover_xl", "")
                                .ifBlank { item.optString("cover_big", "") }
                                .ifBlank { item.optString("cover_medium", "") }
                            val artistName = item.optJSONObject("artist")?.optString("name", "").orEmpty()
                            if (cover.isBlank()) null
                            else CoverCandidate(
                                title = item.optString("title", ""),
                                artist = artistName,
                                album = item.optString("title", ""),
                                imageUrl = cover,
                                source = "Deezer"
                            )
                        }
                    }.getOrDefault(emptyList())
                }

                (itunesDeferred.await() + deezerDeferred.await()).distinctBy { it.imageUrl }
            }
        }

    private fun fetchFromItunes(artist: String, track: String): EnrichedMetadata? {
        return runCatching {
            val query = URLEncoder.encode("$artist $track".trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=5"
            val body = executeGet(url) ?: return null
            val results = JSONObject(body).optJSONArray("results") ?: return null

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val candidateTitle = item.optString("trackName", "")
                val candidateArtist = item.optString("artistName", "")
                if (!TitleCleaner.isGoodMetadataMatch(track, artist, candidateTitle, candidateArtist)) {
                    continue
                }

                val artwork100 = item.optString("artworkUrl100", "")
                val artworkHd = artwork100
                    .replace("100x100bb", "600x600bb")
                    .replace("100x100", "600x600")
                val releaseDate = item.optString("releaseDate", "")
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""

                return EnrichedMetadata(
                    title = candidateTitle.ifBlank { track },
                    artist = candidateArtist.ifBlank { artist },
                    album = item.optString("collectionName", ""),
                    year = year,
                    coverUrl = artworkHd
                )
            }
            null
        }.getOrNull()
    }

    private fun fetchFromDeezer(artist: String, track: String): EnrichedMetadata? {
        return runCatching {
            val query = URLEncoder.encode("$artist $track".trim(), "UTF-8")
            val url = "https://api.deezer.com/search?q=$query&limit=5"
            val body = executeGet(url) ?: return null
            val data = JSONObject(body).optJSONArray("data") ?: return null

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val artistObj = item.optJSONObject("artist")
                val candidateTitle = item.optString("title", "")
                val candidateArtist = artistObj?.optString("name", "").orEmpty()
                if (!TitleCleaner.isGoodMetadataMatch(track, artist, candidateTitle, candidateArtist)) {
                    continue
                }

                val albumObj = item.optJSONObject("album")
                val cover = albumObj?.optString("cover_xl", "")
                    ?.ifBlank { albumObj.optString("cover_big", "") }
                    ?.ifBlank { albumObj.optString("cover_medium", "") }
                    .orEmpty()

                return EnrichedMetadata(
                    title = candidateTitle.ifBlank { track },
                    artist = candidateArtist.ifBlank { artist },
                    album = albumObj?.optString("title", "").orEmpty(),
                    year = "",
                    coverUrl = cover
                )
            }
            null
        }.getOrNull()
    }

    private fun fetchFromMusicBrainz(artist: String, track: String): EnrichedMetadata? {
        return runCatching {
            val q = URLEncoder.encode("recording:\"$track\" AND artist:\"$artist\"", "UTF-8")
            val url = "https://musicbrainz.org/ws/2/recording/?query=$q&limit=3&fmt=json"
            val body = executeGet(url, mapOf("User-Agent" to "Marea/1.0"))
                ?: return null
            val recordings = JSONObject(body).optJSONArray("recordings") ?: return null

            for (i in 0 until recordings.length()) {
                val rec = recordings.getJSONObject(i)
                val recTitle = rec.optString("title", "")
                val credits = rec.optJSONArray("artist-credit")
                val recArtist = if (credits != null && credits.length() > 0) {
                    credits.getJSONObject(0).optString("name", artist)
                } else artist

                if (!TitleCleaner.isGoodMetadataMatch(track, artist, recTitle, recArtist)) {
                    continue
                }

                val releases = rec.optJSONArray("releases")
                val firstRelease = if (releases != null && releases.length() > 0) releases.getJSONObject(0) else null
                val releaseId = firstRelease?.optString("id", "").orEmpty()
                val albumTitle = firstRelease?.optString("title", "").orEmpty()
                val date = firstRelease?.optString("date", "").orEmpty()
                val year = if (date.length >= 4) date.substring(0, 4) else ""
                val coverUrl = if (releaseId.isNotBlank()) {
                    "https://coverartarchive.org/release/$releaseId/front-500"
                } else {
                    ""
                }

                return EnrichedMetadata(
                    title = recTitle.ifBlank { track },
                    artist = recArtist.ifBlank { artist },
                    album = albumTitle,
                    year = year,
                    coverUrl = coverUrl
                )
            }
            null
        }.getOrNull()
    }

    private fun executeGet(url: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        val builder = Request.Builder().url(url).get()
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}
