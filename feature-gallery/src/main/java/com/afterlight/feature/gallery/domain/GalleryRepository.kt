package com.afterlight.feature.gallery.domain

import com.afterlight.core.security.SecurityManager
import com.afterlight.data.local.dao.MediaDao
import com.afterlight.data.local.model.MediaEntity
import com.afterlight.data.remote.firebase.FirebaseMediaService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepository @Inject constructor(
    private val securityManager: SecurityManager,
    private val mediaDao: MediaDao,
    private val firebaseMediaService: FirebaseMediaService
) {
    
    fun getMediaForParty(partyId: String): Flow<List<MediaEntity>> {
        return mediaDao.getMediaForParty(partyId)
    }
    
    suspend fun decryptMedia(mediaId: String, partyId: String): Result<ByteArray> {
        return try {
            val mediaEntity = mediaDao.getMediaById(mediaId).first()
                ?: return Result.failure(Exception("Media not found"))
            
            val encryptedFile = File(mediaEntity.encryptedFilePath)
            if (!encryptedFile.exists()) {
                encryptedFile.parentFile?.mkdirs()
                val downloaded = downloadWithRetry(partyId, mediaId, encryptedFile)
                if (downloaded.isFailure) {
                    if (encryptedFile.exists()) {
                        encryptedFile.delete()
                    }
                    return Result.failure(
                        Exception(
                            "Failed to download encrypted file from cloud",
                            downloaded.exceptionOrNull()
                        )
                    )
                }
            }
            
            val tempDecrypted = File.createTempFile("decrypt_", ".jpg")
            try {
                securityManager.decryptFile(encryptedFile, tempDecrypted, partyId)
                Result.success(tempDecrypted.readBytes())
            } catch (e: SecurityException) {
                Result.failure(Exception("Decryption failed: GCM tag mismatch (corrupted file)", e))
            } finally {
                tempDecrypted.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadWithRetry(
        partyId: String,
        mediaId: String,
        destination: File
    ): Result<Unit> {
        var last: Result<Unit> = Result.failure(IllegalStateException("Download not attempted"))
        repeat(2) { attempt ->
            last = firebaseMediaService.downloadMedia(partyId, mediaId, destination)
            if (last.isSuccess && destination.exists() && destination.length() > 0L) {
                return last
            }
            if (destination.exists()) {
                destination.delete()
            }
            if (attempt == 0) {
                delay(1_000)
            }
        }
        return last
    }
}
