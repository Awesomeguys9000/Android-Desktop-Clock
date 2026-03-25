package com.dashboard.android

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader
import android.util.Log
import android.widget.Toast

object DataManagementUtils {

    private const val TAG = "DataManagementUtils"

    fun exportData(context: Context, uri: Uri) {
        try {
            val json = JSONObject()

            // 1. Export SharedPreferences
            val prefsObj = JSONObject()
            val prefFiles = listOf("clock_prefs", "otp_prefs")
            for (prefFile in prefFiles) {
                val prefs = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)
                val fileObj = JSONObject()
                for ((key, value) in prefs.all) {
                    val typedValue = JSONObject()
                    typedValue.put("value", value)
                    typedValue.put("type", value?.javaClass?.simpleName ?: "Unknown")
                    fileObj.put(key, typedValue)
                }
                prefsObj.put(prefFile, fileObj)
            }
            json.put("shared_prefs", prefsObj)

            // 2. Export WebView Cookies
            val cookieManager = CookieManager.getInstance()
            val url = "https://appassets.androidplatform.net" // Default app url
            val cookies = cookieManager.getCookie(url)
            json.put("webview_cookies", cookies ?: "")

            // Write to file
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json.toString(2))
                }
            }
            Toast.makeText(context, "Data exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data", e)
            Toast.makeText(context, "Failed to export data: \${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importData(context: Context, uri: Uri) {
        try {
            // Read file
            val stringBuilder = java.lang.StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                }
            }

            val json = JSONObject(stringBuilder.toString())

            // 1. Import SharedPreferences
            if (json.has("shared_prefs")) {
                val prefsObj = json.getJSONObject("shared_prefs")
                for (prefFile in prefsObj.keys()) {
                    val prefs = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)
                    val editor = prefs.edit()

                    val fileObj = prefsObj.getJSONObject(prefFile)
                    for (key in fileObj.keys()) {
                        val typedValue = fileObj.getJSONObject(key)
                        val type = typedValue.getString("type")

                        // When reading from JSON, numbers are parsed as Double or Int
                        when (type) {
                            "Boolean" -> editor.putBoolean(key, typedValue.getBoolean("value"))
                            "Integer" -> editor.putInt(key, typedValue.getInt("value"))
                            "Float" -> editor.putFloat(key, typedValue.getDouble("value").toFloat())
                            "Long" -> editor.putLong(key, typedValue.getLong("value"))
                            "String" -> editor.putString(key, typedValue.getString("value"))
                        }
                    }
                    editor.apply()
                }
            }

            // 2. Import WebView Cookies
            if (json.has("webview_cookies")) {
                 val cookiesString = json.getString("webview_cookies")
                 if (cookiesString.isNotEmpty()) {
                     val cookieManager = CookieManager.getInstance()
                     val url = "https://appassets.androidplatform.net"

                     // Need to set cookies one by one
                     val cookies = cookiesString.split(";")
                     for (cookie in cookies) {
                         cookieManager.setCookie(url, cookie.trim())
                     }
                     cookieManager.flush()
                 }
            }

            Toast.makeText(context, "Data imported successfully. App will restart.", Toast.LENGTH_SHORT).show()

            // Restart the app to apply preferences
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to import data", e)
            Toast.makeText(context, "Failed to import data: \${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
