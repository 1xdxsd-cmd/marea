package com.marea.music.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marea.music.domain.model.PlaylistResult
import com.marea.music.domain.model.TrackItem
import com.marea.music.ui.components.GlassSectionTitle
import com.marea.music.ui.components.GlassIconButton
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import com.marea.music.ui.SearchUiState
import com.marea.music.ui.components.CoverArt
import com.marea.music.ui.components.GlassCard
import com.marea.music.ui.components.GlassChip
import com.marea.music.ui.components.GlowButton
import com.marea.music.ui.components.LocalIsPremium
import com.marea.music.ui.components.NeonTag
import com.marea.music.ui.components.TrackRow
import com.marea.music.ui.components.glass
import com.marea.music.ui.components.glassFieldColors
import com.marea.music.ui.components.glow
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaCard
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaTextSecondary
import com.marea.music.ui.theme.MareaOnAccent

@Composable
fun SearchScreen(
    state: SearchUiState,
    currentTrackId: String?,
    onQueryChange: (String) -> Unit,
    onOriginalsFilterChange: (Boolean) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onDownloadSingle: (TrackItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onDeleteTrack: (TrackItem) -> Unit,
    onTogglePlaylistSelection: (String) -> Unit,
    onSelectAllPlaylist: (Boolean) -> Unit,
    onDownloadSelectedPlaylist: () -> Unit,
    onClearPlaylist: () -> Unit,
    onOpenPlaylist: (PlaylistResult) -> Unit = {},
    onPlayShuffled: (List<TrackItem>) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val isPremium = LocalIsPremium.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
            Text(
                text = "Buscar",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MareaText
            )
            Text(
                text = "Canciones, artistas o pega un link de YouTube",
                style = MaterialTheme.typography.bodySmall,
                color = MareaTextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Buscar") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            val clip = clipboard.getText()?.text.orEmpty()
                            if (clip.isNotBlank()) {
                                onQueryChange(clip)
                                onSubmitSearch(clip)
                                keyboard?.hide()
                            }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Pegar")
                        }
                        if (state.query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Limpiar")
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSubmitSearch(state.query)
                    }
                ),
                colors = glassFieldColors(),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(
                    text = "Solo originales",
                    selected = state.originalsOnly,
                    onClick = { onOriginalsFilterChange(true) },
                    icon = Icons.Rounded.Verified
                )
                GlassChip(
                    text = "Todo",
                    selected = !state.originalsOnly,
                    onClick = { onOriginalsFilterChange(false) },
                    icon = Icons.Rounded.AllInclusive
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MareaCyan, strokeWidth = 2.5.dp)
            }
            return
        }

        state.error?.let { errorMsg ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        state.infoMessage?.let { msg ->
            Surface(
                color = MareaAccent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MareaAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = msg,
                    color = MareaCyan,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        val playlist = state.playlistPreview
        if (playlist != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item(contentType = "playlist_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .glass(RoundedCornerShape(26.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playlist.thumbnailUrl.isNotBlank()) {
                                CoverArt(
                                    url = playlist.thumbnailUrl,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .glow(MareaAccent, spread = 8.dp, alpha = 0.35f),
                                    shape = RoundedCornerShape(18.dp),
                                    requestSize = 300
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                NeonTag("Playlist")
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.author}  ·  ${playlist.tracks.size} canciones",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MareaTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (playlist.tracks.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlowButton(
                                    text = "Reproducir",
                                    icon = Icons.Rounded.PlayArrow,
                                    onClick = { onPlayTrack(playlist.tracks.first(), playlist.tracks) },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                GlassIconButton(
                                    icon = Icons.Rounded.Shuffle,
                                    contentDescription = "Aleatorio",
                                    onClick = { onPlayShuffled(playlist.tracks) },
                                    size = 52.dp,
                                    tint = MareaCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        val pendingTracks = playlist.tracks.filter { !it.isDownloaded }
                        val pendingSelectedCount = pendingTracks.count { it.id in state.selectedPlaylistIds }

                        GlowButton(
                            text = when {
                                pendingTracks.isEmpty() -> "Playlist descargada"
                                !isPremium -> "Descargar con Premium"
                                else -> "Descargar ($pendingSelectedCount)"
                            },
                            icon = if (isPremium) Icons.Rounded.Download else Icons.Rounded.Lock,
                            onClick = onDownloadSelectedPlaylist,
                            enabled = pendingSelectedCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val allSelected = pendingTracks.isNotEmpty() && pendingSelectedCount == pendingTracks.size
                            TextButton(
                                onClick = { onSelectAllPlaylist(!allSelected) },
                                enabled = pendingTracks.isNotEmpty()
                            ) {
                                Text(
                                    if (allSelected) "Deseleccionar" else "Seleccionar todas",
                                    color = MareaCyan
                                )
                            }
                            TextButton(onClick = onClearPlaylist) {
                                Text("Cerrar", color = MareaTextSecondary)
                            }
                        }
                    }
                }

                items(playlist.tracks, key = { it.id }, contentType = { "track" }) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = track.isDownloaded || track.id in state.selectedPlaylistIds,
                            enabled = !track.isDownloaded,
                            onCheckedChange = { onTogglePlaylistSelection(track.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MareaAccent,
                                checkmarkColor = MareaOnAccent,
                                uncheckedColor = MareaTextSecondary
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        TrackRow(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onClick = { onPlayTrack(track, playlist.tracks) },
                            onDownloadClick = { onDownloadSingle(track) },
                            onArtistClick = onArtistClick,
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onDeleteTrack = { onDeleteTrack(track) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            if (state.results.isEmpty() && state.playlists.isEmpty() && state.error == null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MareaCyan,
                        modifier = Modifier.glow(MareaAccent, spread = 10.dp, alpha = 0.4f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Encuentra cualquier canción",
                        color = MareaText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Escribe el nombre de una canción o artista, o pega un link de YouTube o de una playlist.",
                        color = MareaTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (state.playlists.isNotEmpty()) {
                    item(key = "playlists_row", contentType = "playlists_row") {
                        SearchPlaylistsRow(playlists = state.playlists, onOpen = onOpenPlaylist)
                    }
                }
                items(state.results, key = { it.id }, contentType = { "track" }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = track.id == currentTrackId,
                        // Suena esta canción y después siguen parecidas (no otras versiones de la búsqueda).
                        onClick = { onPlayTrack(track, listOf(track)) },
                        onDownloadClick = { onDownloadSingle(track) },
                        onArtistClick = onArtistClick,
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onDeleteTrack = { onDeleteTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPlaylistsRow(
    playlists: List<PlaylistResult>,
    onOpen: (PlaylistResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GlassSectionTitle(
            title = "Playlists",
            modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = { it.url }, contentType = { "playlist" }) { pl ->
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .glass(RoundedCornerShape(20.dp))
                        .clickable { onOpen(pl) }
                        .padding(10.dp)
                ) {
                    CoverArt(
                        url = pl.thumbnailUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(14.dp),
                        requestSize = 300,
                        fallback = Icons.AutoMirrored.Rounded.PlaylistPlay
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pl.title,
                        color = MareaText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildList {
                        if (pl.author.isNotBlank()) add(pl.author)
                        if (pl.trackCount > 0) add(if (pl.trackCount == 1L) "1 canción" else "${pl.trackCount} canciones")
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = MareaTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
