package com.marea.music.data.podcast

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dónde te quedaste en cada episodio, para seguir desde ahí. */
object PodcastProgressStore {
    private const val PREFS = "marea_podcast_progress"
    private const val DONE = -1L

    private var prefs: SharedPreferences? = null

    /** id del episodio -> milisegundos escuchados (-1 = terminado). */
    private val _progress = MutableStateFlow<Map<String, Long>>(emptyMap())
    val progress: StateFlow<Map<String, Long>> = _progress.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _progress.value = p.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
    }

    /** Posición para retomar el episodio (0 si es nuevo o ya lo terminaste). */
    fun resumePositionMs(episodeId: String): Long =
        _progress.value[episodeId]?.takeIf { it > 0L } ?: 0L

    fun isFinished(episodeId: String): Boolean = _progress.value[episodeId] == DONE

    fun save(episodeId: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs >= durationMs - 30_000L
        val value = if (finished) DONE else positionMs
        if (_progress.value[episodeId] == value) return
        _progress.value = _progress.value + (episodeId to value)
        prefs?.edit()?.putLong(episodeId, value)?.apply()
    }

    fun markFinished(episodeId: String) {
        _progress.value = _progress.value + (episodeId to DONE)
        prefs?.edit()?.putLong(episodeId, DONE)?.apply()
    }
}
