package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.MusicDatabase
import com.example.data.local.SongEntity
import com.example.data.repository.MusicRepository
import com.example.playback.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(
    private val context: Context,
    private val repository: MusicRepository
) : ViewModel() {

    private val playbackManager = PlaybackManager.getInstance(context)

    // State flows
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SongEntity>>(emptyList())
    val searchResults: StateFlow<List<SongEntity>> = _searchResults.asStateFlow()

    // Real-time suggestions & autocompletions
    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _aiSuggestions = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestions: StateFlow<List<String>> = _aiSuggestions.asStateFlow()

    // Map to keep track of concurrent download progress (songId -> progress float 0f to 1f)
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    // Room databases state
    val downloadedSongs: StateFlow<List<SongEntity>> = repository.downloadedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<SongEntity>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player state flows mapped from central PlaybackManager
    val currentSong: StateFlow<SongEntity?> = playbackManager.currentSong
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentPositionMs: StateFlow<Long> = playbackManager.currentPositionMs
    val durationMs: StateFlow<Long> = playbackManager.durationMs

    init {
        // Register URL resolver so PlaybackManager can query streams on demand
        playbackManager.streamUrlProvider = { videoId ->
            repository.getStreamOrDownloadUrl(videoId, isAudio = true)
        }
        // Run initial empty search to load default YouTube recommendations
        search("")
    }

    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            val results = repository.searchSongs(query)
            _searchResults.value = results

            // Fetch live autocompletions as they type
            if (query.trim().length >= 2) {
                _searchSuggestions.value = repository.fetchSearchSuggestions(query)
                _aiSuggestions.value = repository.fetchGeminiAISuggestions(query)
            } else {
                _searchSuggestions.value = emptyList()
                _aiSuggestions.value = emptyList()
            }
        }
    }

    fun selectSuggestion(suggestion: String) {
        search(suggestion)
    }

    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
            search(_searchQuery.value)
        }
    }

    fun downloadSong(song: SongEntity) {
        // Default download as MP3 audio for backward compatibility
        downloadMedia(song, "mp3")
    }

    fun downloadMedia(song: SongEntity, type: String) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (song.id to 0f)
            repository.downloadMedia(song, type) { progress ->
                _downloadProgress.value = _downloadProgress.value + (song.id to progress)
            }
            _downloadProgress.value = _downloadProgress.value - song.id
            search(_searchQuery.value)
        }
    }

    fun deleteDownload(song: SongEntity) {
        viewModelScope.launch {
            repository.deleteLocalFile(song)
            search(_searchQuery.value)
        }
    }

    fun playSong(song: SongEntity, listContext: List<SongEntity>) {
        val index = listContext.indexOfFirst { it.id == song.id }
        if (index != -1) {
            playbackManager.setQueueAndPlay(listContext, index)
        } else {
            playbackManager.playSong(song)
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun playNext() {
        playbackManager.playNext()
    }

    fun playPrevious() {
        playbackManager.playPrevious()
    }
}

// Factory Pattern
class MusicViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            val db = MusicDatabase.getDatabase(context)
            val repository = MusicRepository(context, db.songDao)
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
