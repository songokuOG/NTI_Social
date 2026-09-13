package com.afterlight.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MediaEntityFactoryTest {

    @Test
    fun fromRemote_isIdempotentForSameInputs() {
        val filesDir = File("/data/user/0/com.afterlight.app/files")
        val first = MediaEntityFactory.fromRemote(
            mediaId = "m1",
            partyId = "p1",
            filesDir = filesDir,
            createdAtEpochMs = 1_700_000_000_000,
            flagged = false,
            nowEpochMs = 1_800_000_000_000
        )
        val second = MediaEntityFactory.fromRemote(
            mediaId = "m1",
            partyId = "p1",
            filesDir = filesDir,
            createdAtEpochMs = 1_700_000_000_000,
            flagged = false,
            nowEpochMs = 1_800_000_000_000
        )
        assertEquals(first, second)
        assertEquals(
            MediaFilePaths.encryptedFile(filesDir, "p1", "m1").absolutePath,
            first.encryptedFilePath
        )
    }

    @Test
    fun fromRemote_usesNowWhenTimestampMissing() {
        val filesDir = File("/tmp")
        val entity = MediaEntityFactory.fromRemote(
            mediaId = "m2",
            partyId = "p2",
            filesDir = filesDir,
            createdAtEpochMs = null,
            flagged = true,
            nowEpochMs = 42L
        )
        assertEquals(42L, entity.createdAt.toEpochMilliseconds())
        assertEquals(true, entity.flagged)
    }
}
