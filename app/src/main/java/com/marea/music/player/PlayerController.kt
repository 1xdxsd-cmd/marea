package com.marea.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.marea.music.R
import com.marea.music.data.extractor.YouTubeExtractorRepository
import com.marea.music.data.history.ListeningHistory
import com.marea.music.data.podcast.PodcastProgressStore
import com.marea.music.domain.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class PlaybackUiState(
    val currentTrack: TrackItem? = null,
    val queue: List<TrackItem> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val errorMessage: String? = null,
    /** Está sonando un anuncio (plan gratis). */
    val isAd: Boolean = false,
    /** Con Premium no hay anuncios, los saltos son ilimitados y hay ecualizador. */
    val isPremium: Boolean = false
)

class PlayerController(
    private val context: Context,
    private val extractorRepo: YouTubeExtractorRepository,
    private val history: ListeningHistory? = null,
    private val radioQueue: RadioQueue? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    // La posición cambia 4 veces por segundo: va en su propio flujo para que solo la barra
    // de progreso y la letra se redibujen, no toda la app.
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var mediaController: MediaController? = null
    private var originalQueue: List<TrackItem> = emptyList()
    private var activeQueue: List<TrackItem> = emptyList()
    private var currentIndex: Int = -1
    private var playJob: Job? = null
    private var prefetchJob: Job? = null
    private var radioJob: Job? = null
    private var errorSkipJob: Job? = null
    private var consecutiveErrors = 0
    private var podcastTicks = 0

    // ---- Plan gratis: anuncios y límite de saltos ----
    private val freePrefs = context.getSharedPreferences(FREE_PREFS, Context.MODE_PRIVATE)
    private var premium = false
    private var songsSinceAd = 0
    private var nextAd = (0 until AD_RESOURCES.size).random()
    /** Canción que sonará cuando termine el anuncio. */
    private var indexAfterAd: Int? = null

    init {
        EqualizerManager.init(context)
        VolumeBoostManager.init(context)
        PodcastProgressStore.init(context)
        connectController()
        startProgressTicker()
    }

    private fun connectController() {
        scope.launch {
            runCatching {
                val token = SessionToken(
                    context,
                    ComponentName(context, MareaPlaybackService::class.java)
                )
                val controller = MediaController.Builder(context, token).buildAsync().await()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.update { it.copy(isPlaying = isPlaying) }
                        if (!isPlaying) savePodcastProgress()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.update {
                            it.copy(
                                isBuffering = playbackState == Player.STATE_BUFFERING,
                                durationMs = controller.duration.coerceAtLeast(0L)
                            )
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            if (_uiState.value.isAd) finishAd() else handleTrackEnded()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (_uiState.value.isAd) {
                            finishAd()
                            return
                        }
                        _uiState.update {
                            it.copy(
                                isBuffering = false,
                                isPlaying = false,
                                errorMessage = error.localizedMessage ?: "Error al reproducir el audio"
                            )
                        }
                        skipAfterError()
                    }
                })
            }
        }
    }

    private fun startProgressTicker() {
        scope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(_uiState.value.durationMs)
                    _position.value = pos
                    if (++podcastTicks % 20 == 0) savePodcastProgress()
                    if (_uiState.value.durationMs != dur) {
                        _uiState.update { it.copy(durationMs = dur) }
                    }
                }
                delay(250L)
            }
        }
    }

    fun playTrack(track: TrackItem, queue: List<TrackItem> = listOf(track)) {
        originalQueue = queue
        if (_uiState.value.shuffleEnabled && queue.size > 1) {
            val remaining = queue.filter { it.id != track.id }.shuffled()
            activeQueue = listOf(track) + remaining
            currentIndex = 0
        } else {
            activeQueue = queue
            currentIndex = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        }
        if (_uiState.value.isAd) {
            // La canción elegida suena en cuanto termine el anuncio.
            indexAfterAd = currentIndex
            _uiState.update { it.copy(queue = activeQueue) }
            return
        }
        playAtIndex(currentIndex)
    }

    fun toggleShuffle() {
        val nextShuffle = !_uiState.value.shuffleEnabled
        val currentTrack = if (_uiState.value.isAd) {
            indexAfterAd?.let { activeQueue.getOrNull(it) }
        } else {
            _uiState.value.currentTrack
        }
        if (originalQueue.isNotEmpty()) {
            if (nextShuffle) {
                if (currentTrack != null) {
                    val remaining = originalQueue.filter { it.id != currentTrack.id }.shuffled()
                    activeQueue = listOf(currentTrack) + remaining
                    currentIndex = 0
                } else {
                    activeQueue = originalQueue.shuffled()
                    currentIndex = 0
                }
            } else {
                activeQueue = originalQueue
                currentIndex = if (currentTrack != null) {
                    originalQueue.indexOfFirst { it.id == currentTrack.id }.takeIf { it >= 0 } ?: 0
                } else 0
            }
        }
        if (_uiState.value.isAd && indexAfterAd != null) indexAfterAd = currentIndex
        _uiState.update { it.copy(shuffleEnabled = nextShuffle, queue = activeQueue) }
        prefetchNextTrack()
    }

    fun cycleRepeatMode() {
        val nextMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.update { it.copy(repeatMode = nextMode) }
    }

    private fun handleTrackEnded() {
        _uiState.value.currentTrack?.takeIf { it.isPodcast }?.let { PodcastProgressStore.markFinished(it.id) }
        if (activeQueue.isEmpty()) return
        when (_uiState.value.repeatMode) {
            RepeatMode.ONE -> {
                mediaController?.seekTo(0L)
                mediaController?.play()
            }
            RepeatMode.ALL -> {
                currentIndex = (currentIndex + 1) % activeQueue.size
                playAtIndex(currentIndex)
            }
            RepeatMode.OFF -> {
                if (currentIndex + 1 < activeQueue.size) {
                    currentIndex += 1
                    playAtIndex(currentIndex)
                } else {
                    // Fin de la cola: sigue con canciones parecidas (radio).
                    continueWithRadio(fromIndex = currentIndex)
                }
            }
        }
    }

    fun playNext() {
        if (activeQueue.isEmpty()) return
        if (blockedByAd()) return
        val hasNext = currentIndex + 1 < activeQueue.size || _uiState.value.repeatMode == RepeatMode.ALL
        if (!hasNext) {
            if (radioQueue == null) return
            if (!consumeFreeSkip()) return
            continueWithRadio(fromIndex = currentIndex)
            return
        }
        if (!consumeFreeSkip()) return
        if (currentIndex + 1 < activeQueue.size) {
            currentIndex += 1
            playAtIndex(currentIndex)
        } else {
            currentIndex = 0
            playAtIndex(currentIndex)
        }
    }

    fun playPrevious() {
        if (blockedByAd()) return
        val controller = mediaController
        if (controller != null && controller.currentPosition > 3_000L) {
            controller.seekTo(0L)
            return
        }
        if (activeQueue.isEmpty()) return
        if (currentIndex - 1 >= 0) {
            currentIndex -= 1
            playAtIndex(currentIndex)
        } else if (_uiState.value.repeatMode == RepeatMode.ALL) {
            currentIndex = activeQueue.lastIndex
            playAtIndex(currentIndex)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    /** Pausa y muestra un aviso (por ejemplo, la cuenta empezó a sonar en otro dispositivo). */
    fun pauseWithMessage(message: String) {
        mediaController?.pause()
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun seekTo(positionMs: Long) {
        if (_uiState.value.isAd) return
        mediaController?.seekTo(positionMs)
        _position.value = positionMs
    }

    fun refreshTrackMetadata(updatedTrack: TrackItem, replayAudio: Boolean = false) {
        originalQueue = originalQueue.map { if (it.id == updatedTrack.id) updatedTrack else it }
        activeQueue = activeQueue.map { if (it.id == updatedTrack.id) updatedTrack else it }
        if (_uiState.value.currentTrack?.id == updatedTrack.id) {
            _uiState.update {
                it.copy(
                    currentTrack = updatedTrack,
                    queue = activeQueue,
                    durationMs = if (replayAudio) updatedTrack.durationSeconds * 1000L else it.durationMs
                )
            }
            if (replayAudio) {
                playAtIndex(currentIndex, countForAds = false)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ---------- Plan gratis / Premium ----------

    /** Lo actualiza la app según la suscripción de la cuenta. */
    fun setPremium(value: Boolean) {
        EqualizerManager.setPremium(value)
        if (premium == value) return
        premium = value
        _uiState.update { it.copy(isPremium = value) }
        if (value && _uiState.value.isAd) finishAd()
    }

    private fun blockedByAd(): Boolean {
        if (!_uiState.value.isAd) return false
        _uiState.update { it.copy(errorMessage = "Tu música sigue después del anuncio") }
        return true
    }

    /** Plan gratis: máximo [FREE_SKIPS_PER_HOUR] saltos por hora. Devuelve false si ya no quedan. */
    private fun consumeFreeSkip(): Boolean {
        if (premium) return true
        val now = System.currentTimeMillis()
        val recent = freePrefs.getString(KEY_SKIPS, "").orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { now - it < HOUR_MS }
        if (recent.size >= FREE_SKIPS_PER_HOUR) {
            val minutes = ((HOUR_MS - (now - recent.min())) / 60_000L + 1).coerceAtLeast(1L)
            _uiState.update {
                it.copy(
                    errorMessage = "Llegaste al límite de $FREE_SKIPS_PER_HOUR saltos por hora. " +
                        "Podrás saltar de nuevo en $minutes min, o hazte Premium para saltar sin límite."
                )
            }
            return false
        }
        freePrefs.edit().putString(KEY_SKIPS, (recent + now).joinToString(",")).apply()
        return true
    }

    private fun playAd(nextIndex: Int) {
        val controller = mediaController ?: return
        playJob?.cancel()
        prefetchJob?.cancel()
        indexAfterAd = nextIndex
        songsSinceAd = 0
        val resId = AD_RESOURCES[nextAd]
        nextAd = (nextAd + 1) % AD_RESOURCES.size

        _position.value = 0L
        _uiState.update {
            it.copy(
                currentTrack = AD_TRACK,
                queue = activeQueue,
                isAd = true,
                isBuffering = true,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = null
            )
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(AD_TRACK.id)
            .setUri(Uri.parse("android.resource://${context.packageName}/$resId"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(AD_TRACK.title)
                    .setArtist(AD_TRACK.artist)
                    .build()
            )
            .build()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    private fun finishAd() {
        val next = indexAfterAd ?: currentIndex
        indexAfterAd = null
        _uiState.update { it.copy(isAd = false) }
        currentIndex = next
        playAtIndex(next, countForAds = false)
    }

    private fun playAtIndex(index: Int, countForAds: Boolean = true) {
        val track = activeQueue.getOrNull(index) ?: return
        savePodcastProgress()
        if (countForAds && !premium) {
            if (songsSinceAd >= SONGS_BETWEEN_ADS && mediaController != null) {
                playAd(index)
                return
            }
            songsSinceAd += 1
        }
        playJob?.cancel()
        prefetchJob?.cancel()

        playJob = scope.launch {
            _position.value = 0L
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    queue = activeQueue,
                    isAd = false,
                    isBuffering = true,
                    positionMs = 0L,
                    durationMs = track.durationSeconds * 1000L,
                    errorMessage = null
                )
            }
            try {
                val playableUri = withContext(Dispatchers.IO) { resolvePlayableUri(track) }
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.cleanTitle.ifBlank { track.title })
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album.ifBlank { null })
                    .setArtworkUri(track.bestCoverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(playableUri)
                    .setMediaMetadata(metadata)
                    .build()

                val controller = mediaController ?: return@launch
                // Los episodios de podcast siguen desde donde te quedaste.
                val startMs = if (track.isPodcast) PodcastProgressStore.resumePositionMs(track.id) else 0L
                if (startMs > 0L) _position.value = startMs
                controller.setMediaItem(mediaItem, startMs)
                controller.prepare()
                controller.play()
                if (!track.isPodcast) history?.record(track)
                consecutiveErrors = 0

                prefetchNextTrack()
                extendWithRadioIfNeeded()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        errorMessage = e.message ?: "No se pudo reproducir la canción"
                    )
                }
                skipAfterError()
            }
        }
    }

    private fun savePodcastProgress() {
        val track = _uiState.value.currentTrack?.takeIf { it.isPodcast } ?: return
        val controller = mediaController ?: return
        val pos = controller.currentPosition.coerceAtLeast(0L)
        if (pos < 5_000L) return
        PodcastProgressStore.save(track.id, pos, controller.duration.coerceAtLeast(_uiState.value.durationMs))
    }

    // ---------- Radio: seguir con canciones parecidas ----------

    /** Cuando quedan pocas canciones en la cola, agrega parecidas en segundo plano. */
    private fun extendWithRadioIfNeeded() {
        val radio = radioQueue ?: return
        if (_uiState.value.repeatMode != RepeatMode.OFF) return
        if (currentIndex < activeQueue.size - 2) return
        if (radioJob?.isActive == true) return
        val seed = activeQueue.getOrNull(currentIndex) ?: return
        if (seed.isPodcast) return
        radioJob = scope.launch {
            val added = runCatching { radio.similarTo(seed, activeQueue) }.getOrDefault(emptyList())
            if (added.isNotEmpty()) appendToQueue(added)
        }
    }

    private fun appendToQueue(tracks: List<TrackItem>) {
        val ids = activeQueue.map { it.id }.toHashSet()
        val fresh = tracks.filter { ids.add(it.id) }
        if (fresh.isEmpty()) return
        activeQueue = activeQueue + fresh
        originalQueue = originalQueue + fresh
        _uiState.update { it.copy(queue = activeQueue) }
        prefetchNextTrack()
    }

    /** Se acabó la cola: espera (o pide) las parecidas y sigue sonando. */
    private fun continueWithRadio(fromIndex: Int) {
        // Los podcasts no siguen con canciones parecidas.
        val radio = radioQueue?.takeIf { activeQueue.getOrNull(fromIndex)?.isPodcast != true }
        if (radio == null) {
            _position.value = 0L
            _uiState.update { it.copy(isPlaying = false, positionMs = 0L) }
            return
        }
        val seed = activeQueue.getOrNull(fromIndex)
        scope.launch {
            _uiState.update { it.copy(isBuffering = true) }
            radioJob?.join()
            if (fromIndex + 1 >= activeQueue.size && seed != null) {
                val added = runCatching { radio.similarTo(seed, activeQueue) }.getOrDefault(emptyList())
                appendToQueue(added)
            }
            if (currentIndex != fromIndex) return@launch
            if (fromIndex + 1 < activeQueue.size) {
                currentIndex = fromIndex + 1
                playAtIndex(currentIndex)
            } else {
                _position.value = 0L
                _uiState.update { it.copy(isPlaying = false, isBuffering = false, positionMs = 0L) }
            }
        }
    }

    /** Si una canción no se puede reproducir, pasa a la siguiente (máximo 3 seguidas). */
    private fun skipAfterError() {
        consecutiveErrors += 1
        if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) return
        val failedIndex = currentIndex
        errorSkipJob?.cancel()
        errorSkipJob = scope.launch {
            delay(1_500L)
            if (currentIndex != failedIndex || _uiState.value.isAd) return@launch
            when {
                failedIndex + 1 < activeQueue.size -> {
                    currentIndex = failedIndex + 1
                    playAtIndex(currentIndex)
                }
                _uiState.value.repeatMode == RepeatMode.ALL && activeQueue.size > 1 -> {
                    currentIndex = 0
                    playAtIndex(currentIndex)
                }
                else -> continueWithRadio(fromIndex = failedIndex)
            }
        }
    }

    private fun prefetchNextTrack() {
        val nextIndex = when {
            activeQueue.isEmpty() -> return
            currentIndex + 1 < activeQueue.size -> currentIndex + 1
            _uiState.value.repeatMode == RepeatMode.ALL -> 0
            else -> return
        }
        val nextTrack = activeQueue.getOrNull(nextIndex) ?: return
        if (nextTrack.isPodcast) return
        if (!nextTrack.localUri.isNullOrBlank() || !nextTrack.localFilePath.isNullOrBlank()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            if (nextTrack.sourceUrl.isNotBlank()) {
                extractorRepo.prefetchStreamUrl(nextTrack.sourceUrl)
            } else {
                runCatching {
                    val found = extractorRepo.searchMusic(
                        "${nextTrack.artist} ${nextTrack.cleanTitle}",
                        originalsOnly = true
                    ).firstOrNull()
                    if (found != null) {
                        extractorRepo.prefetchStreamUrl(found.sourceUrl)
                    }
                }
            }
        }
    }

    private suspend fun resolvePlayableUri(track: TrackItem): Uri {
        if (track.isPodcast) return Uri.parse(track.sourceUrl)
        track.localUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
            val parsed = Uri.parse(uriStr)
            if (isLocalUriReadable(parsed)) return parsed
        }
        track.localFilePath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = java.io.File(path)
            if (file.exists() && file.length() > 0L) return Uri.fromFile(file)
        }
        val streamUrl = if (track.sourceUrl.isNotBlank()) {
            extractorRepo.resolveFastStreamingAudioUrl(track.sourceUrl)
        } else {
            val found = extractorRepo.searchMusic("${track.artist} ${track.cleanTitle}", originalsOnly = true)
                .firstOrNull()
                ?: throw IllegalStateException("No se encontró audio para ${track.cleanTitle}")
            extractorRepo.resolveFastStreamingAudioUrl(found.sourceUrl)
        }
        return Uri.parse(streamUrl)
    }

    private fun isLocalUriReadable(uri: Uri): Boolean {
        return when (uri.scheme) {
            "content" -> runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
            "file" -> uri.path?.let { java.io.File(it).let { f -> f.exists() && f.length() > 0L } } ?: false
            else -> false
        }
    }

    private companion object {
        const val FREE_PREFS = "marea_free"
        const val KEY_SKIPS = "skips"
        const val HOUR_MS = 60L * 60L * 1000L

        /** Plan gratis: saltos permitidos por hora. */
        const val FREE_SKIPS_PER_HOUR = 6

        /** Plan gratis: un anuncio cada tantas canciones. */
        const val SONGS_BETWEEN_ADS = 3

        /** Canciones con error seguidas que se saltan solas antes de detenerse. */
        const val MAX_CONSECUTIVE_ERRORS = 3

        val AD_RESOURCES = intArrayOf(
            R.raw.marea_ad_1,
            R.raw.marea_ad_2,
            R.raw.marea_ad_3,
            R.raw.marea_ad_4,
            R.raw.marea_ad_5
        )

        val AD_TRACK = TrackItem(
            id = "marea_ad",
            title = "Anuncio",
            cleanTitle = "Anuncio",
            artist = "Tu música sigue en unos segundos",
            sourceUrl = ""
        )
    }
}
