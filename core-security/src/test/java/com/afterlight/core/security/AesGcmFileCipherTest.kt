package com.afterlight.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.crypto.spec.SecretKeySpec

class AesGcmFileCipherTest {

    private fun tempFile(prefix: String, bytes: ByteArray? = null): File {
        val file = File.createTempFile(prefix, ".bin")
        file.deleteOnExit()
        if (bytes != null) {
            file.writeBytes(bytes)
        }
        return file
    }

    private fun key(seed: Byte = 7): SecretKeySpec {
        return SecretKeySpec(ByteArray(32) { seed }, "AES")
    }

    @Test
    fun encryptDecrypt_roundTrips() {
        val plaintext = "party photo bytes".repeat(200).toByteArray()
        val input = tempFile("plain", plaintext)
        val encrypted = tempFile("enc")
        val decrypted = tempFile("dec")

        AesGcmFileCipher.encrypt(input, encrypted, key())
        AesGcmFileCipher.decrypt(encrypted, decrypted, key())

        assertArrayEquals(plaintext, decrypted.readBytes())
        assertTrue(encrypted.length() > plaintext.size.toLong())
    }

    @Test
    fun encrypt_usesFreshIvEachTime() {
        val plaintext = "same plaintext".toByteArray()
        val input = tempFile("plain", plaintext)
        val first = tempFile("enc1")
        val second = tempFile("enc2")

        AesGcmFileCipher.encrypt(input, first, key())
        AesGcmFileCipher.encrypt(input, second, key())

        assertFalse(first.readBytes().contentEquals(second.readBytes()))
    }

    @Test(expected = Exception::class)
    fun decrypt_rejectsWrongKey() {
        val input = tempFile("plain", "secret".toByteArray())
        val encrypted = tempFile("enc")
        val decrypted = tempFile("dec")
        AesGcmFileCipher.encrypt(input, encrypted, key(1))
        AesGcmFileCipher.decrypt(encrypted, decrypted, key(2))
    }

    @Test(expected = Exception::class)
    fun decrypt_rejectsCorruptedCiphertext() {
        val input = tempFile("plain", "secret".toByteArray())
        val encrypted = tempFile("enc")
        val decrypted = tempFile("dec")
        AesGcmFileCipher.encrypt(input, encrypted, key())
        val bytes = encrypted.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0xFF).toByte()
        encrypted.writeBytes(bytes)
        AesGcmFileCipher.decrypt(encrypted, decrypted, key())
    }

    @Test(expected = SecurityException::class)
    fun decrypt_rejectsTruncatedFile() {
        val encrypted = tempFile("enc", ByteArray(4))
        val decrypted = tempFile("dec")
        AesGcmFileCipher.decrypt(encrypted, decrypted, key())
    }
}
