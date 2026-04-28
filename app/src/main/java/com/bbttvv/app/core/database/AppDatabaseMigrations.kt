package com.bbttvv.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureCurrentSchema(db)
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureCurrentSchema(db)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

    internal fun ensureCurrentSchema(database: SupportSQLiteDatabase) {
        ensureSearchHistory(database)
        ensureBlockedUps(database)
    }

    private fun ensureSearchHistory(database: SupportSQLiteDatabase) {
        if (!database.tableExists(SearchHistoryTable)) {
            database.execSQL(CreateSearchHistorySql)
            return
        }

        val columns = database.columnNames(SearchHistoryTable)
        if ("keyword" !in columns) {
            database.execSQL("DROP TABLE IF EXISTS ${quote(SearchHistoryTable)}")
            database.execSQL(CreateSearchHistorySql)
            return
        }

        database.execSQL("DROP TABLE IF EXISTS ${quote(SearchHistoryTempTable)}")
        database.execSQL(CreateSearchHistoryTempSql)
        database.execSQL(
            """
            INSERT OR REPLACE INTO ${quote(SearchHistoryTempTable)} (`keyword`, `timestamp`)
            SELECT
                `keyword`,
                ${columnOrDefault(columns, "timestamp", "0")}
            FROM ${quote(SearchHistoryTable)}
            """.trimIndent()
        )
        database.execSQL("DROP TABLE ${quote(SearchHistoryTable)}")
        database.execSQL("ALTER TABLE ${quote(SearchHistoryTempTable)} RENAME TO ${quote(SearchHistoryTable)}")
    }

    private fun ensureBlockedUps(database: SupportSQLiteDatabase) {
        if (!database.tableExists(BlockedUpsTable)) {
            database.execSQL(CreateBlockedUpsSql)
            return
        }

        val columns = database.columnNames(BlockedUpsTable)
        if ("mid" !in columns) {
            database.execSQL("DROP TABLE IF EXISTS ${quote(BlockedUpsTable)}")
            database.execSQL(CreateBlockedUpsSql)
            return
        }

        database.execSQL("DROP TABLE IF EXISTS ${quote(BlockedUpsTempTable)}")
        database.execSQL(CreateBlockedUpsTempSql)
        database.execSQL(
            """
            INSERT OR REPLACE INTO ${quote(BlockedUpsTempTable)} (`mid`, `name`, `face`, `blockedAt`)
            SELECT
                `mid`,
                ${columnOrDefault(columns, "name", "''")},
                ${columnOrDefault(columns, "face", "''")},
                ${columnOrDefault(columns, "blockedAt", "0")}
            FROM ${quote(BlockedUpsTable)}
            """.trimIndent()
        )
        database.execSQL("DROP TABLE ${quote(BlockedUpsTable)}")
        database.execSQL("ALTER TABLE ${quote(BlockedUpsTempTable)} RENAME TO ${quote(BlockedUpsTable)}")
    }

    private fun SupportSQLiteDatabase.tableExists(tableName: String): Boolean {
        query(
            SimpleSQLiteQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
                arrayOf(tableName),
            )
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun SupportSQLiteDatabase.columnNames(tableName: String): Set<String> {
        query("PRAGMA table_info(${quote(tableName)})").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex < 0) return emptySet()

            val names = linkedSetOf<String>()
            while (cursor.moveToNext()) {
                names += cursor.getString(nameIndex)
            }
            return names
        }
    }

    private fun columnOrDefault(columns: Set<String>, column: String, defaultValue: String): String {
        return if (column in columns) {
            "COALESCE(${quote(column)}, $defaultValue)"
        } else {
            defaultValue
        }
    }

    private fun quote(identifier: String): String {
        return "`" + identifier.replace("`", "``") + "`"
    }

    private const val SearchHistoryTable = "search_history"
    private const val SearchHistoryTempTable = "search_history_migration_tmp"
    private const val BlockedUpsTable = "blocked_ups"
    private const val BlockedUpsTempTable = "blocked_ups_migration_tmp"

    private const val CreateSearchHistorySql =
        "CREATE TABLE IF NOT EXISTS `search_history` (`keyword` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`keyword`))"

    private const val CreateSearchHistoryTempSql =
        "CREATE TABLE `search_history_migration_tmp` (`keyword` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`keyword`))"

    private const val CreateBlockedUpsSql =
        "CREATE TABLE IF NOT EXISTS `blocked_ups` (`mid` INTEGER NOT NULL, `name` TEXT NOT NULL, `face` TEXT NOT NULL, `blockedAt` INTEGER NOT NULL, PRIMARY KEY(`mid`))"

    private const val CreateBlockedUpsTempSql =
        "CREATE TABLE `blocked_ups_migration_tmp` (`mid` INTEGER NOT NULL, `name` TEXT NOT NULL, `face` TEXT NOT NULL, `blockedAt` INTEGER NOT NULL, PRIMARY KEY(`mid`))"
}
