package com.marea.music.data.extractor

import com.marea.music.data.metadata.TitleCleaner
import com.marea.music.domain.model.PlaylistPreview
import com.marea.music.domain.model.PlaylistResult
import com.marea.music.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

class YouTubeExtractorRepository {

    private data class CachedStreams(
        val downloadUrl: String,
        val fastStreamUrl: String,
        val relatedTracks: List<TrackItem>,
        val cachedAtMs: Long
    )

    private val streamCache = ConcurrentHashMap<String, CachedStreams>()

    suspend fun searchMusic(
        query: String,
        originalsOnly: Boolean = true
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        val ytService = ServiceList.YouTube
        val musicExtractor = ytService.getSearchExtractor(query, listOf("music_songs"), "")
        musicExtractor.fetchPage()

        val rawItems = musicExtractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()

        val sourceItems = if (rawItems.isNotEmpty()) {
            rawItems
        } else {
            val generalExtractor = ytService.getSearchExtractor(query)
            generalExtractor.fetchPage()
            generalExtractor.initialPage.items.filterIsInstance<StreamInfoItem>()
        }

        val mapped = sourceItems.map { item ->
            val rawUploader = item.uploaderName.orEmpty()
            val isOfficialTopic = rawUploader.contains("- Topic", ignoreCase = true) ||
                rawUploader.contains("VEVO", ignoreCase = true)
            mapStreamInfoItem(item) to isOfficialTopic
        }

        val filtered = if (originalsOnly) {
            val onlyOriginals = mapped.filter { (track, _) ->
                TitleCleaner.isLikelyOriginalTrack(
                    rawTitle = track.title,
                    rawArtist = track.artist,
                    userQuery = query
                )
            }
            onlyOriginals.ifEmpty { mapped }
        } else {
            mapped
        }

        rankByQueryRelevance(query, filtered)
    }

    /** Playlists de YouTube Music que coinciden con la búsqueda (si no hay, playlists normales). */
    suspend fun searchPlaylists(query: String, limit: Int = 12): List<PlaylistResult> = withContext(Dispatchers.IO) {
        val ytService = ServiceList.YouTube
        fun fetch(filter: String): List<PlaylistInfoItem> = runCatching {
            val extractor = ytService.getSearchExtractor(query, listOf(filter), "")
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>()
        }.getOrDefault(emptyList())

        val items = fetch("music_playlists").ifEmpty { fetch("playlists") }
        items.mapNotNull { item ->
            val listId = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(item.url.orEmpty())
                ?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            PlaylistResult(
                title = item.name.orEmpty().ifBlank { "Playlist" },
                author = TitleCleaner.cleanArtist(item.uploaderName.orEmpty()),
                url = "https://www.youtube.com/playlist?list=$listId",
                thumbnailUrl = pickBestThumbnail(
                    item.thumbnails.maxByOrNull { it.width * it.height }?.url.orEmpty()
                ),
                trackCount = item.streamCount
            )
        }.distinctBy { it.url }.take(limit)
    }

    suspend fun getTrackInfo(videoUrl: String): TrackItem = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val rawTitle = info.name.orEmpty()
        val rawArtist = info.uploaderName.orEmpty()
        val (artist, cleanTitle) = TitleCleaner.extractArtistAndTrack(rawTitle, rawArtist)
        val bestThumb = pickBestThumbnail(info.thumbnails.maxByOrNull { it.width * it.height }?.url.orEmpty())
        val canonicalUrl = info.url ?: videoUrl

        cacheFromStreamInfo(canonicalUrl, info)

        TrackItem(
            id = extractVideoId(canonicalUrl),
            title = rawTitle,
            cleanTitle = cleanTitle,
            artist = artist,
            durationSeconds = info.duration,
            thumbnailUrl = bestThumb,
            sourceUrl = canonicalUrl
        )
    }

    /**
     * Resolves the highest-bitrate M4A audio URL (used by DownloadWorker).
     */
    suspend fun resolveBestM4aAudioUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        val cached = streamCache[videoUrl]
        if (cached != null && System.currentTimeMillis() - cached.cachedAtMs < STREAM_CACHE_TTL_MS) {
            return@withContext cached.downloadUrl
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        cacheFromStreamInfo(videoUrl, info).downloadUrl
    }

    /**
     * Resolves a lightweight, fast-buffering audio URL (96-160 kbps M4A/Opus preferred)
     * for instant streaming playback of non-downloaded songs.
     */
    suspend fun resolveFastStreamingAudioUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        val cached = streamCache[videoUrl]
        if (cached != null && System.currentTimeMillis() - cached.cachedAtMs < STREAM_CACHE_TTL_MS) {
            return@withContext cached.fastStreamUrl
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        cacheFromStreamInfo(videoUrl, info).fastStreamUrl
    }

    suspend fun getRelatedTracks(videoUrl: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val cached = streamCache[videoUrl]
        if (cached != null && System.currentTimeMillis() - cached.cachedAtMs < STREAM_CACHE_TTL_MS) {
            return@withContext cached.relatedTracks
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        cacheFromStreamInfo(videoUrl, info).relatedTracks
    }

    suspend fun prefetchStreamUrl(videoUrl: String) {
        if (videoUrl.isBlank()) return
        val cached = streamCache[videoUrl]
        if (cached != null && System.currentTimeMillis() - cached.cachedAtMs < STREAM_CACHE_TTL_MS) {
            return
        }
        runCatching { resolveFastStreamingAudioUrl(videoUrl) }
    }

    suspend fun extractPlaylist(playlistUrl: String, maxTracks: Int = 200): PlaylistPreview =
        withContext(Dispatchers.IO) {
            val ytService = ServiceList.YouTube
            val info = PlaylistInfo.getInfo(ytService, playlistUrl)

            val collected = mutableListOf<StreamInfoItem>()
            collected.addAll(info.relatedItems.filterIsInstance<StreamInfoItem>())

            var nextPage = info.nextPage
            while (nextPage != null && collected.size < maxTracks) {
                val page = PlaylistInfo.getMoreItems(ytService, playlistUrl, nextPage)
                collected.addAll(page.items.filterIsInstance<StreamInfoItem>())
                nextPage = page.nextPage
            }

            val tracks = collected
                .map { mapStreamInfoItem(it) }
                .filter { it.id.isNotBlank() }
                .distinctBy { it.id }
                .distinctBy {
                    TitleCleaner.normalizeForMatching(it.artist) + "|" +
                        TitleCleaner.normalizeForMatching(it.cleanTitle.ifBlank { it.title })
                }
                .take(maxTracks)
            val thumb = pickBestThumbnail(
                info.thumbnails.maxByOrNull { it.width * it.height }?.url
                    ?: tracks.firstOrNull()?.thumbnailUrl.orEmpty()
            )

            PlaylistPreview(
                id = info.id.orEmpty().ifBlank { playlistUrl.hashCode().toString() },
                title = info.name.orEmpty().ifBlank { "Playlist de YouTube" },
                author = TitleCleaner.cleanArtist(info.uploaderName.orEmpty()),
                thumbnailUrl = thumb,
                sourceUrl = playlistUrl,
                tracks = tracks
            )
        }

    private fun cacheFromStreamInfo(videoUrl: String, info: StreamInfo): CachedStreams {
        val downloadUrl = selectBestM4aUrl(info.audioStreams)
            ?: throw IllegalStateException("No se encontraron streams de audio para $videoUrl")
        val fastUrl = selectFastStreamingUrl(info.audioStreams) ?: downloadUrl
        val related = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 45..600 }
            .map { mapStreamInfoItem(it) }

        val entry = CachedStreams(
            downloadUrl = downloadUrl,
            fastStreamUrl = fastUrl,
            relatedTracks = related,
            cachedAtMs = System.currentTimeMillis()
        )
        streamCache[videoUrl] = entry
        info.url?.let { streamCache[it] = entry }
        return entry
    }

    private fun selectBestM4aUrl(audioStreams: List<AudioStream>): String? {
        val directStreams = audioStreams.filter { it.isUrl }
        val pool = directStreams.ifEmpty { audioStreams }
        return pool
            .filter { it.format == MediaFormat.M4A }
            .maxByOrNull { it.averageBitrate }
            ?.content
            ?: pool.maxByOrNull { it.averageBitrate }?.content
    }

    private fun selectFastStreamingUrl(audioStreams: List<AudioStream>): String? {
        val directStreams = audioStreams.filter { it.isUrl }
        val pool = directStreams.ifEmpty { audioStreams }
        // Prefer 96-160 kbps streams so initial buffering completes almost instantaneously
        val balanced = pool.filter { it.averageBitrate in 90..165 }
        return balanced.firstOrNull { it.format == MediaFormat.M4A }?.content
            ?: balanced.maxByOrNull { it.averageBitrate }?.content
            ?: pool.filter { it.format == MediaFormat.M4A }.minByOrNull { it.averageBitrate }?.content
            ?: pool.minByOrNull { it.averageBitrate }?.content
    }

    private fun rankByQueryRelevance(
        query: String,
        items: List<Pair<TrackItem, Boolean>>
    ): List<TrackItem> {
        val normalizedQuery = TitleCleaner.normalizeForMatching(query)
        if (normalizedQuery.isBlank()) return items.map { it.first }

        return items
            .mapIndexed { originalIndex, (track, isOfficialTopic) ->
                val combined = "${track.cleanTitle} ${track.artist} ${track.title}"
                val overlap = TitleCleaner.tokenOverlapScore(normalizedQuery, combined)
                val exactPhraseBonus =
                    if (TitleCleaner.normalizeForMatching(combined).contains(normalizedQuery)) 0.35 else 0.0
                val officialBonus = if (isOfficialTopic) 0.15 else 0.0
                val score = overlap + exactPhraseBonus + officialBonus
                Triple(track, score, originalIndex)
            }
            .sortedWith(
                compareByDescending<Triple<TrackItem, Double, Int>> { (it.second * 10).toInt() }
                    .thenBy { it.third }
            )
            .map { it.first }
    }

    private fun mapStreamInfoItem(item: StreamInfoItem): TrackItem {
        val rawTitle = item.name.orEmpty()
        val rawArtist = item.uploaderName.orEmpty()
        val (artist, cleanTitle) = TitleCleaner.extractArtistAndTrack(rawTitle, rawArtist)
        val rawThumb = item.thumbnails.maxByOrNull { it.width * it.height }?.url.orEmpty()
        val thumb = pickBestThumbnail(rawThumb)
        val url = item.url.orEmpty()

        return TrackItem(
            id = extractVideoId(url),
            title = rawTitle,
            cleanTitle = cleanTitle,
            artist = artist,
            durationSeconds = item.duration,
            thumbnailUrl = thumb,
            sourceUrl = url
        )
    }

    private fun pickBestThumbnail(url: String): String {
        if (url.contains("lh3.googleusercontent.com") || url.contains("yt3.ggpht.com")) {
            return url.replace(Regex("""=w\d+-h\d+"""), "=w544-h544")
        }
        return url
    }

    private fun extractVideoId(url: String): String {
        val vRegex = Regex("""[?&]v=([a-zA-Z0-9_-]{6,})""")
        vRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        val shortRegex = Regex("""youtu\.be/([a-zA-Z0-9_-]{6,})""")
        shortRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        return url.hashCode().toString()
    }

    companion object {
        private const val STREAM_CACHE_TTL_MS = 15 * 60 * 1000L
    }
}
