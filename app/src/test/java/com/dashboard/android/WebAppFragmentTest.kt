package com.dashboard.android

import org.junit.Test
import org.junit.Assert.assertEquals
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAppFragmentTest {

    @Test
    fun testOriginExtraction() {
        val url = "https://music.apple.com/us/browse"
        val uri = Uri.parse(url)
        val origin = "${uri.scheme}://${uri.authority}"
        assertEquals("https://music.apple.com", origin)
    }

    @Test
    fun testOriginExtractionWithPort() {
        val url = "http://localhost:8080/app"
        val uri = Uri.parse(url)
        val origin = "${uri.scheme}://${uri.authority}"
        assertEquals("http://localhost:8080", origin)
    }
}
