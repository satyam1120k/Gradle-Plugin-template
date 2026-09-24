package com.guard.runtime

import java.nio.charset.StandardCharsets
import java.util.Base64

object IntelliGuardDecoder {

    /**
     * Called dynamically by obfuscated bytecode at runtime.
     * Reconstructs the original string in memory.
     */
    @JvmStatic
    fun decode(encoded: String, key: Int): String {
        return try {
            // 1. Decode the Base64 ASCII string back into XOR-masked bytes
            val decodedBytes = Base64.getDecoder().decode(encoded)

            // 2. Perform the inverse XOR operation: (A ^ Key) ^ Key = A
            val resultBytes = ByteArray(decodedBytes.size) { i ->
                (decodedBytes[i].toInt() xor key).toByte()
            }

            // 3. Return the original UTF-8 string
            String(resultBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            encoded // Fallback safety
        }
    }
}