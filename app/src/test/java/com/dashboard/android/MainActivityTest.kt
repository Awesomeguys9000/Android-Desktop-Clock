package com.dashboard.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [MainActivityTest.TrackingShadowNotificationManager::class])
class MainActivityTest {

    @Implements(NotificationManager::class)
    class TrackingShadowNotificationManager : ShadowNotificationManager() {
        var getChannelCount = 0

        @Implementation
        override fun getNotificationChannel(channelId: String): Any? {
            getChannelCount++
            return super.getNotificationChannel(channelId)
        }
    }

    @Test
    fun testShowMediaNotification_redundantChannelCheck() {
        // Setup activity
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val notificationManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Because we can't easily get the shadow instance attached to the system service
        // created inside the Activity (since it might be a different instance wrapper),
        // we need to be careful. However, in Robolectric, getSystemService usually returns
        // the same underlying shadow.

        // Wait, Shadow objects are attached to the real objects.
        // I need to get the shadow of the NotificationManager that the Activity is using.
        // Since the Activity uses `getSystemService`, it gets the same instance as `ApplicationProvider`.

        val shadowNotificationManager = org.robolectric.Shadows.shadowOf(notificationManager) as TrackingShadowNotificationManager

        // Reset count just in case onCreate triggered something
        shadowNotificationManager.getChannelCount = 0

        // Call updateSessionMetadata multiple times
        val iterations = 10
        for (i in 0 until iterations) {
            activity.updateSessionMetadata("Title $i", "Artist $i", true)
        }

        // Assert that getNotificationChannel was NOT called during updates
        assertEquals("getNotificationChannel should NOT be called on updates in optimized code",
            0, shadowNotificationManager.getChannelCount)
    }
}
