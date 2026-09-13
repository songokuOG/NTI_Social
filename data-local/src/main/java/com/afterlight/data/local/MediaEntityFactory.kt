package com.afterlight.data.local

import com.afterlight.data.local.model.MediaEntity
import kotlinx.datetime.Instant
import java.io.File

object MediaEntityFactory {
    fun fromRemote(
        mediaId: String,
        partyId: String,
        filesDir: File,
        createdAtEpochMs: Long?,
        flagged: Boolean,
        nowEpochMs: Long
    ): MediaEntity {
        return MediaEntity(
            id = mediaId,
            partyId = partyId,
            encryptedFilePath = MediaFilePaths.encryptedFile(filesDir, partyId, mediaId).absolutePath,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs ?: nowEpochMs),
            flagged = flagged
        )
    }
}
