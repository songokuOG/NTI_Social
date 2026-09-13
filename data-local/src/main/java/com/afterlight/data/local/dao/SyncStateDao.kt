package com.afterlight.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.afterlight.data.local.model.SyncStateEntity
import com.afterlight.data.local.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for sync state operations.
 * Stage 13: Network sync tracking queries.
 */
@Dao
interface SyncStateDao {
    
    @Query("SELECT * FROM sync_state WHERE mediaId = :mediaId")
    fun getSyncStateForMedia(mediaId: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE mediaId = :mediaId")
    suspend fun getByMediaIdOnce(mediaId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE syncStatus IN ('PENDING', 'FAILED', 'IN_PROGRESS')")
    suspend fun getIncompleteOnce(): List<SyncStateEntity>
    
    @Query("SELECT * FROM sync_state WHERE syncStatus = :status ORDER BY lastAttemptAt ASC")
    fun getSyncStateByStatus(status: SyncStatus): Flow<List<SyncStateEntity>>
    
    @Query("SELECT * FROM sync_state WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY lastAttemptAt ASC")
    fun getPendingSync(): Flow<List<SyncStateEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(syncState: SyncStateEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(syncStates: List<SyncStateEntity>)
    
    @Update
    suspend fun update(syncState: SyncStateEntity)
    
    @Query("DELETE FROM sync_state WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM sync_state WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: String)
    
    @Query("DELETE FROM sync_state")
    suspend fun deleteAll()
}
