package com.dashboard.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.dashboard.android.databinding.FragmentWebappBinding

class WebAppFragment : Fragment() {

    private var _binding: FragmentWebappBinding? = null
    private val binding get() = _binding!!
    
    internal lateinit var appConfig: AppConfig
    private var webViewInitialized = false

    companion object {
        internal const val ARG_APP_ID = "app_id"
        internal const val ARG_APP_NAME = "app_name"
        internal const val ARG_APP_URL = "app_url"
        internal const val ARG_ICON_RES_ID = "icon_res_id"
        internal const val ARG_CSS_INJECTION = "css_injection"
        internal const val ARG_JS_INJECTION = "js_injection"
        internal const val ARG_USER_AGENT = "user_agent"
        internal const val ARG_IS_MEDIA_APP = "is_media_app"

        fun newInstance(config: AppConfig): WebAppFragment {
            return WebAppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_APP_ID, config.id)
                    putString(ARG_APP_NAME, config.name)
                    putString(ARG_APP_URL, config.url)
                    putInt(ARG_ICON_RES_ID, config.iconResId)
                    putString(ARG_CSS_INJECTION, config.cssInjection)
                    putString(ARG_JS_INJECTION, config.jsInjection)
                    putString(ARG_USER_AGENT, config.customUserAgent)
                    putBoolean(ARG_IS_MEDIA_APP, config.isMediaApp)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            appConfig = AppConfig(
                id = it.getString(ARG_APP_ID, ""),
                name = it.getString(ARG_APP_NAME, ""),
                url = it.getString(ARG_APP_URL, ""),
                iconResId = it.getInt(ARG_ICON_RES_ID, R.drawable.ic_music),
                cssInjection = it.getString(ARG_CSS_INJECTION),
                jsInjection = it.getString(ARG_JS_INJECTION),
                customUserAgent = it.getString(ARG_USER_AGENT),
                isMediaApp = it.getBoolean(ARG_IS_MEDIA_APP, false)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebappBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Back button
        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.returnToClock()
        }
        
        // Setup WebView if not already done
        if (!webViewInitialized) {
            setupWebView()
            webViewInitialized = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            
            // Custom user agent if specified
            appConfig.customUserAgent?.let { ua ->
                userAgentString = ua
            }
        }
        
        // Javascript Interface
        // Use WebViewCompat for secure messaging if supported
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER)) {
            val uri = android.net.Uri.parse(appConfig.url)
            val origin = "${uri.scheme}://${uri.authority}"

            androidx.webkit.WebViewCompat.addWebMessageListener(
                webView,
                "Android",
                setOf(origin),
                androidx.webkit.WebViewCompat.WebMessageListener { _, message, _, _, _ ->
                    try {
                        val data = org.json.JSONObject(message.data ?: "{}")
                        val type = data.optString("type")

                        if (type == "updateMediaMetadata") {
                            val title = data.optString("title")
                            val artist = data.optString("artist")
                            val isPlaying = data.optBoolean("isPlaying")

                            activity?.runOnUiThread {
                                (activity as? MainActivity)?.updateSessionMetadata(title, artist, isPlaying)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore malformed messages
                    }
                }
            )
        }
        
        // Critical for Apple Music/Spotify
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject JavaScript to hide banners AND poll for metadata
                val metadataJs = """
                    (function() {
                        setInterval(function() {
                            try {
                                var title = "";
                                var artist = "";
                                var isPlaying = false;
                                
                                // Apple Music
                                if (window.MusicKit) {
                                    var mk = window.MusicKit.getInstance();
                                    if (mk && mk.nowPlayingItem) {
                                        title = mk.nowPlayingItem.title;
                                        artist = mk.nowPlayingItem.artistName;
                                        isPlaying = mk.isPlaying;
                                    }
                                }
                                
                                // Send to Android
                                if (title && window.Android && window.Android.postMessage) {
                                    window.Android.postMessage(JSON.stringify({
                                        type: "updateMediaMetadata",
                                        title: title,
                                        artist: artist,
                                        isPlaying: isPlaying
                                    }));
                                }
                            } catch(e) {}
                        }, 1000);
                    })();
                """
                view?.evaluateJavascript(metadataJs, null)
                
                appConfig.jsInjection?.let { js ->
                    view?.evaluateJavascript(js, null)
                }
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        
        // Critical: Actually load the page!
        webView.loadUrl(appConfig.url)
    }
    
    init {
        // Ensure hardware acceleration
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Wake up WebView when shown again
            binding.webView.visibility = View.VISIBLE
            binding.webView.requestLayout()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        // Only pause WebView if the fragment is actually being destroyed or activity paused
        // NOT when just hidden
        if (!isHidden) {
            binding.webView.onPause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up
        _binding = null
    }
    fun play() {
        val js = """
            (function() {
                var media = document.querySelectorAll('audio, video');
                if (media.length > 0) { media.forEach(m => m.play()); return; }
                try { window.MusicKit.getInstance().play(); } catch(e) {}
            })();
        """
        if (_binding != null && webViewInitialized) {
            binding.webView.evaluateJavascript(js, null)
        }
    }

    fun pause() {
        val js = """
            (function() {
                var media = document.querySelectorAll('audio, video');
                if (media.length > 0) { media.forEach(m => m.pause()); return; }
                try { window.MusicKit.getInstance().pause(); } catch(e) {}
            })();
        """
         if (_binding != null && webViewInitialized) {
            binding.webView.evaluateJavascript(js, null)
        }
    }

    fun skipNext() {
         val js = """
            (function() {
                try { window.MusicKit.getInstance().skipToNextItem(); return; } catch(e) {}
                var media = document.querySelectorAll('audio, video');
                if (media.length > 0) { media.forEach(m => m.currentTime += 10); }
            })();
        """
         if (_binding != null && webViewInitialized) {
            binding.webView.evaluateJavascript(js, null)
        }
    }

    fun skipPrevious() {
         val js = """
            (function() {
                try { window.MusicKit.getInstance().skipToPreviousItem(); return; } catch(e) {}
                var media = document.querySelectorAll('audio, video');
                if (media.length > 0) { media.forEach(m => m.currentTime -= 10); }
            })();
        """
         if (_binding != null && webViewInitialized) {
            binding.webView.evaluateJavascript(js, null)
        }
    }
}
