package com.dashboard.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.dashboard.android.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), MediaSessionManager.OnActiveSessionsChangedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPager: ViewPager2
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeMediaController: MediaController? = null
    private var mediaSession: android.media.session.MediaSession? = null
    
    // WebView cache to keep apps running
    private val webViewCache = mutableMapOf<String, WebAppFragment>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Fullscreen immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        hideSystemUI()
        setupViewPager()
        checkNotificationAccess()
        setupMediaSession()
    }
    
    private fun hideSystemUI() {
        val windowInsetsController = WindowInsetsControllerCompat(window, binding.root)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    private fun setupViewPager() {
        viewPager = binding.viewPager
        viewPager.adapter = DashboardPagerAdapter(this)
        viewPager.setCurrentItem(1, false) // Start on Clock (middle)
        viewPager.offscreenPageLimit = 2 // Keep all pages in memory
    }
    
    private fun checkNotificationAccess() {
        if (!isNotificationServiceEnabled()) {
            showNotificationAccessDialog()
        }
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(packageName) == true
    }
    
    private fun showNotificationAccessDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_access_title)
            .setMessage(R.string.notification_access_message)
            .setPositiveButton(R.string.grant_access) { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }
    
    private fun setupMediaSession() {
        try {
            // Create our own session for WebView control
            mediaSession = android.media.session.MediaSession(this, "DashboardWebSession").apply {
                setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onPlay() {
                        activeWebAppId?.let { webViewCache[it]?.play() }
                        // Update state to playing
                         setPlaybackState(android.media.session.PlaybackState.Builder()
                            .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                            .setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f)
                            .build())
                    }
                    override fun onPause() {
                        activeWebAppId?.let { webViewCache[it]?.pause() }
                         setPlaybackState(android.media.session.PlaybackState.Builder()
                            .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                            .setState(android.media.session.PlaybackState.STATE_PAUSED, 0, 1f)
                            .build())
                    }
                    override fun onSkipToNext() {
                        activeWebAppId?.let { webViewCache[it]?.skipNext() }
                    }
                    override fun onSkipToPrevious() {
                        activeWebAppId?.let { webViewCache[it]?.skipPrevious() }
                    }
                })
                isActive = true
                
                // Initial state
                setPlaybackState(android.media.session.PlaybackState.Builder()
                    .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(android.media.session.PlaybackState.STATE_NONE, 0, 1f)
                    .build())
            }

            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, NotificationService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(this, componentName)
            updateActiveMediaController()
        } catch (e: SecurityException) {
            // Notification access not granted yet
        }
    }
    
    private fun updateActiveMediaController() {
        try {
            val componentName = ComponentName(this, NotificationService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            activeMediaController = controllers?.firstOrNull()
        } catch (e: SecurityException) {
            // Notification access not granted
        }
    }
    
    private var activeWebAppId: String? = null

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        // If our session is active and user is in a web app, don't overwrite with system controllers
        // unless they are actually playing something else (like Spotify app)
        val visibleController = controllers?.firstOrNull()
        
        // If our internal session is the one being reported, ignore recursive updates or handle gracefully
        activeMediaController = visibleController
        
        // Notify ClockFragment about media change
        val clockFragment = supportFragmentManager.findFragmentByTag("f1") as? ClockFragment
        clockFragment?.updateMediaInfo(activeMediaController)
    }
    
    fun getActiveMediaController(): MediaController? = activeMediaController
    
    fun showWebApp(appConfig: AppConfig) {
        activeWebAppId = appConfig.id
        
        // Update Session Metadata
        mediaSession?.setMetadata(android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, appConfig.name)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Web App")
            .build())
            
        // Set state to Playing so controls appear (optimistic)
        mediaSession?.setPlaybackState(android.media.session.PlaybackState.Builder()
                            .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                            .setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f)
                            .build())

        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

        // Hide all existing web fragments
        webViewCache.values.forEach { fragment ->
            if (fragment.isAdded && !fragment.isHidden) {
                transaction.hide(fragment)
            }
        }

        // Get or create cached WebAppFragment
        var fragment = webViewCache[appConfig.id]
        if (fragment == null) {
            fragment = WebAppFragment.newInstance(appConfig)
            webViewCache[appConfig.id] = fragment
            transaction.add(R.id.webAppContainer, fragment, appConfig.id)
        } else {
            transaction.show(fragment)
        }
        
        transaction.commit()
        
        binding.webAppContainer.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
    }
    
    fun restartWebApp(appConfig: AppConfig) {
        val fragment = webViewCache[appConfig.id]
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commit()
            webViewCache.remove(appConfig.id)
            android.widget.Toast.makeText(this, "Restarting ${appConfig.name}...", android.widget.Toast.LENGTH_SHORT).show()
        }
        showWebApp(appConfig)
    }
    
    fun returnToClock() {
        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            
        // Hide all web fragments
        webViewCache.values.forEach { fragment ->
            if (fragment.isAdded && !fragment.isHidden) {
                transaction.hide(fragment)
            }
        }
        transaction.commit()

        binding.webAppContainer.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE
        viewPager.setCurrentItem(1, true) // Go to clock
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webAppContainer.visibility == View.VISIBLE) {
            returnToClock()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        updateActiveMediaController()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(this)
    }
    
    // ViewPager adapter for Notifications, Clock, Launcher
    private inner class DashboardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> NotificationsFragment()
                1 -> ClockFragment()
                2 -> LauncherFragment()
                else -> ClockFragment()
            }
        }
    }
}
