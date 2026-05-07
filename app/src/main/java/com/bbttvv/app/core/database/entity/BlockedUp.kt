package com.bbttvv.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_ups")
data class BlockedUp(
    @PrimaryKey
    val mid: Long,
    val name: String,
    val face: String,
    val blockedAt: Long = System.currentTimeMillis()
)

