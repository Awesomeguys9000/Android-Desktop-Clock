package com.dashboard.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class OtpRepository(context: Context) {

    private val prefs = context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
    private val KEY_DATA = "encrypted_otp_data"

    data class OtpEntry(
        val id: String,
        val secret: String, // Base32
        val name: String,
        val issuer: String
    )

    fun getEntries(): List<OtpEntry> {
        val encryptedData = prefs.getString(KEY_DATA, null) ?: return emptyList()
        try {
            val decryptedBytes = CryptoManager.decrypt(encryptedData)
            val jsonString = String(decryptedBytes)
            return parseJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            // In case of decryption failure (e.g. key lost), return empty.
            // Ideally we might want to handle this better, but for now this is safe.
            return emptyList()
        }
    }

    fun saveEntries(entries: List<OtpEntry>) {
        try {
            val jsonString = toJson(entries)
            val encryptedData = CryptoManager.encrypt(jsonString.toByteArray())
            prefs.edit().putString(KEY_DATA, encryptedData).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addEntries(newEntries: List<GoogleAuthParser.OtpEntry>) {
        val current = getEntries().toMutableList()
        for (entry in newEntries) {
            // Check for duplicates (by secret)
            if (current.none { it.secret == entry.secret }) {
                current.add(OtpEntry(
                    id = System.currentTimeMillis().toString() + "_" + (Math.random() * 1000).toInt(),
                    secret = entry.secret,
                    name = entry.name,
                    issuer = entry.issuer
                ))
            }
        }
        saveEntries(current)
    }

    fun deleteEntry(id: String) {
        val current = getEntries().filter { it.id != id }
        saveEntries(current)
    }

    private fun parseJson(json: String): List<OtpEntry> {
        val list = mutableListOf<OtpEntry>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(OtpEntry(
                id = obj.getString("id"),
                secret = obj.getString("secret"),
                name = obj.getString("name"),
                issuer = obj.getString("issuer")
            ))
        }
        return list
    }

    private fun toJson(entries: List<OtpEntry>): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("secret", entry.secret)
            obj.put("name", entry.name)
            obj.put("issuer", entry.issuer)
            array.put(obj)
        }
        return array.toString()
    }
}
