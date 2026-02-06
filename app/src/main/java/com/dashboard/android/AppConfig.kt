package com.dashboard.android

/**
 * Configuration for web apps in the launcher
 */
data class AppConfig(
    val id: String,
    val name: String,
    val url: String,
    val iconResId: Int,
    val cssInjection: String? = null,
    val jsInjection: String? = null,
    val customUserAgent: String? = null
) {
    companion object {
        // JavaScript to hide Apple Music "Get on Google Play" banner
        private const val APPLE_MUSIC_JS = """
            (function() {
                function hideElements() {
                    // Hide app store banners
                    var selectors = [
                        '.we-banner',
                        '.smartbanner',
                        '[class*="download"]',
                        '[class*="app-banner"]',
                        '[class*="store-badge"]',
                        '.upsell-banner',
                        '[class*="native-app"]',
                        '#web-navigation-container' // Sometimes contains 'Get on Play Store' in mobile view
                    ];
                    selectors.forEach(function(sel) {
                        try {
                            var elements = document.querySelectorAll(sel);
                            elements.forEach(function(el) {
                                el.style.display = 'none';
                                el.style.visibility = 'hidden'; // Double tap
                            });
                        } catch(e) {}
                    });
                }
                // Run repeatedly for SPA navigation
                hideElements();
                setInterval(hideElements, 1000); // Check every second
            })();
        """
        
        // Default apps list
        val defaultApps = listOf(
            AppConfig(
                id = "apple_music",
                name = "Apple Music",
                url = "https://music.apple.com",
                iconResId = R.drawable.ic_music,
                jsInjection = APPLE_MUSIC_JS,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ),
            AppConfig(
                id = "apple_podcasts",
                name = "Apple Podcasts",
                url = "https://podcasts.apple.com",
                iconResId = R.drawable.ic_podcast,
                jsInjection = APPLE_MUSIC_JS,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ),
            AppConfig(
                id = "google_calendar",
                name = "Calendar",
                url = "https://calendar.google.com",
                iconResId = R.drawable.ic_calendar,
                customUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            ),
            AppConfig(
                id = "google_tasks",
                name = "Tasks",
                url = "https://tasks.google.com",
                iconResId = R.drawable.ic_tasks
            ),
            AppConfig(
                id = "google_keep",
                name = "Keep",
                url = "https://keep.google.com",
                iconResId = R.drawable.ic_keep
            ),
            AppConfig(
                id = "youtube_music",
                name = "YouTube Music",
                url = "https://music.youtube.com",
                iconResId = R.drawable.ic_youtube_music
            )
        )
    }
}
