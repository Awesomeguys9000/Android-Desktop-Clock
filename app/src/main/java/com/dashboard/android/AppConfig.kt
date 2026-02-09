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
    val customUserAgent: String? = null,
    val isMediaApp: Boolean = false
) {
    companion object {
        // Default apps list
        val defaultApps = listOf(
            AppConfig(
                id = "apple_music",
                name = "Apple Music",
                url = "https://music.apple.com",
                iconResId = R.drawable.ic_music,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                isMediaApp = true
            ),
            AppConfig(
                id = "apple_podcasts",
                name = "Apple Podcasts",
                url = "https://podcasts.apple.com",
                iconResId = R.drawable.ic_podcast,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                isMediaApp = true
            ),
            AppConfig(
                id = "google_calendar",
                name = "Calendar",
                url = "https://calendar.google.com",
                iconResId = R.drawable.ic_calendar,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
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
                id = "outlook",
                name = "Outlook",
                url = "https://outlook.live.com/mail/0/",
                iconResId = R.drawable.ic_outlook,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ),
            AppConfig(
                id = "asana",
                name = "Asana",
                url = "https://app.asana.com",
                iconResId = R.drawable.ic_asana,
                customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
            ),
            AppConfig(
                id = "youtube",
                name = "YouTube",
                url = "https://youtube.com/tv",
                iconResId = R.drawable.ic_youtube,
                customUserAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 5.0) AppleWebKit/537.3 (KHTML, like Gecko) SamsungBrowser/2.2 Chrome/63.0.3239.84 TV Safari/537.3"
            )
        )
    }
}
