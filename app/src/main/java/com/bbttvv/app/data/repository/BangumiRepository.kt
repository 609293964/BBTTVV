// 文件路径: data/repository/BangumiRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.WbiKeyManager
import com.bbttvv.app.core.network.WbiUtils
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class BangumiPlayUrlPayload(
    val code: Int,
    val message: String,
    val videoInfo: BangumiVideoInfo?
)

internal fun buildBangumiPlayUrlParams(
    epId: Long,
    cid: Long,
    qn: Int,
    bvid: String? = null,
    seasonId: Long? = null,
    tryLook: Boolean = true
): Map<String, String> {
    val params = linkedMapOf(
        "ep_id" to epId.toString(),
        "cid" to cid.toString(),
        "qn" to qn.toString(),
        "fnval" to "4048",
        "fnver" to "0",
        "fourk" to "1",
        "voice_balance" to "1",
        "gaia_source" to "pre-load",
        "isGaiaAvoided" to "true",
        "web_location" to "1315873"
    )
    if (seasonId != null && seasonId > 0L) {
        params["season_id"] = seasonId.toString()
    }
    if (tryLook) {
        params["try_look"] = "1"
    }
    if (!bvid.isNullOrBlank()) {
        params["bvid"] = bvid
    }
    return params
}

internal fun signBangumiPlayUrlParams(
    params: Map<String, String>,
    wbiKeys: Pair<String, String>?
): Map<String, String> {
    val (imgKey, subKey) = wbiKeys ?: return params
    if (imgKey.isBlank() || subKey.isBlank()) return params
    return WbiUtils.sign(params, imgKey, subKey)
}

internal fun decodeBangumiPlayUrlPayload(
    rawJson: String,
    json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
): BangumiPlayUrlPayload {
    val root = json.parseToJsonElement(rawJson).jsonObject
    val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
    val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val resultObject = root["result"]?.jsonObject
    val videoInfoElement = resultObject?.get("video_info") ?: root["result"]
    val videoInfo = videoInfoElement?.let {
        json.decodeFromString<BangumiVideoInfo>(it.toString())
    }
    return BangumiPlayUrlPayload(
        code = code,
        message = message,
        videoInfo = videoInfo
    )
}

/**
 * 番剧/影视 Repository
 * 处理番剧、电影、电视剧、纪录片等 PGC 内容
 */
object BangumiRepository {
    private val api = NetworkModule.bangumiApi
    
    /**
     * 获取番剧时间表
     * @param type 1=番剧 4=国创
     */
    suspend fun getTimeline(type: Int = 1): Result<List<TimelineDay>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTimeline(types = type)
            if (response.code == 0 && response.result != null) {
                Result.success(response.result)
            } else {
                Result.failure(Exception("获取时间表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getTimeline error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 获取番剧索引/列表
     * @param seasonType 1=番剧 2=电影 3=纪录片 4=国创 5=电视剧 7=综艺
     */
    suspend fun getBangumiIndex(
        seasonType: Int = 1,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<BangumiIndexData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBangumiIndex(
                seasonType = seasonType,
                st = seasonType,  //  [修复] st 必须与 seasonType 相同
                page = page,
                pageSize = pageSize
            )
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("获取番剧列表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiIndex error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 获取番剧详情
     */
    /**
     * 获取番剧详情
     */
    suspend fun getSeasonDetail(seasonId: Long = 0, epId: Long = 0): Result<BangumiDetail> = withContext(Dispatchers.IO) {
        try {
            //  [修复] 使用 ResponseBody 自行解析，避免大型番剧导致 OOM
            // 优先使用 epId (因为历史记录中的 seasonId 可能是 AVID，而 epId 是准确的)，如果 epId 为 0 则使用 seasonId
            val responseBody = if (epId > 0) {
                api.getSeasonDetail(epId = epId)
            } else if (seasonId > 0) {
                api.getSeasonDetail(seasonId = seasonId)
            } else {
                return@withContext Result.failure(Exception("参数错误: seasonId 和 epId 不能同时为空"))
            }

            var jsonString = responseBody.string()
            
            //  [关键修复] 在解析前预处理 JSON，限制 episodes 数组大小
            // 这是防止 OOM 的核心：在字符串级别截断，避免解析时占用大量内存
            jsonString = limitEpisodesInJson(jsonString, maxEpisodes = 200)
            
            // 使用 kotlinx.serialization.json 手动解析
            val json = kotlinx.serialization.json.Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
            }
            
            val response = json.decodeFromString<BangumiDetailResponse>(jsonString)
            
            if (response.code == 0 && response.result != null) {
                //  [调试] 打印追番状态和认证信息
                val userStatus = response.result.userStatus
                android.util.Log.w("BangumiRepo", """
                     getSeasonDetail 结果:
                    - request seasonId: $seasonId, epId: $epId
                    - result seasonId: ${response.result.seasonId}
                    - title: ${response.result.title}
                    - userStatus: $userStatus
                    - follow: ${userStatus?.follow} (1=已追番, 0=未追番)
                    - SESSDATA存在: ${com.bbttvv.app.core.store.TokenManager.sessDataCache?.isNotEmpty() == true}
                """.trimIndent())
                Result.success(response.result)
            } else {
                Result.failure(Exception("获取番剧详情失败: ${response.message}"))
            }
        } catch (e: OutOfMemoryError) {
            //  [修复] 捕获 OOM 错误，给出更友好的提示
            android.util.Log.e("BangumiRepo", " getSeasonDetail OOM: 番剧数据过大，内存不足", e)
            System.gc() // 尝试触发 GC 回收内存
            Result.failure(Exception("加载失败：番剧数据过大，请稍后重试"))
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getSeasonDetail error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     *  [修复工具] 在 JSON 字符串级别限制 episodes 数组大小
     * 这是防止 OOM 的关键：在解析前截断超大数组
     */
    private fun limitEpisodesInJson(json: String, maxEpisodes: Int): String {
        try {
            // 使用 JsonElement 进行轻量级解析和修改
            val jsonParser = kotlinx.serialization.json.Json { 
                ignoreUnknownKeys = true 
            }
            val jsonElement = jsonParser.parseToJsonElement(json)
            val jsonObject = jsonElement.jsonObject
            
            // 检查 result.episodes 是否存在且过大
            val result = jsonObject["result"]?.jsonObject ?: return json
            val episodes = result["episodes"]?.jsonArray ?: return json
            
            if (episodes.size <= maxEpisodes) {
                return json // 不需要截断
            }
            
            android.util.Log.w("BangumiRepo", " 番剧剧集过多 (${episodes.size}集)，截取前 $maxEpisodes 集以防止内存溢出")
            
            // 构建新的 episodes 数组 (只保留前 maxEpisodes 个)
            val limitedEpisodes = kotlinx.serialization.json.JsonArray(episodes.take(maxEpisodes))
            
            // 构建新的 result 对象
            val newResult = kotlinx.serialization.json.JsonObject(result.toMutableMap().apply {
                put("episodes", limitedEpisodes)
            })
            
            // 构建新的根对象
            val newJsonObject = kotlinx.serialization.json.JsonObject(jsonObject.toMutableMap().apply {
                put("result", newResult)
            })
            
            return newJsonObject.toString()
        } catch (e: Exception) {
            android.util.Log.w("BangumiRepo", "limitEpisodesInJson 处理失败，返回原 JSON: ${e.message}")
            return json // 解析失败时返回原 JSON
        }
    }
    
    /**
     * 获取番剧播放地址
     */
    suspend fun getBangumiPlayUrl(
        epId: Long,
        qn: Int = 80,
        cid: Long = 0L,
        bvid: String? = null,
        seasonId: Long? = null
    ): Result<BangumiVideoInfo> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BangumiRepo", "📡 getBangumiPlayUrl: epId=$epId, cid=$cid, seasonId=$seasonId, qn=$qn")
            val baseParams = buildBangumiPlayUrlParams(
                epId = epId,
                cid = cid,
                qn = qn,
                bvid = bvid,
                seasonId = seasonId
            )
            val wbiKeys = WbiKeyManager.getWbiKeys().getOrNull()
                ?: WbiKeyManager.refreshKeys().getOrNull()
            val signedParams = signBangumiPlayUrlParams(
                params = baseParams,
                wbiKeys = wbiKeys
            )
            android.util.Log.d(
                "BangumiRepo",
                "📡 getBangumiPlayUrl request params: wbiSigned=${signedParams.containsKey("w_rid")}, keys=${signedParams.keys.sorted()}"
            )
            val response = decodeBangumiPlayUrlPayload(
                rawJson = api.getBangumiPlayUrl(
                    signedParams
                ).string()
            )
            android.util.Log.d(
                "BangumiRepo",
                "📡 getBangumiPlayUrl response: code=${response.code}, msg=${response.message}, hasResult=${response.videoInfo != null}"
            )
            
            if (response.code == 0 && response.videoInfo != null) {
                val result = response.videoInfo
                android.util.Log.d("BangumiRepo", "📹 PlayUrl: quality=${result.quality}, hasDash=${result.dash != null}, hasDurl=${!result.durl.isNullOrEmpty()}")
                Result.success(result)
            } else {
                val errorMsg = when (response.code) {
                    -10403 -> "需要大会员才能观看"
                    -404 -> "视频不存在"
                    -101 -> "请先登录后观看"  //  新增：检测需要登录
                    -400 -> "请求参数错误"
                    -403 -> "访问权限不足"
                    else -> "获取播放地址失败: ${response.message} (code=${response.code})"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiPlayUrl error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 追番/追剧
     */
    suspend fun followBangumi(seasonId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
            android.util.Log.w("BangumiRepo", "📌 追番请求: seasonId=$seasonId, csrf=${csrf.take(10)}...")
            val response = api.followBangumi(seasonId = seasonId, csrf = csrf)
            android.util.Log.w("BangumiRepo", "📌 追番响应: code=${response.code}, message=${response.message}")
            if (response.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception("追番失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "followBangumi error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 取消追番/追剧
     */
    suspend fun unfollowBangumi(seasonId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val csrf = TokenManager.csrfCache ?: return@withContext Result.failure(Exception("未登录"))
            val response = api.unfollowBangumi(seasonId = seasonId, csrf = csrf)
            if (response.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception("取消追番失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "unfollowBangumi error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 获取番剧索引/列表（支持筛选）
     */
    suspend fun getBangumiIndexWithFilter(
        seasonType: Int = 1,
        page: Int = 1,
        pageSize: Int = 20,
        filter: BangumiFilter = BangumiFilter()
    ): Result<BangumiIndexData> = withContext(Dispatchers.IO) {
        try {
            val year = filter.toApiYear(seasonType)
            val releaseDate = filter.toApiReleaseDate(seasonType)
            val response = api.getBangumiIndex(
                seasonType = seasonType,
                st = seasonType,
                page = page,
                pageSize = pageSize,
                order = filter.order,
                area = filter.area,
                isFinish = filter.isFinish,
                year = year,
                releaseDate = releaseDate,
                styleId = filter.styleId,
                seasonStatus = filter.seasonStatus
            )
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("获取番剧列表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getBangumiIndexWithFilter error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 搜索番剧
     */
    suspend fun searchBangumi(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<BangumiSearchData> = withContext(Dispatchers.IO) {
        try {
            val navApi = NetworkModule.api
            val searchApi = NetworkModule.searchApi
            
            // 获取 WBI 密钥
            val navResp = navApi.getNavInfo()
            val wbiImg = navResp.data?.wbi_img
            val imgKey = wbiImg?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            val subKey = wbiImg?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
            
            val params = mutableMapOf(
                "keyword" to keyword,
                "search_type" to "media_bangumi",
                "page" to page.toString(),
                "pagesize" to pageSize.toString()
            )
            
            // WBI 签名
            val signedParams = if (imgKey.isNotEmpty()) WbiUtils.sign(params, imgKey, subKey) else params
            val response = searchApi.searchBangumi(signedParams)
            
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("搜索番剧失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "searchBangumi error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     *  [新增] 获取我的追番列表
     */
    suspend fun getMyFollowBangumi(
        type: Int = 1,  // 1=追番 2=追剧
        page: Int = 1,
        pageSize: Int = 30
    ): Result<MyFollowBangumiData> = withContext(Dispatchers.IO) {
        try {
            val mid = TokenManager.midCache ?: return@withContext Result.failure(Exception("未登录"))
            val response = api.getMyFollowBangumi(
                vmid = mid,
                type = type,
                pn = page,
                ps = pageSize
            )
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("获取追番列表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("BangumiRepo", "getMyFollowBangumi error: ${e.message}")
            Result.failure(e)
        }
    }
}

