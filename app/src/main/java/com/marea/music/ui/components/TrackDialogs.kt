package com.marea.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.marea.music.domain.model.LocalPlaylist
import com.marea.music.domain.model.TrackItem
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary

/** Ventana de cristal que usan los diálogos de la app. */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glow(MareaAccent, spread = 10.dp, alpha = 0.18f)
                .glass(RoundedCornerShape(28.dp), strong = true)
                .padding(22.dp),
            content = content
        )
    }
}

@Composable
private fun DialogHeader(title: String, subtitle: String?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MareaText
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MareaTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        GlassIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Cerrar",
            onClick = onClose,
            size = 36.dp,
            iconSize = 18.dp,
            tint = MareaTextSecondary
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    track: TrackItem,
    playlists: List<LocalPlaylist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylistWithTrack: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    GlassDialog(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(
                url = track.bestCoverUrl,
                modifier = Modifier.size(56.dp).glow(MareaAccent, spread = 6.dp, alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
                requestSize = 160
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                NeonTag("Agregar a playlist")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = track.cleanTitle.ifBlank { track.title },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MareaText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MareaTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GlassIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Cerrar",
                onClick = onDismiss,
                size = 36.dp,
                iconSize = 18.dp,
                tint = MareaTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (playlists.isEmpty()) {
            Text(
                text = "Aún no tienes playlists. Crea la primera aquí abajo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MareaTextSecondary
            )
        } else {
            Text(
                "Tus playlists",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MareaTextSecondary
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelectPlaylist(pl.id)
                                onDismiss()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cover = pl.coverUrl.ifBlank { pl.tracks.firstOrNull()?.bestCoverUrl.orEmpty() }
                        CoverArt(
                            url = cover,
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(11.dp),
                            requestSize = 120,
                            fallback = Icons.AutoMirrored.Rounded.QueueMusic
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                pl.name,
                                fontWeight = FontWeight.SemiBold,
                                color = MareaText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (pl.tracks.size == 1) "1 canción" else "${pl.tracks.size} canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MareaTextSecondary
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MareaCyan)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("Nueva playlist…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = glassFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .glow(MareaAccent, spread = 4.dp, alpha = if (newName.isNotBlank()) 0.4f else 0f)
                    .clip(CircleShape)
                    .background(if (newName.isNotBlank()) Glass.accent else Glass.fill, CircleShape)
                    .border(1.dp, Glass.border, CircleShape)
                    .clickable(enabled = newName.isNotBlank()) {
                        onCreatePlaylistWithTrack(newName.trim())
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Crear playlist con esta canción",
                    tint = if (newName.isNotBlank()) com.marea.music.ui.theme.MareaOnAccent else MareaTextSecondary
                )
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    GlassDialog(onDismiss = onDismiss) {
        DialogHeader(title = "Nueva playlist", subtitle = "Dale un nombre a tu colección", onClose = onDismiss)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Nombre de tu playlist…") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = glassFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MareaTextSecondary)
            }
            Spacer(Modifier.weight(1f))
            GlowButton(
                text = "Crear",
                icon = Icons.Rounded.Add,
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(name.trim())
                    onDismiss()
                }
            )
        }
    }
}
