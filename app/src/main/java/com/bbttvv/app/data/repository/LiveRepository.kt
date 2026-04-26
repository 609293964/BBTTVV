// 文件路径: data/repository/LiveRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 直播相关数据仓库
 * 从 VideoRepository 拆分出来，专注于直播功能
 */
object LiveRepository {
    private val api = NetworkModule.api
    private const val LIVE_AREAS_CACHE_TTL_MS = 12 * 60 * 60 * 1000L

    private data class LiveAreasCache(
        val fetchedAtMs: Long,
        val items: List<LiveAreaParent>
    )

    @Volatile
    private var liveAreasCache: LiveAreasCache? = null

    private suspend fun resolveRealRoomId(roomId: Long): Long {
        return try {
            val resp = api.getLiveRoomInit(roomId)
            resp.data?.roomId?.takeIf { it > 0L } ?: roomId
        } catch (_: Exception) {
            roomId
        }
    }

    /**
     * 获取热门直播列表
     */
    suspend fun getLiveRooms(page: Int = 1, parentAreaId: Int = 0): Result<List<LiveRoom>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getLiveList(parentAreaId = parentAreaId, page = page)
            // 使用 getAllRooms() 兼容新旧 API 格式
            val list = resp.data?.getAllRooms() ?: emptyList()
            list.firstOrNull()?.let {
                com.bbttvv.app.core.util.Logger.d("LiveRepo", "🟢 Popular Live: roomid=${it.roomid}, title=${it.title}, online=${it.online}")
            }
            com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 getLiveRooms parentAreaId=$parentAreaId, page=$page, count=${list.size}")
            Result.success(list)
        } catch (e: Exception) {
            com.bbttvv.app.core.util.Logger.e("LiveRepo", " getLiveRooms failed", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 获取直播父分区列表，用于 TV 首页直播分类栏。
     */
    suspend fun getLiveAreas(forceRefresh: Boolean = false): Result<List<LiveAreaParent>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val cached = liveAreasCache
            if (!forceRefresh && cached != null && now - cached.fetchedAtMs < LIVE_AREAS_CACHE_TTL_MS) {
                return@withContext Result.success(cached.items)
            }

            val resp = api.getLiveAreaList()
            if (resp.code != 0) {
                return@withContext Result.failure(
                    Exception(resp.message.ifBlank { "直播分类加载失败(${resp.code})" })
                )
            }
            val areas = resp.data
                ?.filter { it.id > 0 && it.name.isNotBlank() }
                ?: emptyList()
            liveAreasCache = LiveAreasCache(fetchedAtMs = now, items = areas)
            com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 getLiveAreas count=${areas.size}")
            Result.success(areas)
        } catch (e: Exception) {
            com.bbttvv.app.core.util.Logger.e("LiveRepo", " getLiveAreas failed", e)
            Result.failure(e)
        }
    }

    suspend fun warmupLiveMetadata(): Int {
        return getLiveAreas(forceRefresh = false).getOrNull()?.size ?: 0
    }

    /**
     * 获取直播间详情信息。
     */
    suspend fun getLiveRoomDetail(roomId: Long): Result<LiveRoomDetailData> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val resp = api.getLiveRoomDetail(roomId = realRoomId)
            if (resp.code == 0 && resp.data != null) {
                Result.success(resp.data)
            } else {
                Result.failure(Exception(resp.message.ifBlank { "直播间详情加载失败(${resp.code})" }))
            }
        } catch (e: Exception) {
            com.bbttvv.app.core.util.Logger.e("LiveRepo", " getLiveRoomDetail failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取关注的直播间（需要登录）
     */
    suspend fun getFollowedLive(page: Int = 1): Result<List<LiveRoom>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getFollowedLive(page = page)
            
            // 过滤只返回正在直播的（liveStatus == 1）
            val followedRooms = resp.data?.list
                ?.filter { it.liveStatus == 1 }
                ?: emptyList()
            
            // 关注直播 API 不返回在线人数，需要额外获取
            val liveRooms = followedRooms.map { room ->
                val liveRoom = room.toLiveRoom()
                try {
                    // 获取房间详情以得到在线人数
                    val roomInfo = api.getRoomInfo(room.roomid)
                    val online = roomInfo.data?.online ?: 0
                    com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 Room ${room.roomid} online: $online")
                    liveRoom.copy(online = online)
                } catch (e: Exception) {
                    android.util.Log.w("LiveRepo", "Failed to get room info for ${room.roomid}: ${e.message}")
                    liveRoom  // 失败时使用原数据
                }
            }
            
            Result.success(liveRooms)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取直播流 URL
     */
    suspend fun getLivePlayUrl(roomId: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 Fetching live URL for roomId=$roomId(real=$realRoomId)")
            val resp = api.getLivePlayUrl(roomId = realRoomId)
            com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 Live API response: code=${resp.code}, msg=${resp.message}")
            
            // 尝试从新 xlive API 结构获取 URL
            val playurlInfo = resp.data?.playurl_info
            if (playurlInfo != null) {
                com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 Using new xlive API structure")
                val streams = playurlInfo.playurl?.stream ?: emptyList()
                // 优先选择 http_hls，其次 http_stream
                val stream = streams.find { it.protocolName == "http_hls" }
                    ?: streams.find { it.protocolName == "http_stream" }
                    ?: streams.firstOrNull()
                
                val format = stream?.format?.firstOrNull()
                val codec = format?.codec?.firstOrNull()
                val urlInfo = codec?.url_info?.firstOrNull()
                
                if (codec != null && urlInfo != null) {
                    val url = urlInfo.host + codec.baseUrl + urlInfo.extra
                    com.bbttvv.app.core.util.Logger.d("LiveRepo", " Xlive URL: ${url.take(100)}...")
                    return@withContext Result.success(url)
                }
            }
            
            // 回退到旧 API 结构
            com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 Trying legacy durl structure...")
            val url = resp.data?.durl?.firstOrNull()?.url
            if (url != null) {
                com.bbttvv.app.core.util.Logger.d("LiveRepo", " Legacy URL: ${url.take(100)}...")
                return@withContext Result.success(url)
            }
            
            android.util.Log.e("LiveRepo", " No URL found in response")
            Result.failure(Exception("无法获取直播流"))
        } catch (e: Exception) {
            android.util.Log.e("LiveRepo", " getLivePlayUrl failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取直播流（带画质信息）- 用于画质切换
     */
    suspend fun getLivePlayUrlWithQuality(roomId: Long, qn: Int = 10000): Result<LivePlayUrlData> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 Fetching live URL with quality for roomId=$roomId(real=$realRoomId), qn=$qn")
            
            // 使用旧版 API 补充可读画质描述，但不再把 legacy durl 当作主播放来源
            val legacyResp = try {
                api.getLivePlayUrlLegacy(cid = realRoomId, qn = qn)
            } catch (e: Exception) {
                android.util.Log.w("LiveRepo", "Legacy API failed: ${e.message}")
                null
            }
            
            val qualityList = legacyResp?.data?.quality_description ?: emptyList()
            val currentQuality = legacyResp?.data?.current_quality ?: 0
            val legacyHasUrl = legacyResp?.data?.durl?.firstOrNull()?.url != null
            com.bbttvv.app.core.util.Logger.d("LiveRepo", " Legacy API: qualityList=${qualityList.map { it.desc }}, current=$currentQuality, hasUrl=$legacyHasUrl")
            
            // 新版 xlive API 作为主播放来源
            com.bbttvv.app.core.util.Logger.d("LiveRepo", "🔴 Using xlive API as primary stream source...")
            val resp = try {
                api.getLivePlayUrl(roomId = realRoomId, quality = qn)
            } catch (e: Exception) {
                android.util.Log.w("LiveRepo", "xlive API failed: ${e.message}")
                null
            }
            
            if (resp?.code == 0 && resp.data != null) {
                // 合并旧版画质列表到新版响应数据
                val mergedData = resp.data.copy(
                    quality_description = qualityList.takeIf { it.isNotEmpty() } ?: resp.data.quality_description,
                    current_quality = if (currentQuality > 0) currentQuality else resp.data.current_quality
                )
                com.bbttvv.app.core.util.Logger.d("LiveRepo", " Merged data: qualityList=${mergedData.quality_description?.map { it.desc }}")
                Result.success(mergedData)
            } else if (legacyResp?.code == 0 && legacyResp.data != null) {
                com.bbttvv.app.core.util.Logger.w("LiveRepo", "🔴 xlive API unavailable, falling back to legacy durl response")
                Result.success(legacyResp.data)
            } else {
                Result.failure(Exception("获取直播流失败: xlive API 和 旧版 API 均不可用"))
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveRepo", " getLivePlayUrlWithQuality failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    /**
     * 发送直播弹幕
     */
    suspend fun sendDanmaku(roomId: Long, msg: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache ?: ""
            if (csrf.isEmpty()) return@withContext Result.failure(Exception("请先登录"))
            
            val resp = api.sendLiveDanmaku(
                roomId = realRoomId,
                msg = msg,
                csrf = csrf,
                csrfToken = csrf
            )
            
            if (resp.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.message.ifBlank { "发送失败" }))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 直播间点赞 (上报)
     */
    suspend fun clickLike(roomId: Long, uid: Long, anchorId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache ?: ""
            
            val resp = api.clickLikeLiveRoom(
                roomId = realRoomId,
                uid = uid,
                anchorId = anchorId,
                csrf = csrf,
                csrfToken = csrf
            )
            
            if (resp.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception(resp.message))
            }
        } catch (e: Exception) {

            // 点赞失败静默处理
            Result.failure(e)
        }
    }

    /**
     * 获取直播弹幕表情
     * 返回: Map<关键词, 图片URL>
     */
    suspend fun getEmoticons(roomId: Long): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val realRoomId = resolveRealRoomId(roomId)
            val resp = api.getLiveEmoticons(roomId = realRoomId)
            if (resp.code == 0 && resp.data?.data != null) {
                val emojiMap = mutableMapOf<String, String>()
                resp.data.data.forEach { pkg ->
                    pkg.emoticons?.forEach { emotion ->
                        if (emotion.emoji.isNotEmpty() && emotion.url.isNotEmpty()) {
                            emojiMap[emotion.emoji] = emotion.url
                        }
                    }
                }
                com.bbttvv.app.core.util.Logger.d("LiveRepo", " Fetched ${emojiMap.size} emoticons for room $roomId(real=$realRoomId)")
                Result.success(emojiMap)
            } else {
                Result.failure(Exception(resp.msg))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 失败不影响主要流程
            Result.failure(e)
        }
    }
}

