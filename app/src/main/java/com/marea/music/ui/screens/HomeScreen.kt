package com.marea.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.marea.music.data.history.TopArtist
import com.marea.music.domain.model.LocalPlaylist
import com.marea.music.domain.model.TrackItem
import com.marea.music.ui.components.ArtworkBackdrop
import com.marea.music.ui.components.CoverArt
import com.marea.music.ui.components.Glass
import com.marea.music.ui.components.GlassIconButton
import com.marea.music.ui.components.GlassSectionTitle
import com.marea.music.ui.components.NeonTag
import com.marea.music.ui.components.NowPlayingBars
import com.marea.music.ui.components.OneClickDownloadButton
import com.marea.music.ui.components.glass
import com.marea.music.ui.components.glassFieldColors
import com.marea.music.ui.components.glow
import com.marea.music.ui.theme.MareaBg
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.BricolageGrotesque
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaCard
import com.marea.music.ui.theme.MareaOnAccent
import com.marea.music.ui.theme.MareaSurface
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary
import java.util.Calendar

@Composable
fun HomeScreen(
    userName: String,
    playlists: List<LocalPlaylist>,
    recommendations: List<TrackItem>,
    isLoadingRecommendations: Boolean,
    topTracks: List<TrackItem>,
    topArtists: List<TopArtist>,
    artistMix: List<TrackItem>,
    isLoadingArtistMix: Boolean,
    currentTrackId: String?,
    onSearchSubmit: (String) -> Unit,
    onRefreshRecommendations: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onDownloadTrack: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayArtist: (String) -> Unit
) {
    var topQuery by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(contentType = "greeting") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                NeonTag("Marea")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = greeting(userName),
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(MareaText, MareaCyan, MareaAccent)),
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    text = "¿Qué quieres escuchar hoy?",
                    color = MareaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item(contentType = "search_bar") {
            OutlinedTextField(
                value = topQuery,
                onValueChange = { topQuery = it },
                placeholder = { Text("Canciones, artistas o un link") },
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
                colors = glassFieldColors(),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        topTracks.firstOrNull()?.let { hero ->
            item(contentType = "hero") {
                HeroCard(
                    track = hero,
                    isPlaying = hero.id == currentTrackId,
                    onPlay = { onPlayTrack(hero, topTracks) },
                    onArtistClick = { onArtistClick(hero.artist) }
                )
            }
        }

        if (topArtists.isNotEmpty()) {
            item(contentType = "artists") {
                Column {
                    SectionHeader("Tus artistas")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(topArtists, key = { it.name }) { artist ->
                            ArtistBubble(
                                artist = artist,
                                onOpen = { onArtistClick(artist.name) },
                                onPlay = { onPlayArtist(artist.name) }
                            )
                        }
                    }
                }
            }
        }

        if (topTracks.isNotEmpty()) {
            item(contentType = "top_header") {
                SectionHeader(
                    title = "Tu top",
                    subtitle = "Lo que más has escuchado",
                    actionIcon = Icons.Rounded.PlayArrow,
                    actionLabel = "Reproducir tu top",
                    onAction = { onPlayTrack(topTracks.first(), topTracks) }
                )
            }
            itemsIndexed(topTracks, key = { _, t -> "top_${t.id}" }, contentType = { _, _ -> "top_row" }) { index, track ->
                CompactTrackRow(
                    track = track,
                    rank = index + 1,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, topTracks) },
                    onArtistClick = { onArtistClick(track.artist) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onDownload = { onDownloadTrack(track) }
                )
            }
        } else {
            item(contentType = "top_empty") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .glass(RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MareaCyan,
                        modifier = Modifier.glow(MareaAccent, spread = 8.dp, alpha = 0.4f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Escucha algunas canciones y aquí aparecerá tu top y más música de tus artistas.",
                        color = MareaTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (artistMix.isNotEmpty() || isLoadingArtistMix) {
            item(contentType = "mix") {
                Column {
                    SectionHeader(
                        title = "De tus artistas",
                        subtitle = "Más canciones de quienes más escuchas",
                        actionIcon = if (artistMix.isNotEmpty()) Icons.Rounded.PlayArrow else null,
                        actionLabel = "Reproducir todo",
                        onAction = { artistMix.firstOrNull()?.let { onPlayTrack(it, artistMix) } }
                    )
                    if (artistMix.isEmpty()) {
                        LoadingBox(height = 170.dp)
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(artistMix, key = { "mix_${it.id}" }) { track ->
                                CoverCard(
                                    title = track.cleanTitle.ifBlank { track.title },
                                    subtitle = track.artist,
                                    coverUrl = track.bestCoverUrl,
                                    highlighted = track.id == currentTrackId,
                                    onClick = { onPlayTrack(track, artistMix) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item(contentType = "playlists") {
                Column {
                    SectionHeader("Tus playlists")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            val cover = playlist.coverUrl.ifBlank {
                                playlist.tracks.firstOrNull { it.bestCoverUrl.isNotBlank() }?.bestCoverUrl.orEmpty()
                            }
                            CoverCard(
                                title = playlist.name,
                                subtitle = "${playlist.tracks.size} canciones",
                                coverUrl = cover,
                                fallbackIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                onClick = {
                                    playlist.tracks.firstOrNull()?.let { first -> onPlayTrack(first, playlist.tracks) }
                                }
                            )
                        }
                    }
                }
            }
        }

        item(contentType = "rec_header") {
            SectionHeader(
                title = "Recomendado para ti",
                actionIcon = Icons.Rounded.Refresh,
                actionLabel = "Actualizar",
                actionFilled = false,
                onAction = onRefreshRecommendations
            )
        }

        if (isLoadingRecommendations && recommendations.isEmpty()) {
            item(contentType = "loading") { LoadingBox(height = 160.dp) }
        } else {
            items(recommendations, key = { "rec_${it.id}" }, contentType = { "rec_row" }) { track ->
                CompactTrackRow(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onPlay = { onPlayTrack(track, recommendations) },
                    onArtistClick = { onArtistClick(track.artist) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onDownload = { onDownloadTrack(track) }
                )
            }
        }
    }
}

private fun greeting(userName: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val salute = when (hour) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val first = userName.trim().substringBefore(' ')
    return if (first.isBlank()) salute else "$salute, $first"
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    actionFilled: Boolean = true,
    onAction: () -> Unit = {}
) {
    GlassSectionTitle(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        trailing = {
            if (actionIcon != null) {
                if (actionFilled) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .glow(MareaAccent, spread = 8.dp, alpha = 0.4f)
                            .clip(CircleShape)
                            .background(Glass.accent, CircleShape)
                            .clickable(onClick = onAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(actionIcon, contentDescription = actionLabel, tint = MareaOnAccent)
                    }
                } else {
                    GlassIconButton(
                        icon = actionIcon,
                        contentDescription = actionLabel,
                        onClick = onAction,
                        size = 40.dp,
                        iconSize = 20.dp,
                        tint = MareaCyan
                    )
                }
            }
        }
    )
}

/** Tarjeta destacada con tu canción más escuchada, sobre su portada difuminada. */
@Composable
private fun HeroCard(
    track: TrackItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onArtistClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(176.dp)
            .clip(shape)
            .border(1.dp, Glass.border, shape)
            .clickable(onClick = onPlay)
    ) {
        ArtworkBackdrop(url = track.bestCoverUrl)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(MareaBg.copy(alpha = 0.55f), Color.Transparent)))
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                NeonTag("Tu #1")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = track.cleanTitle.ifBlank { track.title },
                    color = MareaText,
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = MareaCyan,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = track.artist.isNotBlank(), onClick = onArtistClick)
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .glow(MareaAccent, spread = 10.dp, alpha = 0.5f)
                            .clip(CircleShape)
                            .background(Glass.accent, CircleShape)
                            .clickable(onClick = onPlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir", tint = MareaOnAccent)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (isPlaying) {
                        NowPlayingBars()
                        Spacer(Modifier.width(8.dp))
                        Text("Sonando ahora", color = MareaText, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("Reproducir tu top", color = MareaText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            CoverArt(
                url = track.bestCoverUrl,
                modifier = Modifier
                    .size(116.dp)
                    .glow(MareaAccent, spread = 14.dp, alpha = 0.35f)
                    .border(1.dp, Glass.border, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                requestSize = 360
            )
        }
    }
}

@Composable
private fun LoadingBox(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MareaCyan, strokeWidth = 2.5.dp)
    }
}

@Composable
private fun Cover(url: String, size: Dp, shape: RoundedCornerShape, fallbackIcon: ImageVector = Icons.Rounded.MusicNote) {
    CoverArt(url = url, modifier = Modifier.size(size), shape = shape, requestSize = 360, fallback = fallbackIcon)
}

/** Fila compacta: portada pequeña, título, artista y acciones (en vez de las tarjetas grandes). */
@Composable
private fun CompactTrackRow(
    track: TrackItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onArtistClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    rank: Int? = null
) {
    val rowShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(rowShape)
            .then(
                if (isPlaying) Modifier
                    .background(Glass.selected, rowShape)
                    .border(1.dp, Glass.border, rowShape)
                else Modifier
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                text = rank.toString().padStart(2, '0'),
                style = TextStyle(
                    brush = if (rank <= 3) Glass.accent else Brush.linearGradient(
                        listOf(MareaTextSecondary, MareaTextSecondary)
                    ),
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.width(30.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Cover(url = track.bestCoverUrl, size = 52.dp, shape = RoundedCornerShape(14.dp))
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) { NowPlayingBars() }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.cleanTitle.ifBlank { track.title },
                color = if (isPlaying) MareaCyan else MareaText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = MareaTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = track.artist.isNotBlank(), onClick = onArtistClick)
            )
        }
        IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistAdd,
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

@Composable
private fun CoverCard(
    title: String,
    subtitle: String,
    coverUrl: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote
) {
    Column(
        modifier = Modifier
            .width(152.dp)
            .glass(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box {
            Cover(url = coverUrl, size = 140.dp, shape = RoundedCornerShape(17.dp), fallbackIcon = fallbackIcon)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .glow(MareaAccent, spread = 6.dp, alpha = 0.45f)
                    .clip(CircleShape)
                    .background(Glass.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (highlighted) {
                    NowPlayingBars(color = MareaOnAccent, height = 12.dp, barWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MareaOnAccent, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = if (highlighted) MareaCyan else MareaText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = subtitle,
            color = MareaTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun ArtistBubble(
    artist: TopArtist,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .glow(MareaAccent, spread = 6.dp, alpha = 0.25f)
                    .clip(CircleShape)
                    .border(2.dp, Glass.accent, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(MareaCard),
                contentAlignment = Alignment.Center
            ) {
                if (artist.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = artist.coverUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = MareaTextSecondary)
                }
            }
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = MareaAccent,
                border = androidx.compose.foundation.BorderStroke(2.dp, MareaBg),
                modifier = Modifier
                    .size(30.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Reproducir ${artist.name}",
                        tint = MareaOnAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.name,
            color = MareaText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
