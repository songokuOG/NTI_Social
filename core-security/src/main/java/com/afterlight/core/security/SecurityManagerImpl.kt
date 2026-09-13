package com.afterlight.core.security

import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM file encryption.
 *
 * New captures use the shared party media key so every member can decrypt.
 * Legacy local files encrypted with a per-device Keystore key remain decryptable
 * on the capturing device only.
 */
@Singleton
class SecurityManagerImpl @Inject constructor(
    private val partyKeyStore: PartyKeyStore
) : SecurityManager {
    
    private companion object {
        const val TAG = "SecurityManagerImpl"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "afterlight_party_"
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }
    
    override suspend fun encryptFile(inputFile: File, outputFile: File, partyId: String) {
        withContext(Dispatchers.IO) {
            try {
                val secretKey = getSharedKeyOrThrow(partyId)
                AesGcmFileCipher.encrypt(inputFile, outputFile, secretKey)
                Log.d(TAG, "Encrypted file for party")
            } catch (e: Exception) {
                Log.e(TAG, "Encryption failed for party $partyId", e)
                throw SecurityException("Encryption failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun decryptFile(inputFile: File, outputFile: File, partyId: String) {
        withContext(Dispatchers.IO) {
            val keys = decryptionKeys(partyId)
            if (keys.isEmpty()) {
                throw SecurityException("No decryption key available for party $partyId")
            }

            var lastError: Exception? = null
            for (secretKey in keys) {
                try {
                    AesGcmFileCipher.decrypt(inputFile, outputFile, secretKey)
                    Log.d(TAG, "Decrypted file for party")
                    return@withContext
                } catch (e: Exception) {
                    lastError = e
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                }
            }

            Log.e(TAG, "Decryption failed for party $partyId", lastError)
            throw SecurityException("Decryption failed: ${lastError?.message}", lastError)
        }
    }
    
    override suspend fun deleteSecurely(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            Log.w(TAG, "Secure delete skipped: file missing")
            return@withContext false
        }
        
        try {
            file.secureDelete()
            Log.d(TAG, "Securely deleted: ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Secure deletion failed: ${file.name}", e)
            false
        }
    }
    
    override suspend fun rotatePartyKey(partyId: String) {
        withContext(Dispatchers.IO) {
            try {
                partyKeyStore.deleteKey(partyId)
                val keyAlias = "$KEY_ALIAS_PREFIX$partyId"
                if (keyStore.containsAlias(keyAlias)) {
                    keyStore.deleteEntry(keyAlias)
                }
                Log.d(TAG, "Deleted encryption keys for party: $partyId")
            } catch (e: Exception) {
                Log.e(TAG, "Key rotation failed for party $partyId", e)
                throw SecurityException("Key rotation failed: ${e.message}", e)
            }
        }
    }

    private fun getSharedKeyOrThrow(partyId: String): SecretKey {
        val shared = partyKeyStore.getKey(partyId)
            ?: throw SecurityException("Shared party media key is not available yet")
        return SecretKeySpec(shared, KeyProperties.KEY_ALGORITHM_AES)
    }

    private fun decryptionKeys(partyId: String): List<SecretKey> {
        val keys = mutableListOf<SecretKey>()
        partyKeyStore.getKey(partyId)?.let {
            keys.add(SecretKeySpec(it, KeyProperties.KEY_ALGORITHM_AES))
        }
        getLegacyKeystoreKey(partyId)?.let { keys.add(it) }
        return keys
    }

    private fun getLegacyKeystoreKey(partyId: String): SecretKey? {
        val keyAlias = "$KEY_ALIAS_PREFIX$partyId"
        if (!keyStore.containsAlias(keyAlias)) {
            return null
        }
        return runCatching {
            val entry = keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
            entry.secretKey
        }.getOrNull()
    }

}
