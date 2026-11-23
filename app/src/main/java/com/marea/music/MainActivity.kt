package com.marea.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marea.music.ui.components.Glass
import com.marea.music.ui.components.LocalIsPremium
import com.marea.music.ui.components.MareaBackdrop
import com.marea.music.ui.components.LocalPlaybackPosition
import com.marea.music.ui.components.glow
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.marea.music.data.access.AccessState
import com.marea.music.data.access.hasAccess
import com.marea.music.data.extractor.YouTubeUrlParser
import com.marea.music.domain.model.TrackItem
import com.marea.music.ui.MareaViewModel
import com.marea.music.ui.components.AddToPlaylistDialog
import com.marea.music.ui.components.ArtistSheet
import com.marea.music.ui.components.CreatePlaylistDialog
import com.marea.music.ui.components.EqualizerSheet
import com.marea.music.ui.components.FullPlayerSheet
import com.marea.music.ui.components.MiniPlayer
import com.marea.music.ui.screens.AccessGate
import com.marea.music.ui.screens.HomeScreen
import com.marea.music.ui.screens.LibraryScreen
import com.marea.music.ui.screens.SearchScreen
import com.marea.music.ui.screens.SubscriptionScreen
import com.marea.music.ui.theme.MareaTextSecondary
import com.marea.music.ui.theme.MareaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MareaViewModel by viewModels {
        MareaViewModel.factory((application as MareaApp).container)
    }

    private val container get() = (application as MareaApp).container

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermissionIfNeeded()

        // Premium según la suscripción; si se cierra la sesión, detener la reproducción.
        lifecycleScope.launch {
            container.accessRepository.state.collect { state ->
                container.playerController.setPremium(state.hasAccess)
                if (state is AccessState.SignedOut) container.playerController.pause()
            }
        }

        val initialSharedUrl = extractUrlFromIntent(intent)

        setContent {
            MareaTheme {
                MareaBackdrop {
                AccessGate(access = container.accessRepository) {
                    var selectedTab by remember {
                        mutableIntStateOf(if (initialSharedUrl != null) 1 else 0)
                    }
                    var showFullPlayer by remember { mutableStateOf(false) }
                    var trackForPlaylist by remember { mutableStateOf<TrackItem?>(null) }
                    var showCreatePlaylist by remember { mutableStateOf(false) }
                    var showEqualizer by remember { mutableStateOf(false) }

                    val snackbarHostState = remember { SnackbarHostState() }

                    val searchState by viewModel.mergedSearchState.collectAsState()
                    val playbackState by viewModel.playbackState.collectAsState()
                    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
                    val activeDownloads by viewModel.activeDownloads.collectAsState()
                    val localPlaylists by viewModel.localPlaylists.collectAsState()
                    val savedTracksMap by viewModel.allSavedTracksMap.collectAsState()
                    val recommendations by viewModel.recommendations.collectAsState()
                    val isLoadingRecs by viewModel.isLoadingRecommendations.collectAsState()
                    val artistState by viewModel.artistState.collectAsState()
                    val lyrics by viewModel.lyricsState.collectAsState()
                    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
                    val uiMessage by viewModel.uiMessage.collectAsState()
                    val accessState by container.accessRepository.state.collectAsState()
                    val topTracks by viewModel.topTracks.collectAsState()
                    val topArtists by viewModel.topArtists.collectAsState()
                    val artistMix by viewModel.artistMix.collectAsState()
                    val isLoadingArtistMix by viewModel.isLoadingArtistMix.collectAsState()

                    LaunchedEffect(initialSharedUrl) {
                        if (initialSharedUrl != null) {
                            viewModel.submitSearchOrLink(initialSharedUrl)
                        }
                    }

                    LaunchedEffect(playbackState.errorMessage) {
                        playbackState.errorMessage?.let { msg ->
                            snackbarHostState.showSnackbar(msg)
                            (application as MareaApp).container.playerController.clearError()
                        }
                    }

                    LaunchedEffect(uiMessage) {
                        uiMessage?.let { msg ->
                            snackbarHostState.showSnackbar(msg)
                            viewModel.clearUiMessage()
                        }
                    }

                    CompositionLocalProvider(
                        LocalIsPremium provides playbackState.isPremium,
                        LocalPlaybackPosition provides container.playerController.position
                    ) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentColor = MareaText,
                        snackbarHost = {
                            SnackbarHost(snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    shape = RoundedCornerShape(18.dp),
                                    containerColor = Color(0xF2132636),
                                    contentColor = MareaText,
                                    actionColor = MareaCyan,
                                    modifier = Modifier.border(1.dp, Glass.border, RoundedCornerShape(18.dp))
                                )
                            }
                        },
                        bottomBar = {
                            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                                MiniPlayer(
                                    playbackState = playbackState,
                                    onTogglePlayPause = viewModel::togglePlayPause,
                                    onExpand = { showFullPlayer = true }
                                )
                                GlassNavBar(
                                    selected = selectedTab,
                                    onSelect = { selectedTab = it }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (selectedTab) {
                                0 -> HomeScreen(
                                    userName = (accessState as? AccessState.SignedIn)?.name.orEmpty(),
                                    playlists = localPlaylists,
                                    recommendations = recommendations,
                                    isLoadingRecommendations = isLoadingRecs,
                                    topTracks = topTracks,
                                    topArtists = topArtists,
                                    artistMix = artistMix,
                                    isLoadingArtistMix = isLoadingArtistMix,
                                    currentTrackId = playbackState.currentTrack?.id,
                                    onSearchSubmit = { query ->
                                        selectedTab = 1
                                        viewModel.submitSearchOrLink(query)
                                    },
                                    onRefreshRecommendations = viewModel::refreshRecommendations,
                                    onPlayTrack = viewModel::playTrack,
                                    onDownloadTrack = viewModel::downloadSingleTrack,
                                    onAddToPlaylist = { trackForPlaylist = it },
                                    onArtistClick = viewModel::openArtistPage,
                                    onPlayArtist = viewModel::playArtist
                                )

                                1 -> SearchScreen(
                                    state = searchState,
                                    currentTrackId = playbackState.currentTrack?.id,
                                    onQueryChange = viewModel::updateQuery,
                                    onOriginalsFilterChange = viewModel::setOriginalsOnly,
                                    onSubmitSearch = viewModel::submitSearchOrLink,
                                    onPlayTrack = viewModel::playTrack,
                                    onDownloadSingle = viewModel::downloadSingleTrack,
                                    onArtistClick = viewModel::openArtistPage,
                                    onAddToPlaylist = { trackForPlaylist = it },
                                    onDeleteTrack = { viewModel.deleteDownloadedTrack(it.id) },
                                    onTogglePlaylistSelection = viewModel::togglePlaylistSelection,
                                    onSelectAllPlaylist = viewModel::selectAllPlaylistTracks,
                                    onDownloadSelectedPlaylist = viewModel::downloadSelectedPlaylist,
                                    onClearPlaylist = viewModel::clearPlaylistPreview,
                                    onOpenPlaylist = viewModel::openSearchPlaylist
                                )

                                2 -> LibraryScreen(
                                    downloadedTracks = downloadedTracks,
                                    activeDownloads = activeDownloads,
                                    playlists = localPlaylists,
                                    currentTrackId = playbackState.currentTrack?.id,
                                    onPlayTrack = viewModel::playTrack,
                                    onPlayShuffled = { tracks ->
                                        if (tracks.isNotEmpty()) {
                                            if (!playbackState.shuffleEnabled) {
                                                viewModel.toggleShuffle()
                                            }
                                            viewModel.playTrack(tracks.random(), tracks)
                                        }
                                    },
                                    onRetryDownload = viewModel::downloadSingleTrack,
                                    onCancelQueueTrack = { viewModel.cancelOrRemoveQueuedDownload(it.id) },
                                    onArtistClick = viewModel::openArtistPage,
                                    onAddToPlaylist = { trackForPlaylist = it },
                                    onDeleteTrack = { viewModel.deleteDownloadedTrack(it.id) },
                                    onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                    onDeletePlaylist = viewModel::deletePlaylist,
                                    onCreateNewPlaylist = { showCreatePlaylist = true }
                                )

                                3 -> (accessState as? AccessState.SignedIn)?.let { signedIn ->
                                    SubscriptionScreen(
                                        access = container.accessRepository,
                                        state = signedIn
                                    )
                                }
                            }
                        }
                    }

                    if (showFullPlayer && playbackState.currentTrack != null) {
                        val synced = playbackState.currentTrack?.let { savedTracksMap[it.id] ?: it }
                        FullPlayerSheet(
                            playbackState = playbackState,
                            syncedTrack = synced,
                            lyrics = lyrics,
                            isLoadingLyrics = isLoadingLyrics,
                            onDismiss = { showFullPlayer = false },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::playNext,
                            onPrevious = viewModel::playPrevious,
                            onSeekTo = viewModel::seekTo,
                            onToggleShuffle = viewModel::toggleShuffle,
                            onCycleRepeatMode = viewModel::cycleRepeatMode,
                            onDownloadClick = viewModel::downloadSingleTrack,
                            onAddToPlaylist = { trackForPlaylist = it },
                            onArtistClick = { artist ->
                                showFullPlayer = false
                                viewModel.openArtistPage(artist)
                            },
                            onOpenEqualizer = { showEqualizer = true }
                        )
                    }

                    if (showEqualizer) {
                        EqualizerSheet(onDismiss = { showEqualizer = false })
                    }

                    artistState?.let { currentArtist ->
                        ArtistSheet(
                            state = currentArtist,
                            currentTrackId = playbackState.currentTrack?.id,
                            onDismiss = viewModel::closeArtistPage,
                            onPlayTrack = viewModel::playTrack,
                            onPlayShuffled = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    if (!playbackState.shuffleEnabled) {
                                        viewModel.toggleShuffle()
                                    }
                                    viewModel.playTrack(tracks.random(), tracks)
                                }
                            },
                            onDownloadTrack = viewModel::downloadSingleTrack,
                            onAddToPlaylist = { trackForPlaylist = it },
                            onDeleteTrack = { viewModel.deleteDownloadedTrack(it.id) }
                        )
                    }

                    trackForPlaylist?.let { target ->
                        AddToPlaylistDialog(
                            track = target,
                            playlists = localPlaylists,
                            onDismiss = { trackForPlaylist = null },
                            onSelectPlaylist = { plId -> viewModel.addTrackToPlaylist(plId, target) },
                            onCreatePlaylistWithTrack = { name -> viewModel.createPlaylist(name, target) }
                        )
                    }

                    if (showCreatePlaylist) {
                        CreatePlaylistDialog(
                            onDismiss = { showCreatePlaylist = false },
                            onCreate = { name -> viewModel.createPlaylist(name, null) }
                        )
                    }
                    }
                }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { container.accessRepository.verify() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (container.accessRepository.state.value !is AccessState.SignedIn) return
        extractUrlFromIntent(intent)?.let { url ->
            viewModel.submitSearchOrLink(url)
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { YouTubeUrlParser.extractFirstUrl(it) }
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private data class NavDestination(val label: String, val icon: ImageVector)

private val NAV_DESTINATIONS = listOf(
    NavDestination("Inicio", Icons.Rounded.Home),
    NavDestination("Buscar", Icons.Rounded.Search),
    NavDestination("Biblioteca", Icons.Rounded.LibraryMusic),
    NavDestination("Cuenta", Icons.Rounded.AccountCircle)
)

/** Barra de navegación flotante de cristal; la pestaña activa se ilumina. */
@Composable
private fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(Glass.fillStrong, shape)
            .border(1.dp, Glass.border, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NAV_DESTINATIONS.forEachIndexed { index, dest ->
            val isSelected = index == selected
            val itemShape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .then(
                        if (isSelected) Modifier.background(Glass.selected, itemShape) else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = dest.icon,
                    contentDescription = dest.label,
                    tint = if (isSelected) MareaCyan else MareaTextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .then(if (isSelected) Modifier.glow(MareaAccent, spread = 8.dp, alpha = 0.45f) else Modifier)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = dest.label,
                    color = if (isSelected) MareaText else MareaTextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}
