package com.marea.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marea.music.AppContainer
import com.marea.music.data.podcast.PodcastEpisode
import com.marea.music.data.podcast.PodcastShow
import com.marea.music.data.podcast.toTrackItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<PodcastShow> = emptyList(),
    val searched: Boolean = false,
    val openedShow: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val error: String? = null
)

class PodcastViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.podcastRepository

    private val _state = MutableStateFlow(PodcastUiState())
    val state: StateFlow<PodcastUiState> = _state.asStateFlow()
    val following: StateFlow<List<PodcastShow>> = repo.following

    private var searchJob: Job? = null
    private var episodesJob: Job? = null

    init {
        viewModelScope.launch { repo.loadFollowing() }
    }

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q, error = null) }
        if (q.isBlank()) _state.update { it.copy(results = emptyList(), searched = false) }
    }

    fun search(q: String = _state.value.query) {
        val term = q.trim()
        if (term.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(isSearching = true, error = null) }
        searchJob = viewModelScope.launch {
            val result = runCatching { repo.search(term) }
            _state.update {
                it.copy(
                    isSearching = false,
                    searched = true,
                    results = result.getOrDefault(emptyList()),
                    error = result.exceptionOrNull()?.let { e -> e.message ?: "No se pudo buscar" }
                )
            }
        }
    }

    fun openShow(show: PodcastShow, refresh: Boolean = false) {
        episodesJob?.cancel()
        _state.update {
            it.copy(
                openedShow = show,
                episodes = if (refresh) it.episodes else emptyList(),
                isLoadingEpisodes = true,
                error = null
            )
        }
        episodesJob = viewModelScope.launch {
            val result = runCatching { repo.episodes(show, forceRefresh = refresh) }
            _state.update {
                if (it.openedShow?.id != show.id) it
                else it.copy(
                    isLoadingEpisodes = false,
                    episodes = result.getOrDefault(it.episodes),
                    error = result.exceptionOrNull()?.let { e -> e.message ?: "No se pudieron cargar los episodios" }
                )
            }
        }
    }

    fun closeShow() {
        episodesJob?.cancel()
        _state.update { it.copy(openedShow = null, episodes = emptyList(), isLoadingEpisodes = false) }
    }

    fun toggleFollow(show: PodcastShow) {
        viewModelScope.launch { repo.toggleFollow(show) }
    }

    /** Toca el episodio; después siguen los episodios anteriores del mismo podcast. */
    fun playEpisode(episode: PodcastEpisode) {
        val show = _state.value.openedShow ?: return
        val queue = _state.value.episodes.map { it.toTrackItem(show) }
        val track = queue.firstOrNull { it.id == episode.id } ?: episode.toTrackItem(show)
        container.playerController.playTrack(track, queue)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PodcastViewModel(container) as T
            }
    }
}
