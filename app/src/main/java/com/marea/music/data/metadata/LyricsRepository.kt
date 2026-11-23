package com.marea.music.data.metadata

import com.marea.music.domain.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Letras (normales y sincronizadas) de LRCLIB (https://lrclib.net).
 * Cada letra encontrada se guarda en el teléfono, así que se ve sin internet; al descargar
 * una canción también se guarda su letra.
 */
class LyricsRepository(
    private val httpClient: OkHttpClient,
    private val cacheDir: File
) {
    suspend fun fetchLyrics(artist: String, title: String, durationSeconds: Long = 0L): LyricsResult? =
        withContext(Dispatchers.IO) {
            val (cleanArtist, cleanTrack) = TitleCleaner.extractArtistAndTrack(title, artist)
            val cacheFile = cacheFileFor(cleanArtist, cleanTrack)
            readCache(cacheFile)?.let { return@withContext it }

            val result = runCatching {
                val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
                val encodedTrack = URLEncoder.encode(cleanTrack, "UTF-8")
                val durationParam = if (durationSeconds > 0) "&duration=$durationSeconds" else ""
                val url =
                    "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTrack$durationParam"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Marea/1.0")
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

            if (result != null && (result.plainLyrics != null || result.syncedLyrics != null)) {
                writeCache(cacheFile, result)
            }
            result
        }

    private fun cacheFileFor(artist: String, track: String): File {
        val key = TitleCleaner.normalizeForMatching(artist) + "|" + TitleCleaner.normalizeForMatching(track)
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.json")
    }

    private fun readCache(file: File): LyricsResult? = runCatching {
        if (!file.exists()) return@runCatching null
        val json = JSONObject(file.readText())
        LyricsResult(
            plainLyrics = json.optString("plain", "").ifBlank { null },
            syncedLyrics = json.optString("synced", "").ifBlank { null }
        )
    }.getOrNull()

    private fun writeCache(file: File, result: LyricsResult) {
        runCatching {
            file.parentFile?.mkdirs()
            val json = JSONObject()
                .put("plain", result.plainLyrics.orEmpty())
                .put("synced", result.syncedLyrics.orEmpty())
            file.writeText(json.toString())
        }
    }
}
