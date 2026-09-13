package com.afterlight.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.afterlight.data.local.model.MediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for media operations.
 * Stage 13: Party-scoped media queries.
 */
@Dao
interface MediaDao {
    
    @Query("SELECT * FROM media WHERE id = :mediaId")
    fun getMediaById(mediaId: String): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE id = :mediaId")
    suspend fun getByIdOnce(mediaId: String): MediaEntity?
    
    @Query("SELECT * FROM media WHERE partyId = :partyId ORDER BY createdAt DESC")
    fun getMediaForParty(partyId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE partyId = :partyId")
    suspend fun getMediaForPartyOnce(partyId: String): List<MediaEntity>
    
    @Query("SELECT * FROM media WHERE flagged = 1 ORDER BY createdAt DESC")
    fun getFlaggedMedia(): Flow<List<MediaEntity>>
    
    @Query("SELECT COUNT(*) FROM media WHERE partyId = :partyId")
    fun getMediaCountForParty(partyId: String): Flow<Int>
    
    // Upsert, not REPLACE: SQLite REPLACE deletes the row first and would
    // CASCADE-wipe sync_state when the media listener re-applies a document.
    @Upsert
    suspend fun insert(media: MediaEntity)
    
    @Upsert
    suspend fun insertAll(media: List<MediaEntity>)
    
    @Query("UPDATE media SET flagged = :flagged WHERE id = :mediaId")
    suspend fun updateFlagged(mediaId: String, flagged: Boolean)
    
    @Query("DELETE FROM media WHERE id = :mediaId")
    suspend fun deleteById(mediaId: String)
    
    @Query("DELETE FROM media WHERE partyId = :partyId")
    suspend fun deleteByPartyId(partyId: String)
    
    @Query("DELETE FROM media")
    suspend fun deleteAll()
}
