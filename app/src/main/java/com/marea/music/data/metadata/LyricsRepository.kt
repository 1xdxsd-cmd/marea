package com.marea.music.data.metadata

import com.marea.music.domain.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Letras (normales y sincronizadas) de LRCLIB (https://lrclib.net).
 * Cada letra encontrada se guarda en el teléfono por artista + título y también por el id de
 * la canción, así que se ve sin internet aunque el nombre cambie después de descargarla.
 */
class LyricsRepository(
    private val httpClient: OkHttpClient,
    private val cacheDir: File
) {
    suspend fun fetchLyrics(
        artist: String,
        title: String,
        durationSeconds: Long = 0L,
        trackIds: List<String> = emptyList()
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val (cleanArtist, cleanTrack) = TitleCleaner.extractArtistAndTrack(title, artist)
        val keyFile = cacheFileFor(cleanArtist, cleanTrack)
        val idFiles = trackIds.filter { it.isNotBlank() }.distinct().map { idCacheFile(it) }

        (idFiles + keyFile).firstNotNullOfOrNull { readCache(it) }?.let { cached ->
            idFiles.forEach { if (!it.exists()) writeCache(it, cached) }
            if (!keyFile.exists()) writeCache(keyFile, cached)
            return@withContext cached
        }

        val result = getExact(cleanArtist, cleanTrack, durationSeconds)
            ?: search("track_name=${enc(cleanTrack)}&artist_name=${enc(cleanArtist)}", durationSeconds)
            ?: search("q=${enc("$cleanArtist $cleanTrack")}", durationSeconds)

        if (result != null && result.hasLyrics()) {
            writeCache(keyFile, result)
            idFiles.forEach { writeCache(it, result) }
        }
        result
    }

    private fun getExact(artist: String, track: String, durationSeconds: Long): LyricsResult? = runCatching {
        val durationParam = if (durationSeconds > 0) "&duration=$durationSeconds" else ""
        val body = get("https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(track)}$durationParam")
            ?: return@runCatching null
        JSONObject(body).toLyrics()?.takeIf { it.hasLyrics() }
    }.getOrNull()

    /** Si no hay coincidencia exacta, busca y se queda con la más parecida en duración. */
    private fun search(query: String, durationSeconds: Long): LyricsResult? = runCatching {
        val body = get("https://lrclib.net/api/search?$query") ?: return@runCatching null
        val arr = JSONArray(body)
        val candidates = (0 until minOf(arr.length(), 15)).map { arr.getJSONObject(it) }
            .filter { it.optString("plainLyrics").isNotBlank() || it.optString("syncedLyrics").isNotBlank() }
            .filter { durationSeconds <= 0 || abs(it.optDouble("duration", 0.0) - durationSeconds) <= 8 }
        val best = candidates.firstOrNull { it.optString("syncedLyrics").isNotBlank() } ?: candidates.firstOrNull()
        best?.toLyrics()
    }.getOrNull()

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "Marea/1.0").get().build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun JSONObject.toLyrics(): LyricsResult? = LyricsResult(
        plainLyrics = optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" },
        syncedLyrics = optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
    )

    private fun LyricsResult.hasLyrics() = plainLyrics != null || syncedLyrics != null

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun cacheFileFor(artist: String, track: String): File {
        val key = TitleCleaner.normalizeForMatching(artist) + "|" + TitleCleaner.normalizeForMatching(track)
        return File(cacheDir, sha1(key) + ".json")
    }

    private fun idCacheFile(trackId: String): File = File(cacheDir, "id_" + sha1(trackId) + ".json")

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun readCache(file: File): LyricsResult? = runCatching {
        if (!file.exists()) return@runCatching null
        val json = JSONObject(file.readText())
        LyricsResult(
            plainLyrics = json.optString("plain", "").ifBlank { null },
            syncedLyrics = json.optString("synced", "").ifBlank { null }
        ).takeIf { it.hasLyrics() }
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
