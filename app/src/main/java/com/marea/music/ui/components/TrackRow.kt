package com.marea.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marea.music.domain.model.DownloadStatus
import com.marea.music.domain.model.TrackItem
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaError
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary

@Composable
fun TrackRow(
    track: TrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: ((String) -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDeleteTrack: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onCancelOrRemoveQueue: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(rowShape)
            .then(
                if (isPlaying) Modifier
                    .background(Glass.selected, rowShape)
                    .border(1.dp, Glass.border, rowShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            CoverArt(
                url = track.bestCoverUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
                requestSize = 160
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    NowPlayingBars()
                }
            }
            if (track.isDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Glass.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = "Descargada",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.cleanTitle.ifBlank { track.title },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isPlaying) MareaCyan else MareaText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artistLabel = buildString {
                append(track.artist.ifBlank { "Artista desconocido" })
                if (track.durationSeconds > 0) {
                    append("  ·  ")
                    append(formatDuration(track.durationSeconds))
                }
            }
            Text(
                text = artistLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MareaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.then(
                    if (onArtistClick != null && track.artist.isNotBlank()) {
                        Modifier.clickable { onArtistClick(track.artist) }
                    } else Modifier
                )
            )
        }

        if (!track.isDownloaded) {
            OneClickDownloadButton(
                status = track.downloadStatus,
                progress = track.downloadProgress,
                onClick = onDownloadClick
            )
        }

        if (onCancelOrRemoveQueue != null) {
            IconButton(onClick = onCancelOrRemoveQueue) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancelar o eliminar",
                    tint = MareaError
                )
            }
        }

        val hasMenu = onAddToPlaylist != null ||
            (track.isDownloaded && onDeleteTrack != null) ||
            onRemoveFromPlaylist != null

        if (hasMenu) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Opciones",
                        tint = MareaTextSecondary
                    )
                }
                GlassMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
                    onAddToPlaylist?.let { action ->
                        GlassMenuItem("Agregar a playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                            menuExpanded = false
                            action()
                        }
                    }
                    onRemoveFromPlaylist?.let { action ->
                        GlassMenuItem("Quitar de esta playlist", Icons.Rounded.RemoveCircleOutline) {
                            menuExpanded = false
                            action()
                        }
                    }
                    if (track.isDownloaded) {
                        onDeleteTrack?.let { action ->
                            GlassMenuItem("Eliminar descarga", Icons.Rounded.DeleteOutline, danger = true) {
                                menuExpanded = false
                                action()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Menú desplegable con aspecto de cristal oscuro. */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = Color(0xF2132636),
        border = BorderStroke(1.dp, Glass.border),
        content = content
    )
}

@Composable
fun GlassMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) MareaError else MareaText
    DropdownMenuItem(
        text = { Text(text, color = color, fontWeight = FontWeight.Medium) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (danger) MareaError else MareaCyan) },
        onClick = onClick
    )
}

/**
 * Botón de descarga. En el plan gratis muestra un candado: al tocarlo se avisa que descargar
 * es de Premium (el aviso lo da el ViewModel).
 */
@Composable
fun OneClickDownloadButton(
    status: DownloadStatus,
    progress: Int,
    onClick: () -> Unit
) {
    val isPremium = LocalIsPremium.current
    when (status) {
        DownloadStatus.COMPLETED -> {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = "Ya descargada",
                    tint = MareaCyan,
                    modifier = Modifier.glow(MareaAccent, spread = 2.dp, alpha = 0.35f)
                )
            }
        }

        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.TAGGING -> {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (progress > 0) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(24.dp),
                        color = MareaCyan,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        "$progress",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MareaText,
                        fontSize = 8.sp
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MareaCyan,
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
                    tint = MareaError
                )
            }
        }

        DownloadStatus.NONE -> {
            IconButton(onClick = onClick) {
                Box {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = if (isPremium) "Descargar" else "Descargar (Premium)",
                        tint = if (isPremium) MareaAccent else MareaTextSecondary.copy(alpha = 0.6f)
                    )
                    if (!isPremium) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MareaCyan,
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 3.dp)
                        )
                    }
                }
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
