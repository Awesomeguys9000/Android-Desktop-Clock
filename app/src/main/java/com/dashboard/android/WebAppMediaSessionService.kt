package com.dashboard.android

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.media3.common.MediaMetadata
import androidx.media3.common.SimpleBasePlayer
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class WebAppMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: WebAppPlayer

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player.handlePause()
            }
        }
    }

    private val metadataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_UPDATE_METADATA") {
                val title = intent.getStringExtra("TITLE") ?: "Web App"
                val artist = intent.getStringExtra("ARTIST") ?: "Dashboard"
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)

                val newMetadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()

                player.syncStateFromWebView(newMetadata, isPlaying)
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

        registerReceiver(noisyAudioReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        player = WebAppPlayer(this)

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

        mediaSession = MediaSession.Builder(this, player)
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
        try {
            unregisterReceiver(noisyAudioReceiver)
        } catch (e: Exception) {}

        mediaSession?.run {
            this.player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
