package com.dashboard.android

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class WebAppFragmentTest {

    @Test
    fun testNewInstanceArguments() {
        val config = AppConfig(
            id = "test_id",
            name = "Test App",
            url = "https://example.com",
            iconResId = 123,
            cssInjection = "body { background: red; }",
            jsInjection = "console.log('test')",
            customUserAgent = "TestUA",
            isMediaApp = true
        )

        val fragment = WebAppFragment.newInstance(config)
        val args = fragment.arguments

        assertNotNull(args)
        assertEquals("test_id", args?.getString(WebAppFragment.ARG_APP_ID))
        assertEquals("Test App", args?.getString(WebAppFragment.ARG_APP_NAME))
        assertEquals("https://example.com", args?.getString(WebAppFragment.ARG_APP_URL))
        assertEquals(123, args?.getInt(WebAppFragment.ARG_ICON_RES_ID))
        assertEquals("body { background: red; }", args?.getString(WebAppFragment.ARG_CSS_INJECTION))
        assertEquals("console.log('test')", args?.getString(WebAppFragment.ARG_JS_INJECTION))
        assertEquals("TestUA", args?.getString(WebAppFragment.ARG_USER_AGENT))
        assertEquals(true, args?.getBoolean(WebAppFragment.ARG_IS_MEDIA_APP))
    }

    @Test
    fun testOnCreateRestoresData() {
        val config = AppConfig(
            id = "test_id",
            name = "Test App",
            url = "https://example.com",
            iconResId = 123,
            cssInjection = "body { background: red; }",
            jsInjection = "console.log('test')",
            customUserAgent = "TestUA",
            isMediaApp = true
        )

        val scenario = launchFragmentInContainer<WebAppFragment>(
            fragmentArgs = WebAppFragment.newInstance(config).arguments
        )

        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertEquals(config, fragment.appConfig)
        }
    }
}
