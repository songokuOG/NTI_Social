package com.afterlight.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the shared per-party media key in EncryptedSharedPreferences.
 *
 * Members receive the same key from the party document (member-only Firestore read)
 * so they can decrypt photos captured on other devices.
 */
@Singleton
class PartyKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_NAME = "afterlight_party_keys"
        const val KEY_PREFIX = "media_key_"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasKey(partyId: String): Boolean {
        return prefs.contains(KEY_PREFIX + partyId)
    }

    fun getKey(partyId: String): ByteArray? {
        val encoded = prefs.getString(KEY_PREFIX + partyId, null) ?: return null
        return runCatching { PartyKeyCodec.decode(encoded) }.getOrNull()
    }

    fun importEncodedKey(partyId: String, encoded: String) {
        val key = PartyKeyCodec.decode(encoded)
        prefs.edit().putString(KEY_PREFIX + partyId, PartyKeyCodec.encode(key)).apply()
    }

    fun importKey(partyId: String, key: ByteArray) {
        prefs.edit().putString(KEY_PREFIX + partyId, PartyKeyCodec.encode(key)).apply()
    }

    fun deleteKey(partyId: String) {
        prefs.edit().remove(KEY_PREFIX + partyId).apply()
    }

    fun deleteAll() {
        prefs.edit().clear().apply()
    }
}
