package com.pego.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pego.music.domain.model.DownloadStatus
import com.pego.music.domain.model.TrackItem
import com.pego.music.ui.theme.PegoCard
import com.pego.music.ui.theme.PegoGreen
import com.pego.music.ui.theme.PegoTextSecondary

@Composable
fun TrackRow(
    track: TrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: ((String) -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onEditCover: (() -> Unit)? = null,
    onTrimTrack: (() -> Unit)? = null,
    onDeleteTrack: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onCancelOrRemoveQueue: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val imageRequest = remember(track.bestCoverUrl) {
        if (track.bestCoverUrl.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(track.bestCoverUrl)
                .size(160)
                .crossfade(false)
                .build()
        } else null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PegoCard),
            contentAlignment = Alignment.Center
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = track.cleanTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = PegoTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = track.cleanTitle.ifBlank { track.title },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isPlaying) PegoGreen else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            val artistLabel = buildString {
                append(track.artist.ifBlank { "Artista desconocido" })
                if (track.durationSeconds > 0) {
                    append(" • ")
                    append(formatDuration(track.durationSeconds))
                }
            }
            Text(
                text = artistLabel,
                style = MaterialTheme.typography.bodySmall,
                color = PegoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onArtistClick != null && track.artist.isNotBlank()) {
                            Modifier.clickable { onArtistClick(track.artist) }
                        } else Modifier
                    )
            )
        }

        OneClickDownloadButton(
            status = track.downloadStatus,
            progress = track.downloadProgress,
            onClick = onDownloadClick
        )

        if (onCancelOrRemoveQueue != null) {
            IconButton(onClick = onCancelOrRemoveQueue) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancelar o eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        val hasMenu = onAddToPlaylist != null ||
            (track.isDownloaded && (onEditCover != null || onTrimTrack != null || onDeleteTrack != null)) ||
            onRemoveFromPlaylist != null

        if (hasMenu) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Opciones",
                        tint = PegoTextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(PegoCard)
                ) {
                    onAddToPlaylist?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Agregar a playlist") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                action()
                            }
                        )
                    }
                    if (track.isDownloaded) {
                        onEditCover?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Cambiar portada") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Image, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    action()
                                }
                            )
                        }
                        onTrimTrack?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Recortar canción") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.ContentCut, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    action()
                                }
                            )
                        }
                    }
                    onRemoveFromPlaylist?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Quitar de esta playlist") },
                            leadingIcon = {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                action()
                            }
                        )
                    }
                    if (track.isDownloaded) {
                        onDeleteTrack?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Eliminar descarga", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    action()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OneClickDownloadButton(
    status: DownloadStatus,
    progress: Int,
    onClick: () -> Unit
) {
    when (status) {
        DownloadStatus.COMPLETED -> {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Ya descargada",
                    tint = PegoGreen
                )
            }
        }

        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.TAGGING -> {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (progress > 0) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(22.dp),
                        color = PegoGreen,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = PegoGreen,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }

        DownloadStatus.FAILED -> {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Reintentar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        DownloadStatus.NONE -> {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Descargar",
                    tint = PegoTextSecondary
                )
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
