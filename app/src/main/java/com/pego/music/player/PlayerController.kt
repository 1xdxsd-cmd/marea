package com.pego.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pego.music.data.extractor.YouTubeExtractorRepository
import com.pego.music.domain.model.TrackItem
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
    val errorMessage: String? = null
)

class PlayerController(
    private val context: Context,
    private val extractorRepo: YouTubeExtractorRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var originalQueue: List<TrackItem> = emptyList()
    private var activeQueue: List<TrackItem> = emptyList()
    private var currentIndex: Int = -1
    private var playJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        connectController()
        startProgressTicker()
    }

    private fun connectController() {
        scope.launch {
            runCatching {
                val token = SessionToken(
                    context,
                    ComponentName(context, PegoPlaybackService::class.java)
                )
                val controller = MediaController.Builder(context, token).buildAsync().await()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.update {
                            it.copy(
                                isBuffering = playbackState == Player.STATE_BUFFERING,
                                durationMs = controller.duration.coerceAtLeast(0L)
                            )
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            handleTrackEnded()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _uiState.update {
                            it.copy(
                                isBuffering = false,
                                isPlaying = false,
                                errorMessage = error.localizedMessage ?: "Error al reproducir el audio"
                            )
                        }
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
                    _uiState.update {
                        if (it.positionMs == pos && it.durationMs == dur) it
                        else it.copy(positionMs = pos, durationMs = dur)
                    }
                }
                delay(500L)
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
        playAtIndex(currentIndex)
    }

    fun toggleShuffle() {
        val nextShuffle = !_uiState.value.shuffleEnabled
        val currentTrack = _uiState.value.currentTrack
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
                    _uiState.update { it.copy(isPlaying = false, positionMs = 0L) }
                }
            }
        }
    }

    fun playNext() {
        if (activeQueue.isEmpty()) return
        if (currentIndex + 1 < activeQueue.size) {
            currentIndex += 1
            playAtIndex(currentIndex)
        } else if (_uiState.value.repeatMode == RepeatMode.ALL) {
            currentIndex = 0
            playAtIndex(currentIndex)
        }
    }

    fun playPrevious() {
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

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.update { it.copy(positionMs = positionMs) }
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
                playAtIndex(currentIndex)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun playAtIndex(index: Int) {
        val track = activeQueue.getOrNull(index) ?: return
        playJob?.cancel()
        prefetchJob?.cancel()

        playJob = scope.launch {
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    queue = activeQueue,
                    isBuffering = true,
                    positionMs = 0L,
                    durationMs = track.durationSeconds * 1000L,
                    errorMessage = null
                )
            }
            try {
                val playableUri = resolvePlayableUri(track)
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
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()

                prefetchNextTrack()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        errorMessage = e.message ?: "No se pudo reproducir la canción"
                    )
                }
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
}
