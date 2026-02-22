package com.example.greetingcard.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import java.io.File

@UnstableApi
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var playerNotificationManager: PlayerNotificationManager? = null
    private lateinit var exoPlayer: ExoPlayer
    private var isForegroundService = false

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Init ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }

        // 2. Attach MediaSession
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        // 3. Create Notification Channel (Android 8+)
        createNotificationChannel()

        // 4. Setup PlayerNotificationManager
        setupPlayerNotificationManager()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player controls"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupPlayerNotificationManager() {
        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setChannelImportance(NotificationManager.IMPORTANCE_LOW)
            .setMediaDescriptionAdapter(DescriptionAdapter(this))
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {

                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) {
                        // Only start foreground if we're not already in foreground
                        if (!isForegroundService) {
                            startForeground(notificationId, notification)
                            isForegroundService = true
                        }
                    }
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    // Stop foreground and service when notification is dismissed
                    if (isForegroundService) {
                        stopForeground(true)
                        isForegroundService = false
                    }
                    stopSelf()
                }
            })
            .build()

        // Set the player AFTER building the notification manager
        playerNotificationManager?.setPlayer(exoPlayer)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Let MediaSessionService handle the intent first
        super.onStartCommand(intent, flags, startId)

        val playlist = intent?.getStringArrayExtra("playlist")
        val startIndex = intent?.getIntExtra("start_index", 0) ?: 0

        if (playlist != null && playlist.isNotEmpty()) {
            val files = playlist.map { File(it) }.filter { it.exists() }
            if (files.isNotEmpty()) {
                playFiles(files, startIndex)
            }
        }

        return START_NOT_STICKY // Changed from START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun playFiles(files: List<File>, startIndex: Int = 0) {
        val mediaItems = files.map { file ->
            MediaItem.Builder()
                .setUri(file.toURI().toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(file.nameWithoutExtension)
                        .setArtist("Unknown Artist")
                        .build()
                )
                .build()
        }

        exoPlayer.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun onDestroy() {
        playerNotificationManager?.setPlayer(null)
        mediaSession?.release()
        exoPlayer.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "music_channel"
        private const val NOTIFICATION_ID = 1
    }
}