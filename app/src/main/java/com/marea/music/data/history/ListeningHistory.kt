package com.marea.music.data.history

import android.content.Context
import com.marea.music.data.metadata.TitleCleaner
import com.marea.music.domain.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Una canción escuchada y cuántas veces. */
data class PlayedTrack(
    val track: TrackItem,
    val plays: Int,
    val lastPlayed: Long
)

/** Un artista escuchado, con la portada de su canción más escuchada. */
data class TopArtist(
    val name: String,
    val coverUrl: String,
    val plays: Int
)

/**
 * Historial de escucha guardado en el teléfono (archivo JSON), base de "Tu top" y de las
 * canciones de los artistas que más escuchas.
 */
class ListeningHistory(context: Context) {
    private val file = File(context.filesDir, "listening_history.json")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<PlayedTrack>>(emptyList())
    val entries: StateFlow<List<PlayedTrack>> = _entries.asStateFlow()

    init {
        io.launch { _entries.value = load() }
    }

    fun record(track: TrackItem) {
        if (track.id.isBlank() || track.cleanTitle.isBlank() && track.title.isBlank()) return
        val now = System.currentTimeMillis()
        _entries.update { list ->
            val existing = list.firstOrNull { it.track.id == track.id }
            val updated = if (existing != null) {
                existing.copy(track = track, plays = existing.plays + 1, lastPlayed = now)
            } else {
                PlayedTrack(track = track, plays = 1, lastPlayed = now)
            }
            (listOf(updated) + list.filter { it.track.id != track.id }).take(MAX_ENTRIES)
        }
        val snapshot = _entries.value
        io.launch { save(snapshot) }
    }

    /** Las canciones más escuchadas (empate: la más reciente primero). */
    fun topTracks(limit: Int = 10): List<PlayedTrack> =
        _entries.value.sortedWith(compareByDescending<PlayedTrack> { it.plays }.thenByDescending { it.lastPlayed })
            .take(limit)

    fun topArtists(limit: Int = 10): List<TopArtist> =
        topArtistsOf(_entries.value, limit)

    private fun load(): List<PlayedTrack> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            if (id.isBlank()) return@mapNotNull null
            PlayedTrack(
                track = TrackItem(
                    id = id,
                    title = o.optString("title"),
                    cleanTitle = o.optString("cleanTitle"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    durationSeconds = o.optLong("duration"),
                    thumbnailUrl = o.optString("thumb"),
                    hdCoverUrl = o.optString("cover"),
                    sourceUrl = o.optString("source")
                ),
                plays = o.optInt("plays", 1),
                lastPlayed = o.optLong("last")
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<PlayedTrack>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.track.id)
                        .put("title", p.track.title)
                        .put("cleanTitle", p.track.cleanTitle)
                        .put("artist", p.track.artist)
                        .put("album", p.track.album)
                        .put("duration", p.track.durationSeconds)
                        .put("thumb", p.track.thumbnailUrl)
                        .put("cover", p.track.hdCoverUrl)
                        .put("source", p.track.sourceUrl)
                        .put("plays", p.plays)
                        .put("last", p.lastPlayed)
                )
            }
            val tmp = File(file.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val MAX_ENTRIES = 400

        fun topArtistsOf(entries: List<PlayedTrack>, limit: Int = 10): List<TopArtist> =
            entries
                .filter { it.track.artist.isNotBlank() }
                .groupBy { TitleCleaner.extractPrimaryArtist(it.track.artist).ifBlank { it.track.artist } }
                .map { (name, plays) ->
                    TopArtist(
                        name = name,
                        coverUrl = plays.maxByOrNull { it.plays }?.track?.bestCoverUrl.orEmpty(),
                        plays = plays.sumOf { it.plays }
                    )
                }
                .filter { !it.name.equals("Artista desconocido", ignoreCase = true) }
                .sortedByDescending { it.plays }
                .take(limit)
    }
}
