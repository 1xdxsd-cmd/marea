package com.marea.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marea.music.data.metadata.LrcParser
import com.marea.music.domain.model.LyricsResult
import com.marea.music.domain.model.TrackItem
import com.marea.music.player.PlaybackUiState
import com.marea.music.player.RepeatMode
import com.marea.music.ui.theme.MareaBg
import com.marea.music.ui.theme.MareaCard
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaTextSecondary
import com.marea.music.ui.theme.MareaOnAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    playbackState: PlaybackUiState,
    syncedTrack: TrackItem?,
    lyrics: LyricsResult?,
    isLoadingLyrics: Boolean,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onDownloadClick: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onEditCover: (TrackItem) -> Unit,
    onTrimTrack: (TrackItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onOpenEqualizer: () -> Unit = {}
) {
    val track = syncedTrack ?: playbackState.currentTrack ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MareaBg,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = when {
                        playbackState.isAd -> "ANUNCIO"
                        track.isDownloaded -> "OFFLINE"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MareaAccent
                )
                IconButton(onClick = onOpenEqualizer) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Ecualizador",
                        tint = MareaTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MareaCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.bestCoverUrl.isNotBlank()) {
                        AsyncImage(
                            model = track.bestCoverUrl,
                            contentDescription = track.cleanTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MareaTextSecondary,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Centered song name & clickable artist directly below image
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.cleanTitle.ifBlank { track.title },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MareaAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = track.artist.isNotBlank() && !playbackState.isAd) {
                            onArtistClick(track.artist)
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Options below song name & artist (no aplican a un anuncio)
            if (!playbackState.isAd) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onAddToPlaylist(track) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "Agregar a playlist",
                        tint = MareaTextSecondary
                    )
                }

                if (track.isDownloaded) {
                    IconButton(onClick = { onEditCover(track) }) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = "Cambiar portada",
                            tint = MareaTextSecondary
                        )
                    }
                    IconButton(onClick = { onTrimTrack(track) }) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCut,
                            contentDescription = "Recortar",
                            tint = MareaTextSecondary
                        )
                    }
                }

                OneClickDownloadButton(
                    status = track.downloadStatus,
                    progress = track.downloadProgress,
                    onClick = { onDownloadClick(track) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val duration = playbackState.durationMs.coerceAtLeast(1L)
            val sliderPos = playbackState.positionMs.coerceIn(0L, duration).toFloat()

            Slider(
                value = sliderPos,
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MareaAccent,
                    inactiveTrackColor = MareaCard
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(playbackState.positionMs / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MareaTextSecondary
                )
                Text(
                    text = formatDuration(duration / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MareaTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (playbackState.shuffleEnabled) MareaAccent else MareaTextSecondary
                    )
                }

                IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        modifier = Modifier.size(38.dp)
                    )
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = MareaAccent,
                    modifier = Modifier.size(66.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MareaOnAccent,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MareaOnAccent,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(onClick = onCycleRepeatMode) {
                    val repeatIcon = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repetir",
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) MareaAccent else MareaTextSecondary
                    )
                }
            }

            if (!playbackState.isAd) {
                LyricsPanel(
                    lyrics = lyrics,
                    isLoading = isLoadingLyrics,
                    positionMs = playbackState.positionMs,
                    onSeekTo = onSeekTo
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Letra debajo del reproductor. Con letra sincronizada resalta la línea que suena y se desplaza
 * sola; tocar una línea salta a ese momento de la canción.
 */
@Composable
private fun LyricsPanel(
    lyrics: LyricsResult?,
    isLoading: Boolean,
    positionMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val synced = remember(lyrics) { LrcParser.parse(lyrics?.syncedLyrics) }
    val active = LrcParser.activeIndex(synced, positionMs + 250L)
    val listState = rememberLazyListState()

    LaunchedEffect(active, synced) {
        if (synced.isNotEmpty()) listState.animateScrollToItem((active - 1).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MareaCard)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Letra",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MareaAccent, modifier = Modifier.size(28.dp))
            }

            synced.isNotEmpty() -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(synced) { index, line ->
                    val isActive = index == active
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = if (isActive) {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = when {
                            isActive -> MaterialTheme.colorScheme.onBackground
                            index < active -> MareaTextSecondary.copy(alpha = 0.6f)
                            else -> MareaTextSecondary
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSeekTo(line.timeMs) }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            !lyrics?.plainLyrics.isNullOrBlank() -> Text(
                text = lyrics?.plainLyrics.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            else -> Text(
                text = "No encontramos la letra de esta canción",
                color = MareaTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
