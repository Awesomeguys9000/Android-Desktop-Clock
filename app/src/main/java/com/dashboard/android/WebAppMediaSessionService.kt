package com.dashboard.android

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class WebAppMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val metadataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_UPDATE_METADATA") {
                val title = intent.getStringExtra("TITLE") ?: "Web App"
                val artist = intent.getStringExtra("ARTIST") ?: "Dashboard"

                val newMetadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()

                player?.let { p ->
                    if (p.mediaItemCount > 0) {
                        val currentItem = p.getMediaItemAt(0)
                        val updatedItem = currentItem.buildUpon()
                            .setMediaMetadata(newMetadata)
                            .build()
                        // Replace the dummy item cleanly, triggering the notification update naturally
                        // without destroying the SilenceMediaSource that ExoPlayer uses to play.
                        p.replaceMediaItem(0, updatedItem)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Register receiver for metadata updates
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(metadataReceiver, IntentFilter("ACTION_UPDATE_METADATA"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(metadataReceiver, IntentFilter("ACTION_UPDATE_METADATA"))
        }

        // Configure audio attributes for focus management
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Provide a dummy silent media source so ExoPlayer reaches STATE_READY
        // and its hardware controls/focus listeners actually work.
        // We wrap it in a dummy MediaItem so replaceMediaItem works later for metadata injection.
        val dummyItem = MediaItem.Builder()
            .setMediaId("webview_media")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Web App")
                    .setArtist("Dashboard")
                    .build()
            )
            .build()

        // Create the source and set the duration
        val silenceSource = SilenceMediaSource.Factory()
            .setDurationUs(1_000_000L * 60 * 60 * 24) // 24 hours of silence
            .setTag(dummyItem)
            .createMediaSource()

        // Set the source and let ExoPlayer extract the tag as the MediaItem
        player?.setMediaSource(silenceSource)
        player?.repeatMode = Player.REPEAT_MODE_ALL // Keep "playing" silence forever
        player?.prepare()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                // Broadcast state to MainActivity
                val action = if (isPlaying) "ACTION_WEB_APP_PLAY" else "ACTION_WEB_APP_PAUSE"
                sendBroadcast(Intent(action).setPackage(packageName))
            }
        })

        // Create intent to open MainActivity when notification is tapped
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setId("WebAppSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Prevent MediaSessionService from auto-stopping when the app is swiped away
        // This ensures the background playback notification stays alive so users can resume it.
        val sessionPlayer = mediaSession?.player
        if (sessionPlayer == null || !sessionPlayer.playWhenReady || sessionPlayer.mediaItemCount == 0) {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // We can expose a custom broadcast receiver inside the service
    // or just rely on MainActivity updating the player's metadata/state.

    override fun onDestroy() {
        try {
            unregisterReceiver(metadataReceiver)
        } catch (e: Exception) {}

        mediaSession?.run {
            this.player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
