package com.bbttvv.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import com.bbttvv.app.core.database.dao.SearchHistoryDao
import com.bbttvv.app.core.database.entity.SearchHistory
import com.bbttvv.app.core.database.entity.BlockedUp
import com.bbttvv.app.core.database.dao.BlockedUpDao

@Database(entities = [SearchHistory::class, BlockedUp::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun blockedUpDao(): BlockedUpDao

    companion object {
        const val DATABASE_NAME = "pure_bilibili.db"

        fun getDatabase(context: Context): AppDatabase {
            return DatabaseModule.getDatabase(context)
        }
    }
}
