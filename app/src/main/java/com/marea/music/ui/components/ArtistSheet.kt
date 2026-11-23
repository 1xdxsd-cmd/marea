package com.marea.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marea.music.domain.model.TrackItem
import com.marea.music.ui.ArtistUiState
import com.marea.music.ui.theme.MareaBg
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaTextSecondary
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaText
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import com.marea.music.ui.theme.MareaOnAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSheet(
    state: ArtistUiState,
    currentTrackId: String?,
    onDismiss: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onPlayShuffled: (List<TrackItem>) -> Unit,
    onDownloadTrack: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onDeleteTrack: (TrackItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allArtistTracks = state.downloadedTracks + state.recommendedTracks

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MareaBg,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        val heroCover = allArtistTracks.firstOrNull { it.bestCoverUrl.isNotBlank() }?.bestCoverUrl.orEmpty()
        Box(modifier = Modifier.fillMaxSize()) {
        MareaBackdrop {}
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                ArtworkBackdrop(url = heroCover)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Cerrar",
                            onClick = onDismiss,
                            size = 40.dp,
                            iconSize = 20.dp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(
                            url = heroCover,
                            modifier = Modifier
                                .size(84.dp)
                                .glow(MareaAccent, spread = 12.dp, alpha = 0.4f)
                                .border(2.dp, Glass.accent, CircleShape),
                            shape = CircleShape,
                            requestSize = 300,
                            fallback = Icons.Rounded.Person
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            NeonTag("Artista")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = state.artistName,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MareaText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (allArtistTracks.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlowButton(
                                text = "Reproducir",
                                icon = Icons.Rounded.PlayArrow,
                                onClick = { onPlayTrack(allArtistTracks.first(), allArtistTracks) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            GlassIconButton(
                                icon = Icons.Rounded.Shuffle,
                                contentDescription = "Aleatorio",
                                onClick = { onPlayShuffled(allArtistTracks) },
                                size = 52.dp,
                                tint = MareaCyan
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item(contentType = "header") {
                    GlassSectionTitle(
                        title = "Descargadas",
                        subtitle = if (state.downloadedTracks.size == 1) "1 canción" else "${state.downloadedTracks.size} canciones",
                        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                    )
                }

                if (state.downloadedTracks.isEmpty()) {
                    item(contentType = "empty_downloaded") {
                        Text(
                            text = "Sin canciones descargadas",
                            color = MareaTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(state.downloadedTracks, key = { "dl_${it.id}" }, contentType = { "track" }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onClick = { onPlayTrack(track, state.downloadedTracks) },
                            onDownloadClick = { onDownloadTrack(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onDeleteTrack = { onDeleteTrack(track) }
                        )
                    }
                }

                item(contentType = "header_recs") {
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassSectionTitle(
                        title = "Más de ${state.artistName}",
                        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
                    )
                }

                when {
                    state.isLoadingRecommendations -> {
                        item(contentType = "loading_recs") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MareaCyan, strokeWidth = 2.5.dp)
                            }
                        }
                    }

                    state.recommendedTracks.isEmpty() -> {
                        item(contentType = "empty_recs") {
                            Text(
                                text = "Sin resultados disponibles",
                                color = MareaTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    else -> {
                        items(state.recommendedTracks, key = { "rec_${it.id}" }, contentType = { "track" }) { track ->
                            TrackRow(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                onClick = { onPlayTrack(track, state.recommendedTracks) },
                                onDownloadClick = { onDownloadTrack(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
