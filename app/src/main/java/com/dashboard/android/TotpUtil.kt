package com.dashboard.android

import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpUtil {

    private const val HMAC_ALGO = "HmacSHA1"
    private const val TOTP_INTERVAL_SECONDS = 30L

    fun generateTotp(secretBase32: String): String {
        val secretBytes = base32Decode(secretBase32) ?: return "000000"
        val time = System.currentTimeMillis() / 1000 / TOTP_INTERVAL_SECONDS
        return generateTotp(secretBytes, time)
    }

    fun getRemainingSeconds(): Int {
        val time = System.currentTimeMillis() / 1000
        return (TOTP_INTERVAL_SECONDS - (time % TOTP_INTERVAL_SECONDS)).toInt()
    }

    fun getProgress(): Int {
        // Returns 0-100 progress
        return ((getRemainingSeconds().toFloat() / TOTP_INTERVAL_SECONDS) * 100).toInt()
    }

    private fun generateTotp(secret: ByteArray, interval: Long): String {
        val data = ByteBuffer.allocate(8).putLong(interval).array()
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(secret, HMAC_ALGO))
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0xF
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 10.0.pow(6.0).toInt()
        return String.format(Locale.US, "%06d", otp)
    }

    // Simple Base32 decoder
    fun base32Decode(base32: String): ByteArray? {
        val cleanInput = base32.uppercase(Locale.US).replace(" ", "").trim()
        if (cleanInput.isEmpty()) return ByteArray(0)

        // Remove padding
        val withoutPadding = cleanInput.trimEnd('=')

        val outputLength = withoutPadding.length * 5 / 8
        val result = ByteArray(outputLength)

        var buffer = 0
        var bufferLength = 0
        var index = 0

        for (char in withoutPadding) {
            val value = when (char) {
                in 'A'..'Z' -> char - 'A'
                in '2'..'7' -> char - '2' + 26
                else -> return null // Invalid char
            }

            buffer = (buffer shl 5) or value
            bufferLength += 5

            if (bufferLength >= 8) {
                if (index < result.size) {
                    result[index++] = (buffer shr (bufferLength - 8)).toByte()
                }
                bufferLength -= 8
            }
        }
        return result
    }
}
