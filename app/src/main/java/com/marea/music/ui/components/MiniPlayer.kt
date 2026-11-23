package com.marea.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marea.music.player.PlaybackUiState
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaOnAccent
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary

/** Reproductor flotante de cristal sobre la barra de navegación. */
@Composable
fun MiniPlayer(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val position = LocalPlaybackPosition.current.collectAsState()
    val durationMs = playbackState.durationMs
    val context = LocalContext.current
    // Deja lista la portada grande para que el reproductor abra sin esperar la imagen.
    LaunchedEffect(track.bestCoverUrl) {
        if (track.bestCoverUrl.isNotBlank()) {
            context.imageLoader.enqueue(
                ImageRequest.Builder(context).data(track.bestCoverUrl).size(900).build()
            )
        }
    }
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(Glass.fillStrong, shape)
            .background(Glass.selected, shape)
            .border(1.dp, Glass.border, shape)
            .clickable(onClick = onExpand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArt(
                url = track.bestCoverUrl,
                modifier = Modifier
                    .size(46.dp)
                    .glow(MareaAccent, spread = 6.dp, alpha = if (playbackState.isPlaying) 0.45f else 0.15f),
                shape = RoundedCornerShape(14.dp),
                requestSize = 160
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.cleanTitle.ifBlank { track.title },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MareaText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playbackState.isPlaying) {
                        NowPlayingBars(height = 9.dp, barWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (playbackState.isAd) "Anuncio" else track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playbackState.isAd) MareaCyan else MareaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .glow(MareaAccent, spread = 6.dp, alpha = 0.35f)
                    .clip(CircleShape)
                    .background(Glass.accent, CircleShape)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                if (playbackState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MareaOnAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                        tint = MareaOnAccent
                    )
                }
            }
        }

        // Progreso: línea luminosa en el borde inferior.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = if (durationMs > 0) (position.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        scaleX = p
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Glass.accentHorizontal)
            )
        }
    }
}
