package com.bbttvv.app.data.repository

import android.content.Context
import com.bbttvv.app.core.database.AppDatabase
import com.bbttvv.app.core.database.entity.BlockedUp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BlockedUpRepository(context: Context) {
    private val blockedUpDao = AppDatabase.getDatabase(context).blockedUpDao()

    fun getAllBlockedUps(): Flow<List<BlockedUp>> = blockedUpDao.getAllBlockedUps()

    fun isBlocked(mid: Long): Flow<Boolean> = blockedUpDao.isBlocked(mid)

    suspend fun blockUp(mid: Long, name: String, face: String) {
        val entity = BlockedUp(mid = mid, name = name, face = face)
        blockedUpDao.insert(entity)
    }

    suspend fun unblockUp(mid: Long) {
        blockedUpDao.delete(mid)
    }
}

