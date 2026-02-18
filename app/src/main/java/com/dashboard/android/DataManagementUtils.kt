package com.dashboard.android

import android.content.Context
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject

object DataManagementUtils {

    private const val KEY_SHARED_PREFS = "shared_prefs"
    private const val KEY_COOKIES = "cookies"
    private const val CLOCK_PREFS_NAME = "clock_prefs"

    fun exportData(context: Context): String {
        val root = JSONObject()

        // 1. Export SharedPreferences
        val sharedPrefsJson = JSONObject()
        val clockPrefs = context.getSharedPreferences(CLOCK_PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefsJson.put(CLOCK_PREFS_NAME, JSONObject(clockPrefs.all))

        // Add placeholder for 2FA/OTP if it exists
        val otpPrefs = context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
        if (otpPrefs.all.isNotEmpty()) {
            sharedPrefsJson.put("otp_prefs", JSONObject(otpPrefs.all))
        }

        root.put(KEY_SHARED_PREFS, sharedPrefsJson)

        // 2. Export Cookies
        val cookiesArray = JSONArray()
        val cookieManager = CookieManager.getInstance()

        AppConfig.defaultApps.forEach { app ->
            val cookie = cookieManager.getCookie(app.url)
            if (cookie != null) {
                val cookieObj = JSONObject()
                cookieObj.put("url", app.url)
                cookieObj.put("cookie", cookie)
                cookiesArray.put(cookieObj)
            }
        }
        root.put(KEY_COOKIES, cookiesArray)

        return root.toString(2)
    }

    fun importData(context: Context, jsonData: String): Boolean {
        return try {
            val root = JSONObject(jsonData)

            // 1. Restore SharedPreferences
            if (root.has(KEY_SHARED_PREFS)) {
                val sharedPrefsJson = root.getJSONObject(KEY_SHARED_PREFS)
                val it = sharedPrefsJson.keys()
                while (it.hasNext()) {
                    val prefsName = it.next()
                    val prefsData = sharedPrefsJson.getJSONObject(prefsName)
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    editor.clear()

                    val dataIt = prefsData.keys()
                    while (dataIt.hasNext()) {
                        val key = dataIt.next()
                        when (val value = prefsData.get(key)) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Double -> {
                                // JSON numbers can be parsed as Double.
                                // In this app we mostly use Int for colors or Float.
                                // We'll try to downcast if appropriate.
                                if (value == value.toInt().toDouble()) {
                                    editor.putInt(key, value.toInt())
                                } else {
                                    editor.putFloat(key, value.toFloat())
                                }
                            }
                            is String -> editor.putString(key, value)
                        }
                    }
                    editor.apply()
                }
            }

            // 2. Restore Cookies
            if (root.has(KEY_COOKIES)) {
                val cookiesArray = root.getJSONArray(KEY_COOKIES)
                val cookieManager = CookieManager.getInstance()
                for (i in 0 until cookiesArray.length()) {
                    val cookieObj = cookiesArray.getJSONObject(i)
                    val url = cookieObj.getString("url")
                    val cookieString = cookieObj.getString("cookie")

                    // Split and set individually as setCookie expects a single name-value pair
                    cookieString.split(";").forEach {
                        val cookiePair = it.trim()
                        if (cookiePair.isNotEmpty()) {
                            cookieManager.setCookie(url, cookiePair)
                        }
                    }
                }
                cookieManager.flush()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
