package com.afterlight.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.afterlight.data.local.model.PartyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * DAO for party operations.
 * Stage 13: Active party tracking with soft delete.
 */
@Dao
interface PartyDao {
    
    @Query("SELECT * FROM parties WHERE id = :partyId")
    fun getPartyById(partyId: String): Flow<PartyEntity?>
    
    @Query("SELECT * FROM parties WHERE isDeleted = 0 AND expiresAt > :currentTime ORDER BY createdAt DESC")
    fun getActiveParties(currentTime: Instant): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE isDeleted = 0 AND expiresAt > :currentTime")
    suspend fun getActivePartiesOnce(currentTime: Instant): List<PartyEntity>
    
    @Query("SELECT * FROM parties WHERE hostUserId = :userId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getPartiesByHost(userId: String): Flow<List<PartyEntity>>
    
    @Query("SELECT * FROM parties WHERE expiresAt <= :currentTime AND isDeleted = 0")
    fun getExpiredParties(currentTime: Instant): Flow<List<PartyEntity>>
    
    // Upsert, not REPLACE: SQLite REPLACE deletes the row first and would
    // CASCADE-wipe media/sync_state on every party snapshot.
    @Upsert
    suspend fun insert(party: PartyEntity)
    
    @Upsert
    suspend fun insertAll(parties: List<PartyEntity>)
    
    @Query("UPDATE parties SET isDeleted = 1 WHERE id = :partyId")
    suspend fun softDelete(partyId: String)
    
    @Query("DELETE FROM parties WHERE id = :partyId")
    suspend fun deleteById(partyId: String)
    
    @Query("DELETE FROM parties")
    suspend fun deleteAll()
}
