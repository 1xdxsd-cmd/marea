package com.marea.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marea.music.data.metadata.LrcParser
import com.marea.music.domain.model.LyricsResult
import com.marea.music.domain.model.TrackItem
import com.marea.music.player.VolumeBoostManager
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marea.music.player.PlaybackUiState
import com.marea.music.player.RepeatMode
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaBg
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaOnAccent
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary

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
    onArtistClick: (String) -> Unit,
    onOpenEqualizer: () -> Unit = {}
) {
    val track = syncedTrack ?: playbackState.currentTrack ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MareaBg,
        dragHandle = null,
        shape = RoundedCornerShape(0.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkBackdrop(url = track.bestCoverUrl)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .navigationBarsPadding()
            ) {
                Spacer(Modifier.height(8.dp))
                PlayerTopBar(
                    label = when {
                        playbackState.isAd -> "Anuncio"
                        track.isDownloaded -> "Sin conexión"
                        else -> "En streaming"
                    },
                    isPlaying = playbackState.isPlaying,
                    onDismiss = onDismiss,
                    onOpenEqualizer = onOpenEqualizer
                )

                Spacer(Modifier.height(26.dp))

                // Portada flotante con halo: se encoge un poco en pausa.
                val coverScale by animateFloatAsState(
                    targetValue = if (playbackState.isPlaying || playbackState.isBuffering) 1f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                    label = "coverScale"
                )
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .aspectRatio(1f)
                            .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                            .glow(MareaAccent, spread = 36.dp, alpha = 0.40f)
                    ) {
                        CoverArt(
                            url = track.bestCoverUrl,
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, Glass.border, RoundedCornerShape(30.dp)),
                            shape = RoundedCornerShape(30.dp),
                            requestSize = 900
                        )
                        // Reflejo de cristal en la parte de arriba de la portada.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.45f)
                                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                                    )
                                )
                        )
                    }
                }

                Spacer(Modifier.height(30.dp))

                // Título, artista y acciones
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.cleanTitle.ifBlank { track.title },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp
                            ),
                            color = MareaText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MareaCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = track.artist.isNotBlank() && !playbackState.isAd) {
                                    onArtistClick(track.artist)
                                }
                                .padding(vertical = 2.dp)
                        )
                    }
                    if (!playbackState.isAd) {
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Agregar a playlist",
                            onClick = { onAddToPlaylist(track) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Glass.fill, CircleShape)
                                .border(1.dp, Glass.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            OneClickDownloadButton(
                                status = track.downloadStatus,
                                progress = track.downloadProgress,
                                onClick = { onDownloadClick(track) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                GlowSeekBar(
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs,
                    onSeek = onSeekTo,
                    enabled = !playbackState.isAd
                )

                Spacer(Modifier.height(14.dp))

                PlayerControls(
                    playbackState = playbackState,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeatMode = onCycleRepeatMode
                )

                Spacer(Modifier.height(28.dp))

                if (!playbackState.isAd) {
                    LyricsPanel(
                        lyrics = lyrics,
                        isLoading = isLoadingLyrics,
                        positionMs = playbackState.positionMs,
                        onSeekTo = onSeekTo
                    )
                } else {
                    GlassCard {
                        Text(
                            "Escuchas un anuncio porque estás en el plan gratis.",
                            color = MareaText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Con Premium no hay anuncios, saltas sin límite, descargas y tienes ecualizador.",
                            color = MareaTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    label: String,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "Cerrar",
            onClick = onDismiss,
            iconSize = 26.dp
        )
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NowPlayingBars(animate = isPlaying, height = 10.dp, barWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "REPRODUCIENDO",
                    color = MareaTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                color = MareaText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        val boostDb by VolumeBoostManager.boostDb.collectAsState()
        var showVolume by remember { mutableStateOf(false) }
        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "Subir volumen",
            onClick = { showVolume = true },
            tint = if (boostDb > 0) MareaAccent else MareaCyan
        )
        Spacer(Modifier.width(8.dp))
        GlassIconButton(
            icon = Icons.Rounded.GraphicEq,
            contentDescription = "Ecualizador",
            onClick = onOpenEqualizer,
            tint = MareaCyan
        )
        if (showVolume) {
            VolumeBoostDialog(current = boostDb, onDismiss = { showVolume = false })
        }
    }
}

/** Sube el volumen por encima del máximo del teléfono para canciones que suenan bajito. */
@Composable
private fun VolumeBoostDialog(current: Int, onDismiss: () -> Unit) {
    GlassDialog(onDismiss = onDismiss) {
        Text(
            "Subir volumen",
            color = MareaText,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Para canciones que se escuchan muy bajito. Se aplica a todas las canciones hasta que lo regreses a Normal.",
            color = MareaTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VolumeBoostManager.LEVELS_DB.forEach { db ->
                GlassChip(
                    text = if (db == 0) "Normal" else "+$db dB",
                    selected = current == db,
                    onClick = { VolumeBoostManager.setBoost(db) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "En niveles altos algunas canciones pueden distorsionar.",
            color = MareaTextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Listo", color = MareaCyan, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PlayerControls(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleControl(
            icon = Icons.Rounded.Shuffle,
            description = "Aleatorio",
            active = playbackState.shuffleEnabled,
            onClick = onToggleShuffle
        )
        GlassIconButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Anterior",
            onClick = onPrevious,
            size = 58.dp,
            iconSize = 32.dp
        )

        // Botón principal: degradado azul con halo que respira al sonar.
        val pulse by animateFloatAsState(
            targetValue = if (playbackState.isPlaying) 0.55f else 0.3f,
            label = "playGlow"
        )
        Box(
            modifier = Modifier
                .size(82.dp)
                .glow(MareaAccent, spread = 22.dp, alpha = pulse)
                .clip(CircleShape)
                .background(Glass.accent, CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center
        ) {
            if (playbackState.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = MareaOnAccent,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                    tint = MareaOnAccent,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        GlassIconButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Siguiente",
            onClick = onNext,
            size = 58.dp,
            iconSize = 32.dp
        )
        ToggleControl(
            icon = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            description = "Repetir",
            active = playbackState.repeatMode != RepeatMode.OFF,
            onClick = onCycleRepeatMode
        )
    }
}

/** Aleatorio / repetir: ícono con un punto luminoso debajo cuando está activo. */
@Composable
private fun ToggleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MareaCyan else MareaTextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .then(if (active) Modifier.glow(MareaCyan, spread = 4.dp, alpha = 0.8f) else Modifier)
                .clip(CircleShape)
                .background(if (active) MareaCyan else Color.Transparent)
        )
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

    GlassCard(shape = RoundedCornerShape(28.dp), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MareaCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Letra",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MareaText,
                modifier = Modifier.weight(1f)
            )
            if (synced.isNotEmpty()) NeonTag("En vivo")
        }
        Spacer(modifier = Modifier.height(12.dp))
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MareaCyan, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }

            synced.isNotEmpty() -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .fadingEdges(top = 0.08f, bottom = 0.22f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(synced) { index, line ->
                    val isActive = index == active
                    val scale by animateFloatAsState(if (isActive) 1f else 0.94f, label = "lyricScale")
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = 22.sp,
                            lineHeight = 28.sp
                        ),
                        color = when {
                            isActive -> MareaText
                            index < active -> MareaTextSecondary.copy(alpha = 0.35f)
                            else -> MareaTextSecondary.copy(alpha = 0.7f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSeekTo(line.timeMs) }
                            .padding(vertical = 2.dp)
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }

            !lyrics?.plainLyrics.isNullOrBlank() -> Text(
                text = lyrics?.plainLyrics.orEmpty(),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = MareaText
            )

            else -> Text(
                text = "No encontramos la letra de esta canción",
                color = MareaTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start
            )
        }
    }
}
