package com.marea.music.player

import com.marea.music.data.extractor.YouTubeExtractorRepository
import com.marea.music.data.local.MareaDao
import com.marea.music.data.metadata.TitleCleaner
import com.marea.music.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Canciones parecidas para seguir sonando cuando se acaba la cola (tipo radio).
 * Usa las relacionadas de YouTube Music y quita otras versiones de la misma canción;
 * sin internet sigue con tus canciones descargadas.
 */
class RadioQueue(
    private val extractorRepo: YouTubeExtractorRepository,
    private val dao: MareaDao
) {
    suspend fun similarTo(
        seed: TrackItem,
        alreadyQueued: List<TrackItem>,
        limit: Int = 15
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        val downloaded = runCatching { dao.observeDownloadedTracks().first().map { it.toDomain() } }
            .getOrDefault(emptyList())
        val downloadedByKey = downloaded.associateBy { key(it) }
        val queuedIds = alreadyQueued.map { it.id }.toSet()
        val queuedKeys = alreadyQueued.map { key(it) }.toSet()
        val queuedTitles = alreadyQueued.map { titleKey(it) }.toSet()
        val seedTitle = titleKey(seed)

        val sourceUrl = seed.sourceUrl.ifBlank {
            runCatching {
                extractorRepo.searchMusic("${seed.artist} ${seed.cleanTitle}", originalsOnly = true)
                    .firstOrNull()?.sourceUrl
            }.getOrNull().orEmpty()
        }
        val related = if (sourceUrl.isNotBlank()) {
            runCatching { extractorRepo.getRelatedTracks(sourceUrl) }.getOrDefault(emptyList())
        } else emptyList()

        val picked = related
            .asSequence()
            .filter { it.id !in queuedIds && key(it) !in queuedKeys }
            .filter { !isSameSong(titleKey(it), seedTitle) && titleKey(it) !in queuedTitles }
            .filter { TitleCleaner.isLikelyOriginalTrack(it.title, it.artist, "") }
            .distinctBy { titleKey(it) }
            // Si ya la tienes descargada, suena desde el teléfono.
            .map { downloadedByKey[key(it)] ?: it }
            .take(limit)
            .toList()

        picked.ifEmpty {
            // Sin internet (o sin relacionadas): tus descargas, sin repetir.
            downloaded
                .filter { it.id !in queuedIds && key(it) !in queuedKeys }
                .shuffled()
                .take(limit)
        }
    }

    private fun key(track: TrackItem): String =
        TitleCleaner.normalizeForMatching("${track.artist} ${track.cleanTitle.ifBlank { track.title }}")

    private fun titleKey(track: TrackItem): String =
        TitleCleaner.normalizeForMatching(track.cleanTitle.ifBlank { track.title })

    /** "manicero" y "el manicero (version 2)" cuentan como la misma canción. */
    private fun isSameSong(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= 4 && longer.contains(shorter)
    }
}
