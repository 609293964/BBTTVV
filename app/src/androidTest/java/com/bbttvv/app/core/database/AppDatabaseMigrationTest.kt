package com.bbttvv.app.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TestDatabaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TestDatabaseName)
    }

    @Test
    fun migratesV1SearchHistoryWithoutClearingKnownData() {
        createLegacyDatabase(version = 1) { database ->
            database.execSQL(
                "CREATE TABLE `search_history` (`keyword` TEXT NOT NULL, PRIMARY KEY(`keyword`))"
            )
            database.execSQL("INSERT INTO `search_history` (`keyword`) VALUES ('hello-tv')")
        }

        val database = openMigratedDatabase()
        try {
            database.openHelper.writableDatabase
                .query("SELECT `keyword`, `timestamp` FROM `search_history` WHERE `keyword` = 'hello-tv'")
                .use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals("hello-tv", cursor.getString(0))
                    assertEquals(0L, cursor.getLong(1))
                }

            database.openHelper.writableDatabase
                .query("SELECT COUNT(*) FROM `blocked_ups`")
                .use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
        } finally {
            database.close()
        }
    }

    @Test
    fun migratesV2BlockedUpsWithoutClearingKnownData() {
        createLegacyDatabase(version = 2) { database ->
            database.execSQL(
                "CREATE TABLE `blocked_ups` (`mid` INTEGER NOT NULL, `name` TEXT, PRIMARY KEY(`mid`))"
            )
            database.execSQL("INSERT INTO `blocked_ups` (`mid`, `name`) VALUES (42, 'blocked')")
        }

        val database = openMigratedDatabase()
        try {
            database.openHelper.writableDatabase
                .query("SELECT `mid`, `name`, `face`, `blockedAt` FROM `blocked_ups` WHERE `mid` = 42")
                .use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(42L, cursor.getLong(0))
                    assertEquals("blocked", cursor.getString(1))
                    assertEquals("", cursor.getString(2))
                    assertEquals(0L, cursor.getLong(3))
                }

            database.openHelper.writableDatabase
                .query("SELECT COUNT(*) FROM `search_history`")
                .use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
        } finally {
            database.close()
        }
    }

    private fun createLegacyDatabase(
        version: Int,
        block: (SQLiteDatabase) -> Unit,
    ) {
        val databaseFile = context.getDatabasePath(TestDatabaseName)
        databaseFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            block(database)
            database.version = version
        }
    }

    private fun openMigratedDatabase(): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TestDatabaseName,
        )
            .addMigrations(*AppDatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()
    }

    private companion object {
        private const val TestDatabaseName = "app-database-migration-test.db"
    }
}
