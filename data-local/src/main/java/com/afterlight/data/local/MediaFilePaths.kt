package com.afterlight.data.local

import java.io.File

object MediaFilePaths {
    fun encryptedFile(filesDir: File, partyId: String, mediaId: String): File {
        return File(partyDirectory(filesDir, partyId), "$mediaId.enc")
    }

    fun partyDirectory(filesDir: File, partyId: String): File {
        return File(filesDir, "parties/$partyId")
    }

    fun allPartiesDirectory(filesDir: File): File {
        return File(filesDir, "parties")
    }

    fun deletePartyFiles(filesDir: File, partyId: String): Boolean {
        val directory = partyDirectory(filesDir, partyId)
        if (!directory.exists()) {
            return true
        }
        return directory.deleteRecursively()
    }

    fun deleteAllPartyFiles(filesDir: File): Boolean {
        val directory = allPartiesDirectory(filesDir)
        if (!directory.exists()) {
            return true
        }
        return directory.deleteRecursively()
    }
}
