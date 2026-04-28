package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongInfoResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: SongInfoData? = null
)

@Serializable
data class SongInfoData(
    val id: Long = 0,
    val uid: Long = 0,
    val uname: String = "",
    val author: String = "",
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val lyric: String = "",
    val duration: Int = 0,
    val passtime: Long = 0,
    val coin_num: Int = 0,
    val statistic: SongStatistic? = null
)

@Serializable
data class SongStatistic(
    val sid: Long = 0,
    val play: Long = 0,
    val collect: Long = 0,
    val comment: Long = 0,
    val share: Long = 0
)

@Serializable
data class SongStreamResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: SongStreamData? = null
)

@Serializable
data class SongStreamData(
    val sid: Long = 0,
    val type: Int = 0,
    val size: Long = 0,
    val cdns: List<String> = emptyList(),
    val title: String = "",
    val cover: String = ""
)

@Serializable
data class SongLyricResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: String? = null
)

