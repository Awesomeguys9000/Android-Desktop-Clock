package com.dashboard.android

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class WebAppPlayer(private val context: android.content.Context) : SimpleBasePlayer(android.os.Looper.getMainLooper()) {

    private var isCurrentlyPlaying = false
    private var currentMediaItem = MediaItem.Builder().setMediaId("webview_media").build()

    override fun getState(): State {
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SET_MEDIA_ITEM,
                        Player.COMMAND_STOP
                    )
                    .build()
            )
            .setPlayWhenReady(isCurrentlyPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(listOf(MediaItemData.Builder(currentMediaItem.mediaId).setMediaItem(currentMediaItem).build()))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        isCurrentlyPlaying = playWhenReady
        invalidateState()

        val action = if (playWhenReady) "ACTION_WEB_APP_PLAY" else "ACTION_WEB_APP_PAUSE"
        context.sendBroadcast(Intent(action).setPackage(context.packageName))

        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        isCurrentlyPlaying = false
        invalidateState()
        context.sendBroadcast(Intent("ACTION_WEB_APP_PAUSE").setPackage(context.packageName))
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        if (mediaItems.isNotEmpty()) {
            currentMediaItem = mediaItems[0]
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }

    // External method to dynamically sync metadata/playback from WebView to this Player state
    // without broadcasting back and causing infinite loops
    fun syncStateFromWebView(metadata: androidx.media3.common.MediaMetadata, isPlaying: Boolean) {
        currentMediaItem = currentMediaItem.buildUpon().setMediaMetadata(metadata).build()
        isCurrentlyPlaying = isPlaying
        invalidateState()
    }

    fun handlePause() {
        isCurrentlyPlaying = false
        invalidateState()
        context.sendBroadcast(Intent("ACTION_WEB_APP_PAUSE").setPackage(context.packageName))
    }
}
