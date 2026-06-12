package com.example.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.data.local.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlaybackManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    // State flows for Compose UI
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<SongEntity>>(emptyList())
    val queue: StateFlow<List<SongEntity>> = _queue.asStateFlow()

    private var currentQueueIndex = -1

    var streamUrlProvider: (suspend (String) -> String)? = null

    init {
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setOnCompletionListener {
                playNext()
            }
            setOnErrorListener { mp, what, extra ->
                Log.e("PlaybackManager", "MediaPlayer error: what=$what, extra=$extra")
                _isPlaying.value = false
                stopProgressUpdate()
                true
            }
        }
    }

    fun setQueueAndPlay(songs: List<SongEntity>, startIndex: Int) {
        if (songs.isEmpty()) return
        _queue.value = songs
        currentQueueIndex = startIndex.coerceIn(0, songs.size - 1)
        playSong(songs[currentQueueIndex])
    }

    fun playSong(song: SongEntity) {
        try {
            stopProgressUpdate()
            _isPlaying.value = false
            _currentSong.value = song
            _currentPositionMs.value = 0L

            mediaPlayer?.reset() ?: initializeMediaPlayer()

            mediaPlayer?.setOnPreparedListener { mp ->
                mp.start()
                _isPlaying.value = true
                _durationMs.value = mp.duration.toLong()
                startProgressUpdate()
                // Synchronize playing notification/service if needed
                MusicService.startService(context)
            }

            val hasLocalFile = song.localFilePath?.let { File(it).exists() } ?: false

            if (hasLocalFile) {
                Log.d("PlaybackManager", "Playing offline song: ${song.title} from: ${song.localFilePath}")
                val fileUri = Uri.fromFile(File(song.localFilePath!!))
                mediaPlayer?.setDataSource(context, fileUri)
                mediaPlayer?.prepareAsync()
            } else {
                Log.d("PlaybackManager", "Streaming online song: ${song.title}, resolving URL...")
                scope.launch {
                    try {
                        val resolvedUrl = streamUrlProvider?.invoke(song.id) ?: song.streamUrl
                        Log.d("PlaybackManager", "Streaming online song resolved url: $resolvedUrl")
                        if (_currentSong.value?.id == song.id) {
                            mediaPlayer?.setDataSource(resolvedUrl)
                            mediaPlayer?.prepareAsync()
                        }
                    } catch (e: java.lang.Exception) {
                        Log.e("PlaybackManager", "Error preparing streaming media datasource", e)
                        _isPlaying.value = false
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("PlaybackManager", "Error playing song: ${song.title}", e)
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                stopProgressUpdate()
            } else {
                if (_currentSong.value != null) {
                    mp.start()
                    _isPlaying.value = true
                    startProgressUpdate()
                    MusicService.startService(context)
                } else {
                    _queue.value.firstOrNull()?.let { playSong(it) }
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        }
    }

    fun playNext() {
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        currentQueueIndex = (currentQueueIndex + 1) % currentQueue.size
        playSong(currentQueue[currentQueueIndex])
    }

    fun playPrevious() {
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        currentQueueIndex = if (currentQueueIndex - 1 < 0) {
            currentQueue.size - 1
        } else {
            currentQueueIndex - 1
        }
        playSong(currentQueue[currentQueueIndex])
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPositionMs.value = mp.currentPosition.toLong()
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdate()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
