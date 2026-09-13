package com.afterlight.core.security

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM file format: 12-byte random IV || ciphertext || 16-byte tag.
 * A new IV is generated for every encryption call to prevent nonce reuse.
 */
object AesGcmFileCipher {
    const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val BUFFER_SIZE = 8192

    fun encrypt(inputFile: File, outputFile: File, secretKey: SecretKey) {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        FileInputStream(inputFile).use { input ->
            FileOutputStream(outputFile).use { output ->
                output.write(iv)
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val encryptedChunk = cipher.update(buffer, 0, bytesRead)
                    if (encryptedChunk != null) {
                        output.write(encryptedChunk)
                    }
                }
                output.write(cipher.doFinal())
                buffer.fill(0)
            }
        }
    }

    fun decrypt(inputFile: File, outputFile: File, secretKey: SecretKey) {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        FileInputStream(inputFile).use { input ->
            val iv = ByteArray(IV_LENGTH_BYTES)
            val ivBytesRead = input.read(iv)
            if (ivBytesRead != IV_LENGTH_BYTES) {
                throw SecurityException("Invalid encrypted file: IV missing or truncated")
            }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val decryptedChunk = cipher.update(buffer, 0, bytesRead)
                    if (decryptedChunk != null) {
                        output.write(decryptedChunk)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock.isNotEmpty()) {
                    output.write(finalBlock)
                }
                buffer.fill(0)
            }
        }
    }
}
