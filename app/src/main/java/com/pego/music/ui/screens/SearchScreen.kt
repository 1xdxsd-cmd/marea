package com.pego.music.ui.screens

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
import androidx.compose.material.icons.rounded.Clear
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
import com.pego.music.domain.model.TrackItem
import com.pego.music.ui.SearchUiState
import com.pego.music.ui.components.TrackRow
import com.pego.music.ui.theme.MareaCard
import com.pego.music.ui.theme.MareaAccent
import com.pego.music.ui.theme.MareaTextSecondary
import com.pego.music.ui.theme.MareaOnAccent

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
    onEditCover: (TrackItem) -> Unit,
    onTrimTrack: (TrackItem) -> Unit,
    onDeleteTrack: (TrackItem) -> Unit,
    onTogglePlaylistSelection: (String) -> Unit,
    onSelectAllPlaylist: (Boolean) -> Unit,
    onDownloadSelectedPlaylist: () -> Unit,
    onClearPlaylist: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MareaCard,
                    unfocusedContainerColor = MareaCard,
                    focusedBorderColor = MareaAccent,
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.originalsOnly,
                    onClick = { onOriginalsFilterChange(true) },
                    label = { Text("Solo Originales") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MareaAccent,
                        selectedLabelColor = MareaOnAccent
                    )
                )
                FilterChip(
                    selected = !state.originalsOnly,
                    onClick = { onOriginalsFilterChange(false) },
                    label = { Text("Todo") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MareaAccent,
                        selectedLabelColor = MareaOnAccent
                    )
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MareaAccent)
            }
            return
        }

        state.error?.let { errorMsg ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
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
                color = MareaAccent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = msg,
                    color = MareaAccent,
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(MareaCard)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playlist.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = playlist.thumbnailUrl,
                                    contentDescription = playlist.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.author} • ${playlist.tracks.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MareaTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val pendingTracks = playlist.tracks.filter { !it.isDownloaded }
                        val pendingSelectedCount = pendingTracks.count { it.id in state.selectedPlaylistIds }

                        Button(
                            onClick = onDownloadSelectedPlaylist,
                            enabled = pendingSelectedCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MareaAccent,
                                contentColor = MareaOnAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pendingTracks.isEmpty()) {
                                    "Playlist descargada"
                                } else {
                                    "Descargar ($pendingSelectedCount)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

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
                                    color = MareaAccent
                                )
                            }
                            OutlinedButton(onClick = onClearPlaylist) {
                                Text("Cerrar")
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
                            colors = CheckboxDefaults.colors(checkedColor = MareaAccent),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        TrackRow(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onClick = { onPlayTrack(track, playlist.tracks) },
                            onDownloadClick = { onDownloadSingle(track) },
                            onArtistClick = onArtistClick,
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onEditCover = { onEditCover(track) },
                            onTrimTrack = { onTrimTrack(track) },
                            onDeleteTrack = { onDeleteTrack(track) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(state.results, key = { it.id }, contentType = { "track" }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = track.id == currentTrackId,
                        onClick = { onPlayTrack(track, state.results) },
                        onDownloadClick = { onDownloadSingle(track) },
                        onArtistClick = onArtistClick,
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onEditCover = { onEditCover(track) },
                        onTrimTrack = { onTrimTrack(track) },
                        onDeleteTrack = { onDeleteTrack(track) }
                    )
                }
            }
        }
    }
}
