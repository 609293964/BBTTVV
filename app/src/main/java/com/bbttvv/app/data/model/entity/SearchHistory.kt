package com.bbttvv.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistory(
    //  使用 keyword 作为主键，实现自动去重
    @PrimaryKey
    val keyword: String,
    // 用于按时间倒序排列
    val timestamp: Long = System.currentTimeMillis()
)
