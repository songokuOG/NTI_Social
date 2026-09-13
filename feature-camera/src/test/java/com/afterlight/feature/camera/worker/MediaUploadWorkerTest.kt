package com.afterlight.feature.camera.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaUploadWorkerTest {

    @Test
    fun uniqueWorkName_isStablePerMedia() {
        assertEquals("media_upload_abc", MediaUploadWorker.uniqueWorkName("abc"))
        assertEquals(
            MediaUploadWorker.uniqueWorkName("abc"),
            MediaUploadWorker.uniqueWorkName("abc")
        )
        assertNotEquals(
            MediaUploadWorker.uniqueWorkName("abc"),
            MediaUploadWorker.uniqueWorkName("def")
        )
    }

    @Test
    fun retry_stopsAfterMaxAttemptsOrAuthDenial() {
        assertEquals(
            true,
            MediaUploadWorker.isRetryableFailure(1, IllegalStateException("timeout"))
        )
        assertEquals(
            false,
            MediaUploadWorker.isRetryableFailure(1, Exception("User not authenticated"))
        )
        assertEquals(
            false,
            MediaUploadWorker.isRetryableFailure(1, Exception("Permission denied"))
        )
        assertEquals(
            false,
            MediaUploadWorker.isRetryableFailure(MediaUploadWorker.MAX_ATTEMPTS, Exception("timeout"))
        )
    }
}
