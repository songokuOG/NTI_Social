package com.afterlight.core.security

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Best-effort overwrite-then-delete for local files.
 *
 * The 3-pass pattern (0x00, 0xFF, random) is a historical DoD 5220.22-M approach.
 * On modern flash/F2FS/ext4 storage it does not guarantee physical erasure of
 * previous blocks. Treat this as stronger than a plain delete(), not as forensic
 * sanitization.
 */
fun File.secureDelete() {
    if (!exists()) {
        throw IllegalStateException("File does not exist: $absolutePath")
    }
    
    if (!canWrite()) {
        throw SecurityException("Cannot write to file: $absolutePath")
    }
    
    val fileSize = length()
    if (fileSize == 0L) {
        // Empty file, just delete
        delete()
        return
    }
    
    RandomAccessFile(this, "rws").use { raf ->
        // Pass 1: Overwrite with 0x00
        raf.seek(0)
        val zeroBuffer = ByteArray(8192) { 0x00 }
        var remaining = fileSize
        while (remaining > 0) {
            val toWrite = minOf(remaining, zeroBuffer.size.toLong()).toInt()
            raf.write(zeroBuffer, 0, toWrite)
            remaining -= toWrite
        }
        raf.fd.sync() // Force write to disk
        
        // Pass 2: Overwrite with 0xFF
        raf.seek(0)
        val ffBuffer = ByteArray(8192) { 0xFF.toByte() }
        remaining = fileSize
        while (remaining > 0) {
            val toWrite = minOf(remaining, ffBuffer.size.toLong()).toInt()
            raf.write(ffBuffer, 0, toWrite)
            remaining -= toWrite
        }
        raf.fd.sync()
        
        // Pass 3: Overwrite with random bytes
        raf.seek(0)
        val randomBuffer = ByteArray(8192)
        val secureRandom = SecureRandom()
        remaining = fileSize
        while (remaining > 0) {
            val toWrite = minOf(remaining, randomBuffer.size.toLong()).toInt()
            secureRandom.nextBytes(randomBuffer)
            raf.write(randomBuffer, 0, toWrite)
            remaining -= toWrite
        }
        raf.fd.sync()
        
        // Wipe buffers
        zeroBuffer.fill(0)
        ffBuffer.fill(0)
        randomBuffer.fill(0)
    }
    
    // Delete the file
    if (!delete()) {
        throw SecurityException("Failed to delete file after secure wipe: $absolutePath")
    }
}
