package com.marea.music.data.podcast

import android.content.Context
import android.util.Xml
import com.marea.music.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Un podcast (programa) del directorio de Apple Podcasts. */
data class PodcastShow(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String,
    val feedUrl: String
)

/** Un episodio leído del RSS del podcast. */
data class PodcastEpisode(
    val id: String,
    val showId: String,
    val title: String,
    val audioUrl: String,
    val publishedAt: Long,
    val durationSeconds: Long,
    val description: String,
    val imageUrl: String
)

/**
 * Podcasts: búsqueda en el directorio público de Apple (iTunes Search API, sin cuenta),
 * episodios desde el RSS de cada podcast y la lista de podcasts que sigues (en el teléfono).
 * Todo el trabajo de red y de lectura del RSS corre fuera del hilo principal.
 */
class PodcastRepository(
    context: Context,
    private val httpClient: OkHttpClient
) {
    private val followFile = File(context.applicationContext.filesDir, "podcasts_following.json")
    private val episodesDir = File(context.applicationContext.filesDir, "podcast_cache")
    private val episodeCache = ConcurrentHashMap<String, List<PodcastEpisode>>()

    private val _following = MutableStateFlow<List<PodcastShow>>(emptyList())
    val following: StateFlow<List<PodcastShow>> = _following.asStateFlow()
    private var loaded = false

    suspend fun loadFollowing() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loaded = true
        _following.value = runCatching {
            if (!followFile.exists()) return@runCatching emptyList()
            val arr = JSONArray(followFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PodcastShow(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    author = o.optString("author"),
                    artworkUrl = o.optString("artwork"),
                    feedUrl = o.optString("feed")
                )
            }.filter { it.id.isNotBlank() && it.feedUrl.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    suspend fun toggleFollow(show: PodcastShow) = withContext(Dispatchers.IO) {
        val current = _following.value
        val next = if (current.any { it.id == show.id }) {
            current.filter { it.id != show.id }
        } else {
            listOf(show) + current
        }
        _following.value = next
        runCatching {
            val arr = JSONArray()
            next.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("author", it.author)
                        .put("artwork", it.artworkUrl)
                        .put("feed", it.feedUrl)
                )
            }
            followFile.writeText(arr.toString())
        }
    }

    suspend fun search(term: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(term.trim(), "UTF-8")
        val country = Locale.getDefault().country.ifBlank { "MX" }
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=30&term=$q&country=$country"
        val body = httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("No se pudo buscar podcasts (${resp.code})")
            resp.body?.string().orEmpty()
        }
        val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i ->
            val o = results.getJSONObject(i)
            val feed = o.optString("feedUrl")
            if (feed.isBlank()) return@mapNotNull null
            PodcastShow(
                id = o.optLong("collectionId").toString(),
                title = o.optString("collectionName"),
                author = o.optString("artistName"),
                artworkUrl = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") },
                feedUrl = feed
            )
        }
    }

    suspend fun episodes(show: PodcastShow, forceRefresh: Boolean = false): List<PodcastEpisode> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) episodeCache[show.feedUrl]?.let { return@withContext it }
            val list = httpClient.newCall(Request.Builder().url(show.feedUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("No se pudieron cargar los episodios (${resp.code})")
                val stream = resp.body?.byteStream() ?: return@use emptyList()
                parseFeed(stream, show)
            }
            episodeCache[show.feedUrl] = list
            if (list.isNotEmpty()) writeDiskCache(show, list)
            list
        }

    /** Episodios guardados de la última vez: se muestran al instante mientras se actualizan. */
    suspend fun cachedEpisodes(show: PodcastShow): List<PodcastEpisode>? = withContext(Dispatchers.IO) {
        episodeCache[show.feedUrl]?.let { return@withContext it }
        runCatching {
            val file = diskCacheFile(show)
            if (!file.exists()) return@runCatching null
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PodcastEpisode(
                    id = o.getString("id"),
                    showId = show.id,
                    title = o.optString("title"),
                    audioUrl = o.getString("audio"),
                    publishedAt = o.optLong("date"),
                    durationSeconds = o.optLong("dur"),
                    description = o.optString("desc"),
                    imageUrl = o.optString("img")
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Ya se actualizó hace poco en esta sesión (no hace falta volver a bajar el RSS). */
    fun isFresh(show: PodcastShow): Boolean = episodeCache.containsKey(show.feedUrl)

    private fun diskCacheFile(show: PodcastShow) =
        File(episodesDir, Integer.toHexString(show.feedUrl.hashCode()) + "_" + show.id + ".json")

    private fun writeDiskCache(show: PodcastShow, list: List<PodcastEpisode>) {
        runCatching {
            episodesDir.mkdirs()
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("title", e.title)
                        .put("audio", e.audioUrl)
                        .put("date", e.publishedAt)
                        .put("dur", e.durationSeconds)
                        .put("desc", e.description)
                        .put("img", e.imageUrl)
                )
            }
            diskCacheFile(show).writeText(arr.toString())
        }
    }

    private fun parseFeed(input: java.io.InputStream, show: PodcastShow): List<PodcastEpisode> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val out = ArrayList<PodcastEpisode>()
        var inItem = false
        var title = ""
        var audio = ""
        var guid = ""
        var pubDate = ""
        var duration = ""
        var description = ""
        var image = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < MAX_EPISODES) {
            val name = parser.name
            when (event) {
                XmlPullParser.START_TAG -> when {
                    name == "item" -> {
                        inItem = true
                        title = ""; audio = ""; guid = ""; pubDate = ""; duration = ""; description = ""; image = ""
                    }
                    !inItem -> Unit
                    name == "title" -> title = parser.safeText()
                    name == "enclosure" -> audio = parser.getAttributeValue(null, "url").orEmpty()
                    name == "guid" -> guid = parser.safeText()
                    name == "pubDate" -> pubDate = parser.safeText()
                    name == "itunes:duration" -> duration = parser.safeText()
                    name == "itunes:summary" && description.isBlank() -> description = parser.safeText()
                    name == "description" && description.isBlank() -> description = parser.safeText()
                    name == "itunes:image" -> image = parser.getAttributeValue(null, "href").orEmpty()
                }
                XmlPullParser.END_TAG -> if (name == "item") {
                    inItem = false
                    if (audio.isNotBlank()) {
                        val key = guid.ifBlank { audio }
                        out += PodcastEpisode(
                            id = "podcast:" + Integer.toHexString(key.hashCode()) + "_" + show.id,
                            showId = show.id,
                            title = title.trim().ifBlank { "Episodio" },
                            audioUrl = audio,
                            publishedAt = parseDate(pubDate),
                            durationSeconds = parseDuration(duration),
                            description = stripHtml(description).take(400),
                            imageUrl = image.ifBlank { show.artworkUrl }
                        )
                    }
                }
            }
            event = runCatching { parser.next() }.getOrElse { XmlPullParser.END_DOCUMENT }
        }
        return out
    }

    private fun XmlPullParser.safeText(): String = runCatching { nextText() }.getOrDefault("")

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z")
        for (p in patterns) {
            val t = runCatching { SimpleDateFormat(p, Locale.US).parse(raw.trim())?.time }.getOrNull()
            if (t != null) return t
        }
        return 0L
    }

    private fun parseDuration(raw: String): Long {
        val parts = raw.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0L
        return parts.fold(0L) { acc, v -> acc * 60 + v }
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

    private companion object {
        const val MAX_EPISODES = 150
    }
}

/** Convierte un episodio en algo que el reproductor sabe tocar. */
fun PodcastEpisode.toTrackItem(show: PodcastShow): TrackItem = TrackItem(
    id = id,
    title = title,
    cleanTitle = title,
    artist = show.title,
    sourceUrl = audioUrl,
    durationSeconds = durationSeconds,
    thumbnailUrl = imageUrl
)
