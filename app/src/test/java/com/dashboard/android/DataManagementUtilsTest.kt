package com.dashboard.android

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DataManagementUtilsTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testExportImport() {
        // Prepare data
        val prefs = context.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_24_hour", true)
            .putInt("clock_color", 0xFF00FF00.toInt())
            .putString("background_style", "particles")
            .apply()

        // Export
        val json = DataManagementUtils.exportData(context)
        val root = JSONObject(json)

        assertTrue(root.has("shared_prefs"))
        val sharedPrefs = root.getJSONObject("shared_prefs")
        assertTrue(sharedPrefs.has("clock_prefs"))

        val clockPrefsJson = sharedPrefs.getJSONObject("clock_prefs")
        assertEquals(true, clockPrefsJson.getBoolean("is_24_hour"))
        assertEquals(0xFF00FF00.toInt(), clockPrefsJson.getInt("clock_color"))
        assertEquals("particles", clockPrefsJson.getString("background_style"))

        // Clear data
        prefs.edit().clear().apply()
        assertEquals(false, prefs.getBoolean("is_24_hour", false))

        // Import
        val result = DataManagementUtils.importData(context, json)
        assertTrue(result)

        // Verify
        assertEquals(true, prefs.getBoolean("is_24_hour", false))
        assertEquals(0xFF00FF00.toInt(), prefs.getInt("clock_color", 0))
        assertEquals("particles", prefs.getString("background_style", ""))
    }
}
