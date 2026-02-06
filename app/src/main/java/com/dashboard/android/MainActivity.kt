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
    
    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        activeMediaController = controllers?.firstOrNull()
        // Notify ClockFragment about media change
        val clockFragment = supportFragmentManager.findFragmentByTag("f1") as? ClockFragment
        clockFragment?.updateMediaInfo(activeMediaController)
    }
    
    fun getActiveMediaController(): MediaController? = activeMediaController
    
    fun showWebApp(appConfig: AppConfig) {
        // Get or create cached WebAppFragment
        val fragment = webViewCache.getOrPut(appConfig.id) {
            WebAppFragment.newInstance(appConfig)
        }
        
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .add(R.id.webAppContainer, fragment, appConfig.id)
            .addToBackStack(appConfig.id)
            .commit()
        
        binding.webAppContainer.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
    }
    
    fun returnToClock() {
        supportFragmentManager.popBackStack()
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
