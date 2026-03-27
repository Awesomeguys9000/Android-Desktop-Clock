package com.dashboard.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    // Media3 components
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Legacy system media tracking
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeSystemMediaController: android.media.session.MediaController? = null
    
    // WebView cache to keep apps running
    private val webViewCache = mutableMapOf<String, Fragment>()

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.webAppContainer.visibility == View.VISIBLE) {
                returnToClock()
            }
        }
    }


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, re-show notification for the current state if possible
            lastActiveMediaAppId?.let { id ->
                val appConfig = AppConfig.defaultApps.find { it.id == id }
                if (appConfig != null) {
                    updateSessionMetadata(appConfig.name, "Web App", true)
                }
            }
        }
    }

    // Broadcast receiver to listen for Media3 player actions
    private val webAppCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ACTION_WEB_APP_PLAY" -> {
                    lastActiveMediaAppId?.let { id ->
                        (webViewCache[id] as? WebAppFragment)?.play()
                    }
                }
                "ACTION_WEB_APP_PAUSE" -> {
                    lastActiveMediaAppId?.let { id ->
                        (webViewCache[id] as? WebAppFragment)?.pause()
                    }
                }
            }
        }
    }
    
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
        setupSystemMediaTracking()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val filter = IntentFilter().apply {
            addAction("ACTION_WEB_APP_PLAY")
            addAction("ACTION_WEB_APP_PAUSE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(webAppCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(webAppCommandReceiver, filter)
        }

        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private fun setupSystemMediaTracking() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, NotificationService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(this, componentName)
            updateActiveSystemMediaController()
        } catch (e: SecurityException) {
            // Notification access not granted yet
        }
    }

    private fun updateActiveSystemMediaController() {
        try {
            val componentName = ComponentName(this, NotificationService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            activeSystemMediaController = controllers?.firstOrNull()
        } catch (e: SecurityException) {
            // Notification access not granted
        }
    }

    override fun onActiveSessionsChanged(controllers: MutableList<android.media.session.MediaController>?) {
        val visibleController = controllers?.firstOrNull()
        activeSystemMediaController = visibleController

        // Notify ClockFragment about media change
        val clockFragment = supportFragmentManager.findFragmentByTag("f1") as? ClockFragment
        clockFragment?.updateMediaInfo(activeSystemMediaController)
    }

    override fun onStart() {
        super.onStart()

        val sessionToken = SessionToken(this, ComponentName(this, WebAppMediaSessionService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener(
            {
                mediaController = mediaControllerFuture?.get()
                pendingMediaAction?.invoke()
                pendingMediaAction = null
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        super.onStop()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
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
    
    fun updateSessionMetadata(title: String, artist: String, isPlaying: Boolean) {
        val controller = mediaController
        if (controller == null) {
            // Queue it to run when connected
            pendingMediaAction = { updateSessionMetadata(title, artist, isPlaying) }
            return
        }

        // Send metadata and state broadcast to update WebAppPlayer
        val intent = Intent("ACTION_UPDATE_METADATA").apply {
            setPackage(packageName)
            putExtra("TITLE", title)
            putExtra("ARTIST", artist)
            putExtra("IS_PLAYING", isPlaying)
        }
        sendBroadcast(intent)
    }
    
    private var pendingMediaAction: (() -> Unit)? = null

    private var activeWebAppId: String? = null
    private var lastActiveMediaAppId: String? = null

    fun getActiveMediaController(): android.media.session.MediaController? = activeSystemMediaController
    
    fun getActiveEmbeddedAppName(): String? {
        return AppConfig.defaultApps.find { it.id == lastActiveMediaAppId }?.name
    }

    fun showWebApp(appConfig: AppConfig) {
        activeWebAppId = appConfig.id
        
        // Only update media tracking if this is actually a media app
        if (appConfig.isMediaApp) {
            lastActiveMediaAppId = appConfig.id
            updateSessionMetadata(appConfig.name, "Web App", true)
        }

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
            fragment = if (appConfig.id == "otp_authenticator") {
                OtpFragment()
            } else {
                WebAppFragment.newInstance(appConfig)
            }
            webViewCache[appConfig.id] = fragment
            transaction.add(R.id.webAppContainer, fragment, appConfig.id)
        } else {
            transaction.show(fragment)
        }
        
        transaction.commit()
        
        binding.webAppContainer.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        backPressedCallback.isEnabled = true
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
        backPressedCallback.isEnabled = false
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        updateActiveSystemMediaController()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemUI()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(this)
        try {
            unregisterReceiver(webAppCommandReceiver)
        } catch (e: Exception) {}
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
