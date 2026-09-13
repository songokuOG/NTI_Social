package com.afterlight.feature.camera.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaUploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(partyId: String, mediaId: String) {
        val input = Data.Builder()
            .putString(MediaUploadWorker.KEY_PARTY_ID, partyId)
            .putString(MediaUploadWorker.KEY_MEDIA_ID, mediaId)
            .build()

        val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            MediaUploadWorker.uniqueWorkName(mediaId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
