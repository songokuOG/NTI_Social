package com.afterlight.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaFilePathsTest {

    @Test
    fun encryptedFile_usesDeterministicPath() {
        val root = File("/tmp/afterlight-files")
        val file = MediaFilePaths.encryptedFile(root, "party-1", "media-2")
        assertEquals(File(root, "parties/party-1/media-2.enc").path, file.path)
    }

    @Test
    fun deletePartyFiles_removesOnlyThatParty() {
        val root = File.createTempFile("files", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val keep = MediaFilePaths.encryptedFile(root, "keep", "a")
        val remove = MediaFilePaths.encryptedFile(root, "gone", "b")
        keep.parentFile?.mkdirs()
        remove.parentFile?.mkdirs()
        keep.writeText("keep")
        remove.writeText("gone")

        assertTrue(MediaFilePaths.deletePartyFiles(root, "gone"))
        assertTrue(keep.exists())
        assertFalse(remove.exists())
    }
}
