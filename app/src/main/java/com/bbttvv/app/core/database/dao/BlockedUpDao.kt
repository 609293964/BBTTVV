package com.bbttvv.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bbttvv.app.core.database.entity.BlockedUp
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedUpDao {
    @Query("SELECT * FROM blocked_ups ORDER BY blockedAt DESC")
    fun getAllBlockedUps(): Flow<List<BlockedUp>>

    @Query("SELECT * FROM blocked_ups WHERE mid = :mid LIMIT 1")
    suspend fun getBlockedUp(mid: Long): BlockedUp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockedUp: BlockedUp)

    @Query("DELETE FROM blocked_ups WHERE mid = :mid")
    suspend fun delete(mid: Long)
    
    @Query("SELECT COUNT(*) FROM blocked_ups WHERE mid = :mid")
    fun isBlocked(mid: Long): Flow<Boolean>
}

