package com.dashboard.android

import android.content.Context
import android.content.SharedPreferences
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.dashboard.android.databinding.FragmentClockBinding
import java.text.SimpleDateFormat
import java.util.*

class ClockFragment : Fragment() {

    private var _binding: FragmentClockBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var is24Hour = false
    private var showSeconds = true
    private var clockColor = 0xFFFFFFFF.toInt()
    
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = requireContext().getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)
        loadPreferences()
        
        setupClockDisplay()
        setupSettingsPanel()
        setupMediaControls()
    }

    private fun loadPreferences() {
        is24Hour = prefs.getBoolean("is_24_hour", false)
        showSeconds = prefs.getBoolean("show_seconds", true)
        clockColor = prefs.getInt("clock_color", 0xFFFFFFFF.toInt())
        
        val backgroundStyle = prefs.getString("background_style", "animated_gradient")
        binding.animatedBackground.style = when (backgroundStyle) {
            "solid" -> AnimatedBackgroundView.BackgroundStyle.SOLID
            "gradient" -> AnimatedBackgroundView.BackgroundStyle.GRADIENT
            "particles" -> AnimatedBackgroundView.BackgroundStyle.PARTICLES
            else -> AnimatedBackgroundView.BackgroundStyle.ANIMATED_GRADIENT
        }
    }

    private fun setupClockDisplay() {
        binding.clockText.setTextColor(clockColor)
        binding.dateText.setTextColor(clockColor and 0x80FFFFFF.toInt()) // 50% opacity
        
        // Tap clock to show settings
        binding.clockText.setOnClickListener {
            toggleSettingsPanel()
        }
        
        updateClock()
    }

    private fun updateClock() {
        val now = Date()
        
        // Time format
        val pattern = when {
            is24Hour && showSeconds -> "HH:mm:ss"
            is24Hour && !showSeconds -> "HH:mm"
            !is24Hour && showSeconds -> "h:mm:ss a"
            else -> "h:mm a"
        }
        val timeFormat = SimpleDateFormat(pattern, Locale.getDefault())
        binding.clockText.text = timeFormat.format(now)
        
        // Date format
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        binding.dateText.text = dateFormat.format(now)
    }

    private fun toggleSettingsPanel() {
        val panel = binding.settingsPanel
        if (panel.visibility == View.VISIBLE) {
            panel.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { panel.visibility = View.GONE }
                .start()
        } else {
            panel.alpha = 0f
            panel.visibility = View.VISIBLE
            panel.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun setupSettingsPanel() {
        // 12/24 hour toggle
        binding.toggle24Hour.isChecked = is24Hour
        binding.toggle24Hour.setOnCheckedChangeListener { _, isChecked ->
            is24Hour = isChecked
            prefs.edit().putBoolean("is_24_hour", isChecked).apply()
            updateClock()
        }
        
        // Show seconds toggle
        binding.toggleSeconds.isChecked = showSeconds
        binding.toggleSeconds.setOnCheckedChangeListener { _, isChecked ->
            showSeconds = isChecked
            prefs.edit().putBoolean("show_seconds", isChecked).apply()
            updateClock()
        }
        
        // Color options
        setupColorButtons()
        
        // Background style options
        setupBackgroundOptions()
        
        // Close settings button
        binding.closeSettings.setOnClickListener {
            toggleSettingsPanel()
        }
    }

    private fun setupColorButtons() {
        val colors = mapOf(
            binding.colorWhite to 0xFFFFFFFF.toInt(),
            binding.colorBlue to 0xFF60A5FA.toInt(),
            binding.colorPurple to 0xFFA78BFA.toInt(),
            binding.colorPink to 0xFFF472B6.toInt(),
            binding.colorGreen to 0xFF34D399.toInt(),
            binding.colorOrange to 0xFFFB923C.toInt()
        )
        
        colors.forEach { (button, color) ->
            button.setOnClickListener {
                clockColor = color
                binding.clockText.setTextColor(clockColor)
                binding.dateText.setTextColor(clockColor and 0x80FFFFFF.toInt())
                prefs.edit().putInt("clock_color", color).apply()
            }
        }
    }

    private fun setupBackgroundOptions() {
        binding.bgSolid.setOnClickListener {
            binding.animatedBackground.style = AnimatedBackgroundView.BackgroundStyle.SOLID
            prefs.edit().putString("background_style", "solid").apply()
        }
        binding.bgGradient.setOnClickListener {
            binding.animatedBackground.style = AnimatedBackgroundView.BackgroundStyle.GRADIENT
            prefs.edit().putString("background_style", "gradient").apply()
        }
        binding.bgAnimated.setOnClickListener {
            binding.animatedBackground.style = AnimatedBackgroundView.BackgroundStyle.ANIMATED_GRADIENT
            prefs.edit().putString("background_style", "animated_gradient").apply()
        }
        binding.bgParticles.setOnClickListener {
            binding.animatedBackground.style = AnimatedBackgroundView.BackgroundStyle.PARTICLES
            prefs.edit().putString("background_style", "particles").apply()
        }
    }

    private fun setupMediaControls() {
        binding.btnPlayPause.setOnClickListener {
            getMediaController()?.let { controller ->
                val state = controller.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }
        }
        
        binding.btnSkipNext.setOnClickListener {
            getMediaController()?.transportControls?.skipToNext()
        }
        
        binding.btnSkipPrevious.setOnClickListener {
            getMediaController()?.transportControls?.skipToPrevious()
        }
        
        // Initial media info update
        updateMediaInfo(getMediaController())
    }

    private fun getMediaController(): MediaController? {
        return (activity as? MainActivity)?.getActiveMediaController()
    }

    fun updateMediaInfo(controller: MediaController?) {
        if (_binding == null) return
        
        if (controller != null) {
            val metadata = controller.metadata
            val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            
            if (title.isNotEmpty() || artist.isNotEmpty()) {
                binding.nowPlayingContainer.visibility = View.VISIBLE
                binding.trackTitle.text = title.ifEmpty { getString(R.string.not_playing) }
                binding.trackArtist.text = artist
                
                val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                binding.btnPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            } else {
                binding.nowPlayingContainer.visibility = View.GONE
            }
        } else {
            binding.nowPlayingContainer.visibility = View.GONE
        }
    }

    private var currentController: MediaController? = null
    
    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaInfo(currentController)
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updateMediaInfo(currentController)
        }
    }

    private val sessionListener =  android.media.session.MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        // Update to the new primary controller if available
        val controller = controllers?.firstOrNull()
        if (currentController != controller) {
            currentController?.unregisterCallback(mediaCallback)
            currentController = controller
            currentController?.registerCallback(mediaCallback)
            updateMediaInfo(currentController)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockRunnable)
        
        // Setup media listeners
        val manager = requireContext().getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        try {
            // Need a component name for the listener, using the service we created or just context
            val componentName = android.content.ComponentName(requireContext(), NotificationService::class.java)
            manager.addOnActiveSessionsChangedListener(sessionListener, componentName)
            
            // Set initial controller
            val controllers = manager.getActiveSessions(componentName)
            val controller = controllers.firstOrNull()
            
            if (currentController != controller) {
                currentController?.unregisterCallback(mediaCallback)
                currentController = controller
                currentController?.registerCallback(mediaCallback)
            }
            updateMediaInfo(currentController)
        } catch (e: SecurityException) {
            // Permission might not be granted yet
            updateMediaInfo(null)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
        
        // Cleanup media listeners
        val manager = requireContext().getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        try {
            manager.removeOnActiveSessionsChangedListener(sessionListener)
            currentController?.unregisterCallback(mediaCallback)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentController = null
        _binding = null
    }
}
