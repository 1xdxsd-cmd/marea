package com.marea.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marea.music.AppContainer
import com.marea.music.data.extractor.ParsedYouTubeInput
import com.marea.music.data.history.ListeningHistory
import com.marea.music.data.history.TopArtist
import com.marea.music.data.extractor.YouTubeUrlParser
import com.marea.music.data.local.PlaylistEntity
import com.marea.music.data.local.PlaylistTrackCrossRef
import com.marea.music.data.local.TrackEntity
import com.marea.music.data.metadata.TitleCleaner
import com.marea.music.domain.model.DownloadStatus
import com.marea.music.domain.model.LocalPlaylist
import com.marea.music.domain.model.LyricsResult
import com.marea.music.domain.model.PlaylistPreview
import com.marea.music.domain.model.PlaylistResult
import com.marea.music.domain.model.TrackItem
import com.marea.music.player.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val originalsOnly: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val results: List<TrackItem> = emptyList(),
    /** Playlists de YouTube que coinciden con la búsqueda. */
    val playlists: List<PlaylistResult> = emptyList(),
    val playlistPreview: PlaylistPreview? = null,
    val selectedPlaylistIds: Set<String> = emptySet(),
    val infoMessage: String? = null
)

data class ArtistUiState(
    val artistName: String,
    val downloadedTracks: List<TrackItem> = emptyList(),
    val recommendedTracks: List<TrackItem> = emptyList(),
    val isLoadingRecommendations: Boolean = false
)

class MareaViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val dao = container.database.mareaDao()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsResult?>(null)
    val lyricsState: StateFlow<LyricsResult?> = _lyricsState.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _rawRecommendations = MutableStateFlow<List<TrackItem>>(emptyList())
    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    private val _selectedArtistName = MutableStateFlow<String?>(null)
    private val _rawArtistRecs = MutableStateFlow<List<TrackItem>>(emptyList())
    private val _isLoadingArtistRecs = MutableStateFlow(false)

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private var searchJob: Job? = null
    private var enrichJob: Job? = null
    private var recommendJob: Job? = null
    private var artistJob: Job? = null
    private var lyricsJob: Job? = null
    private var artistMixJob: Job? = null
    private var searchGeneration = 0L

    val playbackState: StateFlow<PlaybackUiState> = container.playerController.uiState

    // ---- Tu top: lo que más has escuchado y canciones de esos artistas ----

    val topArtists: StateFlow<List<TopArtist>> = container.listeningHistory.entries
        .map { ListeningHistory.topArtistsOf(it, limit = 12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _artistMix = MutableStateFlow<List<TrackItem>>(emptyList())
    private val _isLoadingArtistMix = MutableStateFlow(false)
    val isLoadingArtistMix: StateFlow<Boolean> = _isLoadingArtistMix.asStateFlow()

    val allSavedTracksMap: StateFlow<Map<String, TrackItem>> = dao.observeAllTracks()
        .map { list -> list.associate { it.id to it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Canciones de una lista ya resueltas a su id de YouTube (id original -> id descargado). */
    private val _resolvedIds = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Canciones a las que se les tocó descargar y aún buscan su fuente. */
    private val _resolvingIds = MutableStateFlow<Set<String>>(emptySet())

    /** Todo lo necesario para saber el estado de descarga de cualquier canción en cualquier lista. */
    private class SavedIndex(
        val byId: Map<String, TrackItem>,
        val byNormalized: Map<String, TrackItem>,
        val aliases: Map<String, String>,
        val resolving: Set<String>
    ) {
        fun match(track: TrackItem): TrackItem? =
            byId[track.id]?.takeIf { it.downloadStatus != DownloadStatus.NONE }
                ?: aliases[track.id]?.let { byId[it] }
                ?: byNormalized[normalizedKey(track)]
                ?: byId[track.id]

        /** La canción con el estado de descarga real (en cola, descargando, descargada...). */
        fun sync(track: TrackItem): TrackItem {
            val saved = match(track)
            if (saved == null || saved.downloadStatus == DownloadStatus.NONE) {
                return if (track.id in resolving) {
                    track.copy(downloadStatus = DownloadStatus.QUEUED, downloadProgress = 0)
                } else track
            }
            return track.copy(
                downloadStatus = saved.downloadStatus,
                downloadProgress = saved.downloadProgress,
                localUri = saved.localUri,
                localFilePath = saved.localFilePath,
                hdCoverUrl = track.hdCoverUrl.ifBlank { saved.hdCoverUrl }
            )
        }
    }

    private val savedIndex: StateFlow<SavedIndex> = combine(
        allSavedTracksMap,
        _resolvedIds,
        _resolvingIds
    ) { map, aliases, resolving ->
        SavedIndex(
            byId = map,
            byNormalized = map.values
                .filter { it.downloadStatus != DownloadStatus.NONE }
                .associateBy { normalizedKey(it) },
            aliases = aliases,
            resolving = resolving
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SavedIndex(emptyMap(), emptyMap(), emptyMap(), emptySet()))

    val downloadedTracks: StateFlow<List<TrackItem>> = dao.observeDownloadedTracks()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeDownloads: StateFlow<List<TrackItem>> = dao.observeActiveAndFailedDownloads()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localPlaylists: StateFlow<List<LocalPlaylist>> = dao.observePlaylistsWithTracks()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tus canciones más escuchadas, con el estado de descarga al día. */
    val topTracks: StateFlow<List<TrackItem>> = combine(
        container.listeningHistory.entries,
        savedIndex
    ) { entries, index ->
        entries
            .sortedWith(compareByDescending<com.marea.music.data.history.PlayedTrack> { it.plays }.thenByDescending { it.lastPlayed })
            .take(10)
            .map { index.sync(it.track) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Canciones nuevas de los artistas que más escuchas. */
    val artistMix: StateFlow<List<TrackItem>> = combine(_artistMix, savedIndex) { mix, index ->
        mix.map { index.sync(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Syncs recommendation download status with Room (matching by ID or normalized artist+title).
     */
    val recommendations: StateFlow<List<TrackItem>> = combine(
        _rawRecommendations,
        savedIndex
    ) { recs, index ->
        recs.map { rec -> index.syncKeepingSource(rec) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artistState: StateFlow<ArtistUiState?> = combine(
        _selectedArtistName,
        downloadedTracks,
        _rawArtistRecs,
        _isLoadingArtistRecs,
        savedIndex
    ) { artistName, downloaded, rawRecs, loadingRecs, index ->
        if (artistName.isNullOrBlank()) return@combine null

        val downloadedOfArtist = downloaded.filter { track ->
            TitleCleaner.isExplicitArtistMatch(
                targetArtist = artistName,
                candidateArtist = track.artist,
                candidateTitle = track.title
            )
        }
        val syncedRecs = rawRecs.map { rec -> index.syncKeepingSource(rec) }

        ArtistUiState(
            artistName = artistName,
            downloadedTracks = downloadedOfArtist,
            recommendedTracks = syncedRecs,
            isLoadingRecommendations = loadingRecs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // La letra sigue a la canción que suena, también con siguiente/anterior y audífonos.
        viewModelScope.launch {
            container.playerController.uiState
                .map { state -> state.currentTrack?.takeIf { !state.isAd } }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collect { track -> loadLyricsFor(track) }
        }
        // "De tus artistas" se actualiza cuando cambian tus artistas más escuchados.
        viewModelScope.launch {
            topArtists
                .map { list -> list.take(3).map { it.name } }
                .distinctUntilChanged()
                .collect { refreshArtistMix() }
        }
    }

    init {
        viewModelScope.launch {
            downloadedTracks
                .map { list -> list.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collect {
                    if (_rawRecommendations.value.isEmpty() || downloadedTracks.value.isNotEmpty()) {
                        refreshRecommendations()
                    }
                }
        }
    }

    /** Mezcla canciones de tus 3 artistas más escuchados que todavía no están en tu top. */
    fun refreshArtistMix() {
        val artists = topArtists.value.take(3).map { it.name }
        if (artists.isEmpty()) {
            _artistMix.value = emptyList()
            return
        }
        artistMixJob?.cancel()
        artistMixJob = viewModelScope.launch {
            _isLoadingArtistMix.value = true
            val known = container.listeningHistory.entries.value.map { it.track } + downloadedTracks.value
            val perArtist = artists.map { name ->
                async {
                    runCatching {
                        container.recommendationEngine.recommendStrictForArtist(
                            rawArtist = name,
                            downloadedTracks = known,
                            limit = 8
                        )
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
            // Intercala: una de cada artista por turno.
            val mixed = mutableListOf<TrackItem>()
            val maxLen = perArtist.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxLen) perArtist.forEach { list -> list.getOrNull(i)?.let(mixed::add) }
            _artistMix.value = mixed.distinctBy { it.id }.take(18)
            _isLoadingArtistMix.value = false
        }
    }

    /** Reproduce las canciones de un artista: primero las descargadas y luego las más populares. */
    fun playArtist(rawArtist: String) {
        val primary = TitleCleaner.extractPrimaryArtist(rawArtist).ifBlank { rawArtist }
        if (primary.isBlank()) return
        viewModelScope.launch {
            _uiMessage.value = "Preparando canciones de $primary…"
            val downloaded = downloadedTracks.value.filter { track ->
                TitleCleaner.isExplicitArtistMatch(
                    targetArtist = primary,
                    candidateArtist = track.artist,
                    candidateTitle = track.title
                )
            }
            val popular = runCatching {
                container.recommendationEngine.recommendStrictForArtist(
                    rawArtist = primary,
                    downloadedTracks = downloaded,
                    limit = 25
                )
            }.getOrDefault(emptyList())
            val queue = (downloaded.shuffled() + popular).distinctBy { it.id }
            if (queue.isEmpty()) {
                _uiMessage.value = "No encontramos canciones de $primary"
                return@launch
            }
            playTrack(queue.first(), queue)
        }
    }

    fun refreshRecommendations() {
        recommendJob?.cancel()
        recommendJob = viewModelScope.launch {
            _isLoadingRecommendations.value = true
            // Tu biblioteca más lo que más escuchas: así las recomendaciones siguen tus gustos.
            val seeds = (downloadedTracks.value + container.listeningHistory.topTracks(12).map { it.track })
                .distinctBy { it.id }
            val recs = container.recommendationEngine.recommendForLibrary(
                downloadedTracks = seeds,
                limit = 16
            )
            _rawRecommendations.value = recs
            _isLoadingRecommendations.value = false
            prefetchRecommendationsAsync(recs.take(4))
        }
    }

    private fun prefetchRecommendationsAsync(tracks: List<TrackItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            tracks.map { track ->
                async {
                    val resolved = resolveTrackIfNeeded(track) ?: return@async
                    if (resolved.id != track.id || resolved.sourceUrl != track.sourceUrl) {
                        _rawRecommendations.update { list ->
                            list.map { if (it.id == track.id) resolved else it }
                        }
                    }
                    container.extractorRepo.prefetchStreamUrl(resolved.sourceUrl)
                }
            }.awaitAll()
        }
    }

    val mergedSearchState: StateFlow<SearchUiState> = combine(
        _searchState,
        savedIndex
    ) { state, index ->
        val mergedResults = state.results.map { track -> index.sync(track) }
        val mergedPlaylist = state.playlistPreview?.let { pl ->
            pl.copy(tracks = pl.tracks.map { track -> index.sync(track) })
        }
        state.copy(results = mergedResults, playlistPreview = mergedPlaylist)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun updateQuery(newQuery: String) {
        _searchState.update { it.copy(query = newQuery, error = null, infoMessage = null) }
    }

    fun setOriginalsOnly(originalsOnly: Boolean) {
        _searchState.update { it.copy(originalsOnly = originalsOnly) }
        if (_searchState.value.query.isNotBlank() && _searchState.value.playlistPreview == null) {
            submitSearchOrLink(_searchState.value.query)
        }
    }

    fun submitSearchOrLink(input: String = _searchState.value.query) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        searchJob?.cancel()
        enrichJob?.cancel()
        val generation = ++searchGeneration
        val originalsOnly = _searchState.value.originalsOnly

        _searchState.update {
            it.copy(
                query = trimmed,
                isLoading = true,
                error = null,
                infoMessage = null
            )
        }

        searchJob = viewModelScope.launch {
            try {
                when (val parsed = YouTubeUrlParser.parse(trimmed)) {
                    is ParsedYouTubeInput.PlaylistUrl -> {
                        val preview = container.extractorRepo.extractPlaylist(parsed.url)
                        if (generation != searchGeneration) return@launch
                        val savedMap = allSavedTracksMap.value
                        val selectableIds = preview.tracks
                            .filter { savedMap[it.id]?.isDownloaded != true }
                            .map { it.id }
                            .toSet()
                        _searchState.update {
                            it.copy(
                                isLoading = false,
                                playlistPreview = preview,
                                results = emptyList(),
                                playlists = emptyList(),
                                selectedPlaylistIds = selectableIds
                            )
                        }
                        enrichTopCoversAsync(preview.tracks.take(10), isPlaylist = true, generation = generation)
                    }

                    is ParsedYouTubeInput.VideoUrl -> {
                        val track = container.extractorRepo.getTrackInfo(parsed.url)
                        if (generation != searchGeneration) return@launch
                        _searchState.update {
                            it.copy(
                                isLoading = false,
                                results = listOf(track),
                                playlists = emptyList(),
                                playlistPreview = null
                            )
                        }
                        enrichTopCoversAsync(listOf(track), isPlaylist = false, generation = generation)
                    }

                    is ParsedYouTubeInput.SearchQuery -> {
                        val playlistsAsync = async {
                            runCatching { container.extractorRepo.searchPlaylists(parsed.query) }
                                .getOrDefault(emptyList())
                        }
                        val results = container.extractorRepo.searchMusic(
                            query = parsed.query,
                            originalsOnly = originalsOnly
                        )
                        if (generation != searchGeneration) return@launch
                        _searchState.update {
                            it.copy(
                                isLoading = false,
                                results = results,
                                playlists = emptyList(),
                                playlistPreview = null
                            )
                        }
                        launch {
                            val found = playlistsAsync.await()
                            if (generation == searchGeneration) {
                                _searchState.update { it.copy(playlists = found) }
                            }
                        }
                        enrichTopCoversAsync(results.take(8), isPlaylist = false, generation = generation)
                        prefetchTopSearchStreamsAsync(results.take(3))
                    }
                }
            } catch (e: Exception) {
                if (generation != searchGeneration) return@launch
                _searchState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al buscar o analizar el enlace"
                    )
                }
            }
        }
    }

    private fun prefetchTopSearchStreamsAsync(tracks: List<TrackItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            tracks.forEach { track ->
                if (track.sourceUrl.isNotBlank()) {
                    launch { container.extractorRepo.prefetchStreamUrl(track.sourceUrl) }
                }
            }
        }
    }

    private fun enrichTopCoversAsync(
        tracks: List<TrackItem>,
        isPlaylist: Boolean,
        generation: Long
    ) {
        enrichJob = viewModelScope.launch {
            val enrichedPairs = tracks.map { track ->
                async {
                    val meta = container.metadataEnricher.enrich(
                        rawTitle = track.cleanTitle.ifBlank { track.title },
                        rawArtist = track.artist
                    )
                    track.id to meta
                }
            }.awaitAll().toMap()

            if (generation != searchGeneration) return@launch

            _searchState.update { state ->
                if (isPlaylist) {
                    val updatedPl = state.playlistPreview?.let { pl ->
                        pl.copy(
                            tracks = pl.tracks.map { t ->
                                val meta = enrichedPairs[t.id]
                                if (meta != null && meta.coverUrl.isNotBlank()) {
                                    t.copy(
                                        hdCoverUrl = meta.coverUrl,
                                        album = meta.album.ifBlank { t.album }
                                    )
                                } else t
                            }
                        )
                    }
                    state.copy(playlistPreview = updatedPl)
                } else {
                    state.copy(
                        results = state.results.map { t ->
                            val meta = enrichedPairs[t.id]
                            if (meta != null && meta.coverUrl.isNotBlank()) {
                                t.copy(
                                    hdCoverUrl = meta.coverUrl,
                                    album = meta.album.ifBlank { t.album }
                                )
                            } else t
                        }
                    )
                }
            }
        }
    }

    fun togglePlaylistSelection(trackId: String) {
        if (allSavedTracksMap.value[trackId]?.isDownloaded == true) return
        _searchState.update { state ->
            val current = state.selectedPlaylistIds
            val next = if (trackId in current) current - trackId else current + trackId
            state.copy(selectedPlaylistIds = next)
        }
    }

    fun selectAllPlaylistTracks(selectAll: Boolean) {
        val savedMap = allSavedTracksMap.value
        _searchState.update { state ->
            val allIds = if (selectAll) {
                state.playlistPreview?.tracks
                    ?.filter { savedMap[it.id]?.isDownloaded != true }
                    ?.map { it.id }
                    ?.toSet()
                    .orEmpty()
            } else emptySet()
            state.copy(selectedPlaylistIds = allIds)
        }
    }

    /** Las descargas son solo para Premium; en el plan gratis se avisa y no se descarga. */
    private fun canDownload(): Boolean {
        if (container.playerController.uiState.value.isPremium) return true
        _uiMessage.value = PREMIUM_DOWNLOAD_MESSAGE
        return false
    }

    fun downloadSingleTrack(track: TrackItem) {
        val current = savedIndex.value.sync(track)
        if (current.downloadStatus == DownloadStatus.COMPLETED ||
            current.downloadStatus == DownloadStatus.QUEUED ||
            current.downloadStatus == DownloadStatus.DOWNLOADING ||
            current.downloadStatus == DownloadStatus.TAGGING
        ) {
            return
        }
        if (!canDownload()) return

        // La animación de descarga aparece al instante, aunque todavía se busque la fuente.
        _resolvingIds.update { it + current.id }
        viewModelScope.launch {
          try {
            val resolved = if (current.sourceUrl.isBlank()) {
                val ytMatch = runCatching {
                    container.extractorRepo.searchMusic(
                        query = "${current.artist} ${current.cleanTitle}",
                        originalsOnly = true
                    ).firstOrNull()
                }.getOrNull()

                if (ytMatch == null) {
                    _uiMessage.value = "No se encontró fuente descargable para ${current.cleanTitle}"
                    return@launch
                }

                val merged = ytMatch.copy(
                    cleanTitle = current.cleanTitle,
                    artist = current.artist,
                    album = current.album,
                    hdCoverUrl = current.bestCoverUrl
                )
                // Update recommendation entry with resolved YouTube ID so progress shows on card
                _rawRecommendations.update { list ->
                    list.map { if (it.id == current.id) merged else it }
                }
                merged
            } else {
                current
            }

            if (resolved.id != current.id) {
                _resolvedIds.update { it + (current.id to resolved.id) }
            }
            if ((allSavedTracksMap.value[resolved.id]?.downloadStatus) == DownloadStatus.COMPLETED) {
                return@launch
            }
            container.downloadOrchestrator.enqueueSingleTrack(resolved)
          } finally {
            _resolvingIds.update { it - current.id }
          }
        }
    }

    fun downloadSelectedPlaylist() {
        val state = _searchState.value
        val preview = state.playlistPreview ?: return
        if (!canDownload()) return
        val savedMap = allSavedTracksMap.value
        val selected = preview.tracks.filter {
            it.id in state.selectedPlaylistIds && savedMap[it.id]?.isDownloaded != true
        }
        if (selected.isEmpty()) {
            _searchState.update {
                it.copy(infoMessage = "Las canciones seleccionadas ya están descargadas")
            }
            return
        }

        viewModelScope.launch {
            container.downloadOrchestrator.enqueuePlaylist(preview, selected)
            _searchState.update {
                it.copy(infoMessage = "Cola iniciada: ${selected.size} canciones en descarga")
            }
        }
    }

    /** Abre una playlist encontrada en la búsqueda; al cerrarla vuelves a los resultados. */
    fun openSearchPlaylist(playlist: PlaylistResult) {
        searchJob?.cancel()
        enrichJob?.cancel()
        val generation = ++searchGeneration
        _searchState.update { it.copy(isLoading = true, error = null, infoMessage = null) }
        searchJob = viewModelScope.launch {
            try {
                val preview = container.extractorRepo.extractPlaylist(playlist.url)
                if (generation != searchGeneration) return@launch
                val savedMap = allSavedTracksMap.value
                val selectableIds = preview.tracks
                    .filter { savedMap[it.id]?.isDownloaded != true }
                    .map { it.id }
                    .toSet()
                _searchState.update {
                    it.copy(
                        isLoading = false,
                        playlistPreview = preview,
                        selectedPlaylistIds = selectableIds
                    )
                }
                enrichTopCoversAsync(preview.tracks.take(10), isPlaylist = true, generation = generation)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != searchGeneration) return@launch
                _searchState.update {
                    it.copy(isLoading = false, error = e.message ?: "No se pudo abrir la playlist")
                }
            }
        }
    }

    fun clearPlaylistPreview() {
        _searchState.update { it.copy(playlistPreview = null, infoMessage = null) }
    }

    fun cancelOrRemoveQueuedDownload(trackId: String) {
        viewModelScope.launch {
            container.downloadOrchestrator.cancelOrRemoveTrack(trackId)
        }
    }

    fun openArtistPage(rawArtist: String) {
        val primary = TitleCleaner.extractPrimaryArtist(rawArtist)
        if (primary.isBlank()) return
        _selectedArtistName.value = primary
        _rawArtistRecs.value = emptyList()
        artistJob?.cancel()
        artistJob = viewModelScope.launch {
            _isLoadingArtistRecs.value = true
            val recs = runCatching {
                container.recommendationEngine.recommendStrictForArtist(
                    rawArtist = primary,
                    downloadedTracks = downloadedTracks.value,
                    limit = 20
                )
            }.getOrDefault(emptyList())
            _rawArtistRecs.value = recs
            _isLoadingArtistRecs.value = false
        }
    }

    fun closeArtistPage() {
        artistJob?.cancel()
        _selectedArtistName.value = null
        _rawArtistRecs.value = emptyList()
    }

    // --- Custom Playlist Management ---

    fun createPlaylist(name: String, initialTrack: TrackItem? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = dao.insertPlaylist(
                PlaylistEntity(
                    name = trimmed,
                    coverUrl = initialTrack?.bestCoverUrl.orEmpty(),
                    sourceUrl = null
                )
            )
            if (initialTrack != null) {
                val trackToSave = resolveTrackIfNeeded(initialTrack) ?: initialTrack
                dao.insertTrackIgnore(TrackEntity.fromDomain(trackToSave))
                dao.insertPlaylistRefs(
                    listOf(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackToSave.id, position = 0))
                )
            }
            _uiMessage.value = "Playlist \"$trimmed\" creada"
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: TrackItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val trackToSave = resolveTrackIfNeeded(track) ?: track
            dao.insertTrackIgnore(TrackEntity.fromDomain(trackToSave))
            dao.insertPlaylistRefs(
                listOf(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackToSave.id, position = 0))
            )
            if (trackToSave.bestCoverUrl.isNotBlank()) {
                dao.updatePlaylistCoverIfEmpty(playlistId, trackToSave.bestCoverUrl)
            }
            _uiMessage.value = "Agregada a la playlist"
        }
    }

    private suspend fun resolveTrackIfNeeded(track: TrackItem): TrackItem? {
        if (track.sourceUrl.isNotBlank()) return track
        val found = runCatching {
            container.extractorRepo.searchMusic("${track.artist} ${track.cleanTitle}", originalsOnly = true)
                .firstOrNull()
        }.getOrNull() ?: return null
        return found.copy(
            cleanTitle = track.cleanTitle,
            artist = track.artist,
            album = track.album,
            hdCoverUrl = track.bestCoverUrl
        )
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPlaylistRefs(playlistId)
            dao.deletePlaylist(playlistId)
        }
    }

    fun deleteDownloadedTrack(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeTrackFromAllPlaylists(trackId)
            dao.deleteTrack(trackId)
        }
    }

    // --- Playback Controls (Sequential / Shuffle / Repeat) ---

    fun playTrack(track: TrackItem, queue: List<TrackItem> = listOf(track)) {
        // Usa el archivo descargado aunque la canción venga con otro id (búsqueda, radio...).
        val index = savedIndex.value
        val saved = allSavedTracksMap.value
        val synced = index.sync(saved[track.id] ?: track)
        val syncedQueue = queue.map { index.sync(saved[it.id] ?: it) }
        container.playerController.playTrack(synced, syncedQueue)
    }

    fun togglePlayPause() = container.playerController.togglePlayPause()
    fun playNext() = container.playerController.playNext()
    fun playPrevious() = container.playerController.playPrevious()
    fun seekTo(posMs: Long) = container.playerController.seekTo(posMs)
    fun toggleShuffle() = container.playerController.toggleShuffle()
    fun cycleRepeatMode() = container.playerController.cycleRepeatMode()

    /** Carga la letra de [track]; la de la canción anterior se borra al instante. */
    fun loadLyricsFor(track: TrackItem?) {
        lyricsJob?.cancel()
        _lyricsState.value = null
        if (track == null) {
            _isLoadingLyrics.value = false
            return
        }
        lyricsJob = viewModelScope.launch {
            _isLoadingLyrics.value = true
            val result = container.lyricsRepository.fetchLyrics(
                artist = track.artist,
                title = track.cleanTitle.ifBlank { track.title },
                durationSeconds = track.durationSeconds
            )
            _lyricsState.value = result
            _isLoadingLyrics.value = false
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    /** Igual que sync, pero si ya existe en la biblioteca usa su id y fuente (para recomendaciones). */
    private fun SavedIndex.syncKeepingSource(rec: TrackItem): TrackItem {
        val match = match(rec)?.takeIf { it.downloadStatus != DownloadStatus.NONE }
            ?: return sync(rec)
        return sync(rec).copy(
            id = match.id,
            sourceUrl = match.sourceUrl.ifBlank { rec.sourceUrl }
        )
    }

    companion object {
        private fun normalizedKey(track: TrackItem): String =
            TitleCleaner.normalizeForMatching("${track.artist} ${track.cleanTitle.ifBlank { track.title }}")

        const val PREMIUM_DOWNLOAD_MESSAGE =
            "Las descargas son de Premium. Activa tu plan en Cuenta para escuchar sin conexión."

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MareaViewModel(container) as T
                }
            }
    }
}
