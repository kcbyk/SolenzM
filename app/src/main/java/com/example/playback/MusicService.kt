package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MusicService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var playbackManager: PlaybackManager

    override fun onCreate() {
        super.onCreate()
        playbackManager = PlaybackManager.getInstance(this)
        createNotificationChannel()
        observePlaybackState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> playbackManager.togglePlayPause()
                ACTION_NEXT -> playbackManager.playNext()
                ACTION_PREV -> playbackManager.playPrevious()
                ACTION_STOP -> {
                    playbackManager.togglePlayPause() // pauses
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            playbackManager.currentSong.collectLatest { song ->
                updateNotification()
            }
        }
        serviceScope.launch {
            playbackManager.isPlaying.collectLatest { playing ->
                updateNotification()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Solenz Müzik Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Müzik çalma kontrolleri"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val song = playbackManager.currentSong.value
        val isPlaying = playbackManager.isPlaying.value

        val openActivityIntent = Intent(this, MainActivity::class.java)
        val openActivityPendingIntent = PendingIntent.getActivity(
            this, 0, openActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (song == null) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Solenz Müzik")
                .setContentText("Müzik çalmaya hazır")
                .setContentIntent(openActivityPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        } else {
            // Broadcast Intents for widgets/controls
            val playPausePendingIntent = createPendingIntent(ACTION_PLAY_PAUSE)
            val nextPendingIntent = createPendingIntent(ACTION_NEXT)
            val prevPendingIntent = createPendingIntent(ACTION_PREV)
            val stopPendingIntent = createPendingIntent(ACTION_STOP)

            val playPauseIcon = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }

            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setContentIntent(openActivityPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Controls Actions
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
                .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
                .addAction(android.R.drawable.ic_delete, "Close", stopPendingIntent)
                .setOngoing(isPlaying)
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "myt_music_channel"
        private const val NOTIFICATION_ID = 413
        const val ACTION_PLAY_PAUSE = "com.example.playback.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.playback.action.NEXT"
        const val ACTION_PREV = "com.example.playback.action.PREV"
        const val ACTION_STOP = "com.example.playback.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, MusicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
