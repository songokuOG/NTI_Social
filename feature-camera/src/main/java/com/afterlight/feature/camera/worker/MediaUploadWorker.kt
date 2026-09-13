package com.afterlight.feature.camera.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afterlight.data.local.dao.MediaDao
import com.afterlight.data.local.dao.SyncStateDao
import com.afterlight.data.local.model.SyncStateEntity
import com.afterlight.data.local.model.SyncStatus
import com.afterlight.data.remote.firebase.FirebaseMediaService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import java.io.File

@HiltWorker
class MediaUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaDao: MediaDao,
    private val syncStateDao: SyncStateDao,
    private val firebaseMediaService: FirebaseMediaService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val partyId = inputData.getString(KEY_PARTY_ID) ?: return Result.failure()
        val mediaId = inputData.getString(KEY_MEDIA_ID) ?: return Result.failure()

        val media = mediaDao.getByIdOnce(mediaId) ?: return Result.failure()
        val encryptedFile = File(media.encryptedFilePath)
        if (!encryptedFile.exists() || encryptedFile.length() == 0L) {
            markStatus(mediaId, SyncStatus.FAILED)
            return Result.failure()
        }

        markStatus(mediaId, SyncStatus.IN_PROGRESS)
        val upload = firebaseMediaService.uploadMedia(partyId, mediaId, encryptedFile)
        return if (upload.isSuccess) {
            markStatus(mediaId, SyncStatus.COMPLETED)
            Result.success()
        } else {
            markStatus(mediaId, SyncStatus.FAILED)
            if (isRetryableFailure(runAttemptCount, upload.exceptionOrNull())) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun markStatus(mediaId: String, status: SyncStatus) {
        syncStateDao.insert(
            SyncStateEntity(
                id = mediaId,
                mediaId = mediaId,
                syncStatus = status,
                lastAttemptAt = Clock.System.now()
            )
        )
    }

    companion object {
        const val KEY_PARTY_ID = "party_id"
        const val KEY_MEDIA_ID = "media_id"
        const val MAX_ATTEMPTS = 8

        fun uniqueWorkName(mediaId: String): String = "media_upload_$mediaId"

        fun isRetryableFailure(attemptCount: Int, error: Throwable?): Boolean {
            if (attemptCount >= MAX_ATTEMPTS) {
                return false
            }
            val message = error?.message?.lowercase().orEmpty()
            return when {
                "not authenticated" in message -> false
                "permission" in message -> false
                "unauthorized" in message -> false
                "forbidden" in message -> false
                else -> true
            }
        }
    }
}
