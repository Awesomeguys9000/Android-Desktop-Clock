package com.dashboard.android

import android.net.Uri
import android.util.Base64
import java.nio.ByteBuffer

object GoogleAuthParser {

    data class OtpEntry(
        val secret: String,
        val name: String,
        val issuer: String
    )

    fun parseUri(uriString: String): List<OtpEntry> {
        val uri = Uri.parse(uriString)

        if (uri.scheme == "otpauth-migration" && uri.host == "offline") {
            val data = uri.getQueryParameter("data") ?: return emptyList()
            try {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                return parseProtobuf(bytes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (uri.scheme == "otpauth") {
            // Standard TOTP (otpauth://totp/Issuer:Account?secret=SECRET&issuer=Issuer)
            val secret = uri.getQueryParameter("secret")
            if (!secret.isNullOrEmpty()) {
                val issuerParam = uri.getQueryParameter("issuer")
                val path = uri.path?.trimStart('/') ?: ""

                // Parse name and issuer from path if needed
                var name = path
                var issuer = issuerParam ?: ""

                if (path.contains(":")) {
                    val parts = path.split(":")
                    if (issuer.isEmpty()) issuer = parts[0].trim()
                    name = parts.drop(1).joinToString(":").trim()
                }

                return listOf(OtpEntry(secret, name, issuer))
            }
        }
        return emptyList()
    }

    private fun parseProtobuf(data: ByteArray): List<OtpEntry> {
        val buffer = ByteBuffer.wrap(data)
        val entries = mutableListOf<OtpEntry>()

        while (buffer.hasRemaining()) {
            val tag = readVarInt(buffer)
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            if (fieldNumber == 1 && wireType == 2) {
                // otp_parameters (repeated message)
                val length = readVarInt(buffer)
                val limit = buffer.position() + length

                // Read the inner message
                var secret: ByteArray? = null
                var name = ""
                var issuer = ""

                while (buffer.position() < limit) {
                    val innerTag = readVarInt(buffer)
                    val innerFieldNumber = innerTag ushr 3
                    val innerWireType = innerTag and 0x07

                    when (innerFieldNumber) {
                        1 -> { // secret (bytes)
                            val len = readVarInt(buffer)
                            val b = ByteArray(len)
                            buffer.get(b)
                            secret = b
                        }
                        2 -> { // name (string)
                            val len = readVarInt(buffer)
                            val b = ByteArray(len)
                            buffer.get(b)
                            name = String(b)
                        }
                        3 -> { // issuer (string)
                            val len = readVarInt(buffer)
                            val b = ByteArray(len)
                            buffer.get(b)
                            issuer = String(b)
                        }
                        else -> skipField(buffer, innerWireType)
                    }
                }

                if (secret != null) {
                    entries.add(OtpEntry(
                        base32Encode(secret),
                        name,
                        issuer
                    ))
                }
            } else {
                skipField(buffer, wireType)
            }
        }
        return entries
    }

    private fun readVarInt(buffer: ByteBuffer): Int {
        var value = 0
        var shift = 0
        while (true) {
            if (!buffer.hasRemaining()) break
            val b = buffer.get().toInt()
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value
    }

    private fun skipField(buffer: ByteBuffer, wireType: Int) {
        when (wireType) {
            0 -> readVarInt(buffer) // Varint
            1 -> buffer.position(buffer.position() + 8) // 64-bit
            2 -> { // Length delimited
                val len = readVarInt(buffer)
                buffer.position(buffer.position() + len)
            }
            5 -> buffer.position(buffer.position() + 4) // 32-bit
            else -> throw IllegalArgumentException("Unsupported wire type: $wireType")
        }
    }

    private fun base32Encode(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var buffer = 0
        var bufferLength = 0

        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bufferLength += 8
            while (bufferLength >= 5) {
                result.append(alphabet[(buffer ushr (bufferLength - 5)) and 0x1F])
                bufferLength -= 5
            }
        }
        if (bufferLength > 0) {
            result.append(alphabet[(buffer shl (5 - bufferLength)) and 0x1F])
        }
        return result.toString()
    }
}
