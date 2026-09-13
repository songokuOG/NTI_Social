package com.afterlight.feature.camera.domain

import android.content.Context
import com.afterlight.core.security.PartyKeyStore
import com.afterlight.core.security.SecurityManager
import com.afterlight.data.local.MediaFilePaths
import com.afterlight.data.local.dao.MediaDao
import com.afterlight.data.local.dao.SyncStateDao
import com.afterlight.data.local.model.MediaEntity
import com.afterlight.data.local.model.SyncStateEntity
import com.afterlight.data.local.model.SyncStatus
import com.afterlight.feature.camera.worker.MediaUploadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.Clock
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JPEG file → encrypt with shared party key → Room + pending sync → durable upload.
 */
@Singleton
class CameraRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager,
    private val partyKeyStore: PartyKeyStore,
    private val mediaDao: MediaDao,
    private val syncStateDao: SyncStateDao,
    private val mediaUploadScheduler: MediaUploadScheduler
) {
    
    suspend fun capturePhoto(partyId: String, jpegFile: File): Result<MediaEntity> {
        return try {
            if (!jpegFile.exists() || jpegFile.length() == 0L) {
                return Result.failure(IllegalStateException("Captured photo is empty"))
            }
            if (!partyKeyStore.hasKey(partyId)) {
                return Result.failure(
                    IllegalStateException("Party encryption key is not ready. Re-open the party and try again.")
                )
            }

            val mediaId = UUID.randomUUID().toString()
            val encryptedFile = MediaFilePaths.encryptedFile(context.filesDir, partyId, mediaId)
            encryptedFile.parentFile?.mkdirs()
            
            securityManager.encryptFile(jpegFile, encryptedFile, partyId)
            
            val mediaEntity = MediaEntity(
                id = mediaId,
                partyId = partyId,
                encryptedFilePath = encryptedFile.absolutePath,
                createdAt = Clock.System.now(),
                flagged = false
            )
            mediaDao.insert(mediaEntity)
            syncStateDao.insert(
                SyncStateEntity(
                    id = mediaId,
                    mediaId = mediaId,
                    syncStatus = SyncStatus.PENDING,
                    lastAttemptAt = null
                )
            )
            mediaUploadScheduler.enqueue(partyId, mediaId)
            
            Result.success(mediaEntity)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (jpegFile.exists()) {
                jpegFile.delete()
            }
        }
    }
}
