package com.marea.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marea.music.data.podcast.PodcastEpisode
import com.marea.music.data.podcast.PodcastProgressStore
import com.marea.music.data.podcast.PodcastShow
import com.marea.music.ui.PodcastUiState
import com.marea.music.ui.components.CoverArt
import com.marea.music.ui.components.Glass
import com.marea.music.ui.components.GlassCard
import com.marea.music.ui.components.GlassIconButton
import com.marea.music.ui.components.GlassSectionTitle
import com.marea.music.ui.components.GlowButton
import com.marea.music.ui.components.NeonTag
import com.marea.music.ui.components.NowPlayingBars
import com.marea.music.ui.components.glass
import com.marea.music.ui.components.glassFieldColors
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PodcastsScreen(
    state: PodcastUiState,
    following: List<PodcastShow>,
    currentTrackId: String?,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenShow: (PodcastShow) -> Unit,
    onRefreshShow: (PodcastShow) -> Unit,
    onCloseShow: () -> Unit,
    onToggleFollow: (PodcastShow) -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit
) {
    val opened = state.openedShow
    if (opened != null) {
        BackHandler(onBack = onCloseShow)
        ShowDetail(
            show = opened,
            state = state,
            isFollowing = following.any { it.id == opened.id },
            currentTrackId = currentTrackId,
            onBack = onCloseShow,
            onRefresh = { onRefreshShow(opened) },
            onToggleFollow = { onToggleFollow(opened) },
            onPlayEpisode = onPlayEpisode
        )
        return
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val shows = if (state.query.isNotBlank() && state.searched) state.results else following

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item(key = "header", contentType = "header") {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
                Text(
                    text = "Podcasts",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MareaText
                )
                Text(
                    text = "Busca tus programas favoritos y síguelos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MareaTextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Buscar podcasts") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        onSearch(state.query)
                    }),
                    colors = glassFieldColors(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        when {
            state.isSearching -> item(key = "loading", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MareaCyan, strokeWidth = 2.5.dp)
                }
            }

            state.error != null && state.query.isNotBlank() -> item(key = "error", contentType = "message") {
                MessageCard(state.error)
            }

            shows.isEmpty() -> item(key = "empty", contentType = "message") {
                MessageCard(
                    if (state.query.isNotBlank() && state.searched) "No encontramos podcasts con ese nombre."
                    else "Aún no sigues ningún podcast. Búscalo arriba y toca Seguir para tenerlo aquí."
                )
            }

            else -> {
                item(key = "section", contentType = "section") {
                    GlassSectionTitle(
                        title = if (state.query.isNotBlank() && state.searched) "Resultados" else "Siguiendo",
                        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
                    )
                }
                items(shows, key = { "show_" + it.id }, contentType = { "show" }) { show ->
                    ShowRow(show = show, onClick = {
                        keyboard?.hide()
                        onOpenShow(show)
                    })
                }
            }
        }
    }
}

@Composable
private fun ShowRow(show: PodcastShow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(
            url = show.artworkUrl,
            modifier = Modifier.size(58.dp),
            shape = RoundedCornerShape(15.dp),
            requestSize = 180,
            fallback = Icons.Rounded.Podcasts
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.title,
                color = MareaText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = show.author,
                color = MareaTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShowDetail(
    show: PodcastShow,
    state: PodcastUiState,
    isFollowing: Boolean,
    currentTrackId: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFollow: () -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit
) {
    // Se actualiza cada pocos segundos mientras suena un episodio (no en cada tic).
    val progress by PodcastProgressStore.progress.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item(key = "hero", contentType = "hero") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                shape = RoundedCornerShape(28.dp),
                contentPadding = 16.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        onClick = onBack,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Buscar episodios nuevos",
                        onClick = onRefresh,
                        size = 38.dp,
                        iconSize = 20.dp,
                        tint = MareaCyan
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(
                        url = show.artworkUrl,
                        modifier = Modifier.size(96.dp),
                        shape = RoundedCornerShape(22.dp),
                        requestSize = 360,
                        fallback = Icons.Rounded.Podcasts
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        NeonTag("Podcast")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = show.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MareaText,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = show.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MareaTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlowButton(
                    text = if (isFollowing) "Siguiendo" else "Seguir",
                    icon = if (isFollowing) Icons.Rounded.CheckCircle else Icons.Rounded.Podcasts,
                    onClick = onToggleFollow,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        when {
            state.isLoadingEpisodes && state.episodes.isEmpty() -> item(key = "loading", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MareaCyan, strokeWidth = 2.5.dp)
                }
            }

            state.episodes.isEmpty() -> item(key = "empty", contentType = "message") {
                MessageCard(state.error ?: "Este podcast no tiene episodios disponibles.")
            }

            else -> {
                item(key = "section", contentType = "section") {
                    GlassSectionTitle(
                        title = "Episodios",
                        subtitle = "${state.episodes.size}",
                        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
                    )
                }
                items(state.episodes, key = { it.id }, contentType = { "episode" }) { ep ->
                    EpisodeRow(
                        episode = ep,
                        savedMs = progress[ep.id] ?: 0L,
                        isCurrent = ep.id == currentTrackId,
                        onClick = { onPlayEpisode(ep) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    savedMs: Long,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val finished = savedMs < 0L
    val meta = remember(episode.id, episode.publishedAt, episode.durationSeconds) {
        listOfNotNull(
            episode.publishedAt.takeIf { it > 0 }?.let { formatDate(it) },
            episode.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it) }
        ).joinToString(" · ")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .glass(RoundedCornerShape(20.dp))
            .then(if (isCurrent) Modifier.background(Glass.selected, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCurrent) {
                NowPlayingBars(height = 12.dp, barWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = episode.title,
                color = if (finished) MareaTextSecondary else MareaText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (finished) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Escuchado", tint = MareaCyan, modifier = Modifier.size(18.dp))
            }
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(meta, color = MareaTextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        if (episode.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                episode.description,
                color = MareaTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val total = episode.durationSeconds * 1000L
        if (!finished && savedMs > 0L && total > 0L) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((savedMs.toFloat() / total).coerceIn(0.02f, 1f))
                        .height(3.dp)
                        .background(Glass.accentHorizontal)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Te quedaste en ${formatDuration(savedMs / 1000)}",
                color = MareaCyan,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Icon(Icons.Rounded.Podcasts, contentDescription = null, tint = MareaCyan)
        Spacer(Modifier.height(10.dp))
        Text(message, color = MareaTextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale("es")).format(Date(ms))

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "$h h $m min"
        m > 0 -> "$m min"
        else -> "${seconds % 60} s"
    }
}
