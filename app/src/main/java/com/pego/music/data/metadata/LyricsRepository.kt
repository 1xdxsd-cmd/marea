package com.pego.music.data.metadata

import com.pego.music.domain.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Fetches crowdsourced plain & time-synced lyrics from LRCLIB (https://lrclib.net).
 */
class LyricsRepository(
    private val httpClient: OkHttpClient
) {
    suspend fun fetchLyrics(artist: String, title: String, durationSeconds: Long): LyricsResult? =
        withContext(Dispatchers.IO) {
            runCatching {
                val (cleanArtist, cleanTrack) = TitleCleaner.extractArtistAndTrack(title, artist)
                val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
                val encodedTrack = URLEncoder.encode(cleanTrack, "UTF-8")
                val durationParam = if (durationSeconds > 0) "&duration=$durationSeconds" else ""
                val url =
                    "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTrack$durationParam"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PEGO/0.1.0")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val json = JSONObject(body)
                    LyricsResult(
                        plainLyrics = json.optString("plainLyrics", "").ifBlank { null },
                        syncedLyrics = json.optString("syncedLyrics", "").ifBlank { null }
                    )
                }
            }.getOrNull()
        }
}
