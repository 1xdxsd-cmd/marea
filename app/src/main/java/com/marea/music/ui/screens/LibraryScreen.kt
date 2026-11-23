package com.marea.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marea.music.domain.model.LocalPlaylist
import com.marea.music.domain.model.TrackItem
import com.marea.music.ui.components.CoverArt
import com.marea.music.ui.components.GlassCard
import com.marea.music.ui.components.GlassChip
import com.marea.music.ui.components.GlassIconButton
import com.marea.music.ui.components.GlowButton
import com.marea.music.ui.components.LocalIsPremium
import com.marea.music.ui.components.NeonTag
import com.marea.music.ui.components.TrackRow
import com.marea.music.ui.components.glass
import com.marea.music.ui.components.glow
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaTextSecondary
import com.marea.music.ui.theme.MareaOnAccent

@Composable
fun LibraryScreen(
    downloadedTracks: List<TrackItem>,
    activeDownloads: List<TrackItem>,
    playlists: List<LocalPlaylist>,
    currentTrackId: String?,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onPlayShuffled: (List<TrackItem>) -> Unit,
    onRetryDownload: (TrackItem) -> Unit,
    onCancelQueueTrack: (TrackItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onDeleteTrack: (TrackItem) -> Unit,
    onRemoveTrackFromPlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var openedPlaylist by remember { mutableStateOf<LocalPlaylist?>(null) }

    val currentOpened = openedPlaylist?.let { open ->
        playlists.find { it.id == open.id }
    }

    if (currentOpened != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            val openedCover = currentOpened.coverUrl.ifBlank {
                currentOpened.tracks.firstOrNull { it.bestCoverUrl.isNotBlank() }?.bestCoverUrl.orEmpty()
            }
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                shape = RoundedCornerShape(28.dp),
                contentPadding = 16.dp
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    onClick = { openedPlaylist = null },
                    size = 38.dp,
                    iconSize = 20.dp
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(
                        url = openedCover,
                        modifier = Modifier
                            .size(96.dp)
                            .glow(MareaAccent, spread = 12.dp, alpha = 0.35f),
                        shape = RoundedCornerShape(22.dp),
                        requestSize = 360,
                        fallback = Icons.AutoMirrored.Rounded.PlaylistPlay
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        NeonTag("Playlist")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = currentOpened.name,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MareaText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = songCount(currentOpened.tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MareaTextSecondary
                        )
                    }
                }
                if (currentOpened.tracks.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlowButton(
                            text = "Reproducir",
                            icon = Icons.Rounded.PlayArrow,
                            onClick = { onPlayTrack(currentOpened.tracks.first(), currentOpened.tracks) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        GlassIconButton(
                            icon = Icons.Rounded.Shuffle,
                            contentDescription = "Aleatorio",
                            onClick = { onPlayShuffled(currentOpened.tracks) },
                            size = 52.dp,
                            tint = MareaCyan
                        )
                    }
                }
            }

            if (currentOpened.tracks.isEmpty()) {
                EmptyState("Esta playlist está vacía. Agrega canciones desde el menú ⋮ de cualquier canción.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(currentOpened.tracks, key = { it.id }, contentType = { "track" }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onClick = { onPlayTrack(track, currentOpened.tracks) },
                            onDownloadClick = { onRetryDownload(track) },
                            onArtistClick = onArtistClick,
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onRemoveFromPlaylist = {
                                onRemoveTrackFromPlaylist(currentOpened.id, track.id)
                            },
                            onDeleteTrack = { onDeleteTrack(track) }
                        )
                    }
                }
            }
        }
        return
    }

    val uriHandler = LocalUriHandler.current
    val isPremium = LocalIsPremium.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tu biblioteca",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MareaText
                )
                Text(
                    text = "${songCount(downloadedTracks.size)} sin conexión · ${playlists.size} playlists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MareaTextSecondary
                )
            }
            GlassIconButton(
                icon = Icons.Rounded.FavoriteBorder,
                contentDescription = "Apoyar en Ko-fi",
                onClick = { uriHandler.openUri("https://ko-fi.com/jsguaman") },
                tint = MareaCyan
            )
        }

        val tabs = listOf(
            "Descargas (${downloadedTracks.size})",
            "Playlists (${playlists.size})",
            "Cola (${activeDownloads.size})"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val icons = listOf(Icons.Rounded.DownloadDone, Icons.AutoMirrored.Rounded.QueueMusic, Icons.Rounded.Downloading)
            tabs.forEachIndexed { index, title ->
                GlassChip(
                    text = title,
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = icons[index]
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                if (downloadedTracks.isEmpty()) {
                    EmptyState(
                        if (isPremium) "Aún no descargas canciones. Toca el ícono de descarga en cualquier canción."
                        else "Descargar para escuchar sin conexión es de Premium. Actívalo en Cuenta."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(downloadedTracks, key = { it.id }, contentType = { "track" }) { track ->
                            TrackRow(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                onClick = { onPlayTrack(track, downloadedTracks) },
                                onDownloadClick = { onRetryDownload(track) },
                                onArtistClick = onArtistClick,
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onDeleteTrack = { onDeleteTrack(track) }
                            )
                        }
                    }
                }
            }

            1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    GlowButton(
                        text = "Nueva playlist",
                        icon = Icons.Rounded.Add,
                        onClick = onCreateNewPlaylist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )

                    if (playlists.isEmpty()) {
                        EmptyState("Crea tu primera playlist y junta tus canciones favoritas.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                        .glass(RoundedCornerShape(20.dp))
                                        .clickable { openedPlaylist = playlist }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val cover = playlist.coverUrl.ifBlank {
                                        playlist.tracks.firstOrNull { it.bestCoverUrl.isNotBlank() }?.bestCoverUrl.orEmpty()
                                    }
                                    CoverArt(
                                        url = cover,
                                        modifier = Modifier.size(58.dp),
                                        shape = RoundedCornerShape(15.dp),
                                        requestSize = 200,
                                        fallback = Icons.AutoMirrored.Rounded.PlaylistPlay
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            color = MareaText,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = songCount(playlist.tracks.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MareaTextSecondary
                                        )
                                    }
                                    IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Eliminar playlist",
                                            tint = MareaTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (activeDownloads.isEmpty()) {
                    EmptyState("No hay descargas en curso.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(activeDownloads, key = { it.id }, contentType = { "track" }) { track ->
                            TrackRow(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                onClick = { onPlayTrack(track, activeDownloads) },
                                onDownloadClick = { onRetryDownload(track) },
                                onArtistClick = onArtistClick,
                                onCancelOrRemoveQueue = { onCancelQueueTrack(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun songCount(n: Int): String = if (n == 1) "1 canción" else "$n canciones"

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MareaCyan,
                modifier = Modifier.glow(MareaAccent, spread = 10.dp, alpha = 0.4f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = MareaTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
