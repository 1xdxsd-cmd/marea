package com.pego.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pego.music.domain.model.LocalPlaylist
import com.pego.music.domain.model.TrackItem
import com.pego.music.ui.components.OneClickDownloadButton
import com.pego.music.ui.theme.MareaCard
import com.pego.music.ui.theme.MareaAccent
import com.pego.music.ui.theme.MareaTextSecondary
import com.pego.music.ui.theme.MareaSurface

@Composable
fun HomeScreen(
    playlists: List<LocalPlaylist>,
    recommendations: List<TrackItem>,
    isLoadingRecommendations: Boolean,
    currentTrackId: String?,
    onSearchSubmit: (String) -> Unit,
    onRefreshRecommendations: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onDownloadTrack: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onArtistClick: (String) -> Unit
) {
    var topQuery by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 120.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }, contentType = "search_bar") {
            OutlinedTextField(
                value = topQuery,
                onValueChange = { topQuery = it },
                placeholder = { Text("Buscar") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        val clip = clipboard.getText()?.text.orEmpty()
                        if (clip.isNotBlank()) {
                            topQuery = clip
                            keyboard?.hide()
                            onSearchSubmit(clip)
                        }
                    }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Pegar")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (topQuery.isNotBlank()) {
                            keyboard?.hide()
                            onSearchSubmit(topQuery)
                        }
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
        }

        if (playlists.isNotEmpty()) {
            item(span = { GridItemSpan(2) }, contentType = "playlists_row") {
                Column {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playlists, key = { it.id }, contentType = { "playlist_card" }) { playlist ->
                            PlaylistCompactCard(
                                playlist = playlist,
                                onClick = {
                                    playlist.tracks.firstOrNull()?.let { first ->
                                        onPlayTrack(first, playlist.tracks)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(2) }, contentType = "rec_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recomendado para ti",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onRefreshRecommendations) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualizar",
                        tint = MareaTextSecondary
                    )
                }
            }
        }

        if (isLoadingRecommendations && recommendations.isEmpty()) {
            item(span = { GridItemSpan(2) }, contentType = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MareaAccent)
                }
            }
        } else {
            items(
                items = recommendations,
                key = { it.id },
                contentType = { "recommended_grid_card" }
            ) { track ->
                RecommendedTrackGridCard(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, recommendations) },
                    onDownload = { onDownloadTrack(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onArtistClick = { onArtistClick(track.artist) }
                )
            }
        }
    }
}

@Composable
private fun RecommendedTrackGridCard(
    track: TrackItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onArtistClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(track.bestCoverUrl) {
        if (track.bestCoverUrl.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(track.bestCoverUrl)
                .size(360)
                .crossfade(false)
                .build()
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MareaCard)
            .clickable(onClick = onPlay)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Square Cover Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MareaSurface),
            contentAlignment = Alignment.Center
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = track.cleanTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MareaTextSecondary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Centered Song name & clickable artist directly below image
        Text(
            text = track.cleanTitle.ifBlank { track.title },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isPlaying) MareaAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MareaTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = track.artist.isNotBlank(), onClick = onArtistClick)
                .padding(vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Options row directly below the name
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = if (isPlaying) MareaAccent else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = "Agregar a playlist",
                    tint = MareaTextSecondary
                )
            }

            OneClickDownloadButton(
                status = track.downloadStatus,
                progress = track.downloadProgress,
                onClick = onDownload
            )
        }
    }
}

@Composable
private fun PlaylistCompactCard(
    playlist: LocalPlaylist,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cover = playlist.coverUrl.ifBlank {
        playlist.tracks.firstOrNull { it.bestCoverUrl.isNotBlank() }?.bestCoverUrl.orEmpty()
    }
    val imageRequest = remember(cover) {
        if (cover.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(cover)
                .size(260)
                .crossfade(false)
                .build()
        } else null
    }

    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MareaCard),
            contentAlignment = Alignment.Center
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = MareaTextSecondary,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
