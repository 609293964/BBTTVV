// 文件路径: data/repository/CommentRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.BilibiliApi
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.WbiUtils
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.CommentFraudStatus
import com.bbttvv.app.data.model.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.TreeMap

/**
 * 评论相关数据仓库
 * 从 VideoRepository 拆分出来，专注于评论功能
 */
object CommentRepository {
    private val api = NetworkModule.api
    private val guestApi = NetworkModule.guestApi
    private val commentJson = Json { ignoreUnknownKeys = true }
    private const val EMOTE_MAP_CACHE_TTL_MS = 24L * 60 * 60 * 1000

    private data class EmoteMapCache(
        val fetchedAtMs: Long,
        val map: Map<String, String>
    )

    @Volatile
    private var emoteMapCache: EmoteMapCache? = null
    private val emoteMapLock = Mutex()

    // WBI Key 缓存
    private var wbiKeysCache: Pair<String, String>? = null
    private var wbiKeysTimestamp: Long = 0
    private const val WBI_CACHE_DURATION = 1000 * 60 * 30 // 30分钟缓存

    @Serializable
    private data class CommentPicturePayload(
        @SerialName("img_src") val imgSrc: String,
        @SerialName("img_width") val imgWidth: Int,
        @SerialName("img_height") val imgHeight: Int,
        @SerialName("img_size") val imgSize: Float
    )

    /**
     * 获取 WBI Keys（用于 WBI 签名）
     */
    private suspend fun getWbiKeys(navApi: BilibiliApi = api): Pair<String, String> {
        val currentCheck = System.currentTimeMillis()
        val cached = wbiKeysCache
        if (cached != null && (currentCheck - wbiKeysTimestamp < WBI_CACHE_DURATION)) {
            return cached
        }

        val maxRetries = 3
        var lastError: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                val navResp = navApi.getNavInfo()
                val wbiImg = navResp.data?.wbi_img
                
                if (wbiImg != null) {
                    val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
                    val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
                    
                    wbiKeysCache = Pair(imgKey, subKey)
                    wbiKeysTimestamp = System.currentTimeMillis()
                    com.bbttvv.app.core.util.Logger.d("CommentRepo", " WBI Keys obtained successfully (attempt $attempt)")
                    return wbiKeysCache!!
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("CommentRepo", "getWbiKeys attempt $attempt failed: ${e.message}")
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(200L * attempt) // 递增延迟
                }
            }
        }
        
        throw Exception("Wbi Keys Error after $maxRetries attempts: ${lastError?.message}")
    }

    private fun resolveReadApi(mode: CommentReadApiMode): BilibiliApi {
        return when (mode) {
            CommentReadApiMode.AUTH -> api
            CommentReadApiMode.GUEST -> guestApi
        }
    }

    private suspend fun fetchCommentsByApi(
        apiClient: BilibiliApi,
        oid: Long,
        type: Int,
        page: Int,
        ps: Int,
        mode: Int
    ): ReplyResponse {
        return when (mode) {
            2 -> {
                Logger.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=0 (时间)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 0
                )
            }
            1 -> {
                Logger.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=2 (回复数)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 2
                )
            }
            4 -> {
                Logger.d("CommentRepo", " getComments (Legacy): oid=$oid, type=$type, page=$page, sort=1 (点赞数)")
                apiClient.getReplyListLegacy(
                    oid = oid,
                    type = type,
                    pn = page,
                    ps = ps,
                    sort = 1
                )
            }
            else -> {
                val (imgKey, subKey) = getWbiKeys(apiClient)
                Logger.d("CommentRepo", " getComments (WBI): oid=$oid, type=$type, page=$page, mode=3 (热度)")
                val params = TreeMap<String, String>()
                params["oid"] = oid.toString()
                params["type"] = type.toString()
                params["mode"] = "3"
                params["ps"] = ps.toString()
                params["plat"] = "1"
                params["web_location"] = "1315875"
                if (page <= 1) {
                    params["seek_rpid"] = "0"
                    params["pagination_str"] = """{"offset":""}"""
                } else {
                    params["next"] = page.toString()
                }
                val signedParams = WbiUtils.sign(params, imgKey, subKey)
                apiClient.getReplyList(signedParams)
            }
        }
    }

    private suspend fun fetchGuestHotCommentsCompat(
        oid: Long,
        type: Int,
        page: Int,
        ps: Int
    ): ReplyResponse {
        Logger.d("CommentRepo", " getComments (CompatMain): oid=$oid, type=$type, page=$page, mode=3 (热度)")
        val params = TreeMap<String, String>()
        params["oid"] = oid.toString()
        params["type"] = type.toString()
        params["mode"] = "3"
        params["ps"] = ps.toString()
        params["plat"] = "1"
        params["web_location"] = "1315875"
        if (page <= 1) {
            params["seek_rpid"] = "0"
            params["pagination_str"] = """{"offset":""}"""
        } else {
            params["next"] = page.toString()
        }
        return guestApi.getReplyListMain(params)
    }

    /**
     * 获取评论列表
     * @param mode 排序模式:
     * 3=最热(WBI mode=3), 2=最新(legacy sort=0), 4=点赞(legacy sort=1), 1=回复(legacy sort=2)
     */
    suspend fun getComments(aid: Long, page: Int, ps: Int = 20, mode: Int = 3): Result<ReplyData> = withContext(Dispatchers.IO) {
        getCommentsForSubject(oid = aid, type = 1, page = page, ps = ps, mode = mode)
    }

    suspend fun getCommentsForSubject(
        oid: Long,
        type: Int,
        page: Int,
        ps: Int = 20,
        mode: Int = 3
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            // 确保 buvid3 已初始化
            SubtitleAndAuxRepository.ensureBuvid3()
            val hasSession = !com.bbttvv.app.core.store.TokenManager.sessDataCache.isNullOrEmpty()
            val readPlan = resolveCommentReadPlan(hasSession = hasSession)
            val primaryMode = readPlan.primary
            val primaryResponse = fetchCommentsByApi(
                apiClient = resolveReadApi(primaryMode),
                oid = oid,
                type = type,
                page = page,
                ps = ps,
                mode = mode
            )
            val finalResponse = if (
                shouldFallbackGuestHotCommentReadOnEmptySuccess(
                    primaryMode = primaryMode,
                    page = page,
                    mode = mode,
                    responseCode = primaryResponse.code,
                    data = primaryResponse.data
                )
            ) {
                Logger.w(
                    "CommentRepo",
                    "getComments empty-success fallback triggered: from=$primaryMode to=compat-main, oid=$oid, type=$type, page=$page, mode=$mode, total=${primaryResponse.data?.getAllCount() ?: 0}"
                )
                val compatResponse = fetchGuestHotCommentsCompat(
                    oid = oid,
                    type = type,
                    page = page,
                    ps = ps
                )
                if (
                    compatResponse.code != 0 &&
                    readPlan.fallback != null &&
                    shouldFallbackCommentRead(compatResponse.code)
                ) {
                    val fallbackMode = readPlan.fallback
                    Logger.w(
                        "CommentRepo",
                        "getComments compat fallback triggered: code=${compatResponse.code}, from=compat-main to=$fallbackMode, oid=$oid, type=$type, page=$page, mode=$mode"
                    )
                    fetchCommentsByApi(
                        apiClient = resolveReadApi(fallbackMode),
                        oid = oid,
                        type = type,
                        page = page,
                        ps = ps,
                        mode = mode
                    )
                } else {
                    compatResponse
                }
            } else if (
                primaryResponse.code != 0 &&
                readPlan.fallback != null &&
                shouldFallbackCommentRead(primaryResponse.code)
            ) {
                val fallbackMode = readPlan.fallback
                Logger.w(
                    "CommentRepo",
                    "getComments fallback triggered: code=${primaryResponse.code}, from=$primaryMode to=$fallbackMode, oid=$oid, type=$type, page=$page, mode=$mode"
                )
                fetchCommentsByApi(
                    apiClient = resolveReadApi(fallbackMode),
                    oid = oid,
                    type = type,
                    page = page,
                    ps = ps,
                    mode = mode
                )
            } else {
                primaryResponse
            }
            
            val sortLabel = when (mode) {
                2 -> "时间"
                4 -> "点赞数"
                1 -> "回复数"
                else -> "热度"
            }
            Logger.d(
                "CommentRepo",
                " getComments result: oid=$oid, type=$type, mode=$mode($sortLabel), replies=${finalResponse.data?.replies?.size ?: 0}, code=${finalResponse.code}"
            )

            if (finalResponse.code == 0) {
                Result.success(finalResponse.data ?: ReplyData())
            } else {
                val errorMsg = resolveCommentReadErrorMessage(finalResponse.code)
                android.util.Log.e("CommentRepo", " getComments failed: oid=$oid, type=$type, ${finalResponse.code} - ${finalResponse.message}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("CommentRepo", " getComments exception: oid=$oid, type=$type, ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCommentCountForSubject(
        oid: Long,
        type: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            SubtitleAndAuxRepository.ensureBuvid3()
            val response = api.getReplyCount(oid = oid, type = type)
            if (response.code == 0) {
                Result.success(response.data?.count ?: 0)
            } else {
                Result.failure(Exception(resolveCommentReadErrorMessage(response.code)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取二级评论（楼中楼）
     */
    suspend fun getSubComments(aid: Long, rootId: Long, page: Int, ps: Int = 20): Result<ReplyData> = withContext(Dispatchers.IO) {
        getSubCommentsForSubject(
            oid = aid,
            type = 1,
            rootId = rootId,
            page = page,
            ps = ps
        )
    }

    suspend fun getSubCommentsForSubject(
        oid: Long,
        type: Int,
        rootId: Long,
        page: Int,
        ps: Int = 20
    ): Result<ReplyData> = withContext(Dispatchers.IO) {
        try {
            // 确保 buvid3 已初始化
            SubtitleAndAuxRepository.ensureBuvid3()
            
            Logger.d("CommentRepo", " getSubComments: oid=$oid, type=$type, rootId=$rootId, page=$page")
            val hasSession = !com.bbttvv.app.core.store.TokenManager.sessDataCache.isNullOrEmpty()
            val readPlan = resolveCommentReadPlan(hasSession = hasSession)
            val primaryMode = readPlan.primary
            val primaryResponse = resolveReadApi(primaryMode).getReplyReply(
                oid = oid,
                type = type,
                root = rootId,
                pn = page,
                ps = ps
            )
            val finalResponse = if (
                primaryResponse.code != 0 &&
                readPlan.fallback != null &&
                shouldFallbackCommentRead(primaryResponse.code)
            ) {
                val fallbackMode = readPlan.fallback
                Logger.w(
                    "CommentRepo",
                    "getSubComments fallback triggered: code=${primaryResponse.code}, from=$primaryMode to=$fallbackMode, oid=$oid, type=$type, root=$rootId, page=$page"
                )
                resolveReadApi(fallbackMode).getReplyReply(
                    oid = oid,
                    type = type,
                    root = rootId,
                    pn = page,
                    ps = ps
                )
            } else {
                primaryResponse
            }
            
            Logger.d("CommentRepo", " getSubComments response: oid=$oid, type=$type, code=${finalResponse.code}, replies=${finalResponse.data?.replies?.size ?: 0}")
            
            if (finalResponse.code == 0) {
                Result.success(finalResponse.data ?: ReplyData())
            } else {
                android.util.Log.e("CommentRepo", " getSubComments failed: oid=$oid, type=$type, ${finalResponse.code} - ${finalResponse.message}")
                val errorMsg = resolveCommentReadErrorMessage(finalResponse.code)
                    .replace("评论", "回复")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("CommentRepo", " getSubComments exception: oid=$oid, type=$type, ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取表情包映射
     */
    suspend fun getEmoteMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        getFreshEmoteMapCache(now)?.let { return@withContext it }
        emoteMapLock.withLock {
            getFreshEmoteMapCache(now)?.let { return@withLock it }
            fetchReplyEmoteMap().also { map ->
                emoteMapCache = EmoteMapCache(fetchedAtMs = System.currentTimeMillis(), map = map)
            }
        }
    }

    suspend fun warmupEmoteMap(): Int = withContext(Dispatchers.IO) {
        getEmoteMap().size
    }

    private fun getFreshEmoteMapCache(now: Long): Map<String, String>? {
        val cached = emoteMapCache ?: return null
        if (now - cached.fetchedAtMs > EMOTE_MAP_CACHE_TTL_MS) return null
        return cached.map
    }

    private suspend fun fetchReplyEmoteMap(): Map<String, String> {
        val map = defaultReplyEmoteMap().toMutableMap()
        try {
            val params = mutableMapOf("business" to "reply")

            val response = api.getEmotes(params)
            val packages = response.data?.packages ?: response.data?.all_packages
            packages?.forEach { pkg ->
                pkg.emote?.forEach { emote -> map[emote.text] = emote.url }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.w("CommentRepo", "getEmoteMap failed, using defaults: ${error.message}")
        }
        return map.toMap()
    }

    private fun defaultReplyEmoteMap(): Map<String, String> {
        return mapOf(
            "[doge]" to "http://i0.hdslb.com/bfs/emote/6f8743c3c13009f4705307b2750e32f5068225e3.png",
            "[笑哭]" to "http://i0.hdslb.com/bfs/emote/500b63b2f293309a909403a746566fdd6104d498.png",
            "[妙啊]" to "http://i0.hdslb.com/bfs/emote/03c39c8eb009f63568971032b49c716259c72441.png"
        )
    }

    /**
     * 获取表情包列表 (用于UI展示)
     */
    suspend fun getEmotePackages(): Result<List<EmotePackage>> = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf("business" to "reply")
            
            val response = api.getEmotes(params)
            if (response.code == 0) {
                val data = response.data
                val pkgs = data?.packages ?: data?.all_packages ?: emptyList()
                Result.success(pkgs)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 发送评论
     * @param aid 视频 aid
     * @param message 评论内容
     * @param root 根评论 rpid（回复时需要）
     * @param parent 父评论 rpid
     * @return 新评论的 rpid
     */
    suspend fun addComment(
        aid: Long,
        message: String,
        root: Long = 0,
        parent: Long = 0,
        pictures: List<ReplyPicture> = emptyList()
    ): Result<ReplyItem?> = withContext(Dispatchers.IO) {
        addCommentForSubject(
            oid = aid,
            type = 1,
            message = message,
            root = root,
            parent = parent,
            pictures = pictures
        )
    }

    suspend fun addCommentForSubject(
        oid: Long,
        type: Int,
        message: String,
        root: Long = 0,
        parent: Long = 0,
        pictures: List<ReplyPicture> = emptyList()
    ): Result<ReplyItem?> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            val picturePayload = buildPicturesPayload(pictures)
            
            val response = api.addReply(
                oid = oid,
                type = type,
                message = message,
                root = root.takeIf { it > 0L },
                parent = parent.takeIf { it > 0L },
                pictures = picturePayload,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(response.data?.reply)
            } else {
                Logger.e(
                    "CommentRepo",
                    "addComment failed: oid=$oid, type=$type, root=$root, parent=$parent, pictureCount=${pictures.size}, code=${response.code}, message=${response.message}"
                )
                val errorMsg = when (response.code) {
                    -101 -> "请先登录"
                    -102 -> "账号被封禁"
                    -509 -> "请求过于频繁"
                    12002 -> "评论区已关闭"
                    12015 -> "需要评论验证码"
                    12016 -> "评论内容包含敏感信息"
                    12025 -> "评论字数过多"
                    12035 -> "您已被UP主拉黑"
                    12051 -> "重复评论，请勿刷屏"
                    else -> response.message.ifEmpty { "发送失败 (${response.code})" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Logger.e("CommentRepo", "addComment exception: oid=$oid, type=$type, root=$root, parent=$parent", e)
            Result.failure(e)
        }
    }

    /**
     * 上传评论图片，返回可用于评论 pictures 字段的元数据
     */
    suspend fun uploadCommentImage(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<ReplyPicture> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }

            val mediaType = mimeType.toMediaType()
            val fileBody = bytes.toRequestBody(mediaType)
            val part = okhttp3.MultipartBody.Part.createFormData(
                "file_up",
                fileName.ifBlank { "comment_image.jpg" },
                fileBody
            )
            val textMedia = "text/plain".toMediaType()
            val categoryBody = "daily".toRequestBody(textMedia)
            val bizBody = "new_dyn".toRequestBody(textMedia)
            val csrfBody = csrf.toRequestBody(textMedia)

            val response = api.uploadCommentImage(
                fileUp = part,
                category = categoryBody,
                biz = bizBody,
                csrf = csrfBody
            )

            if (response.code == 0 && response.data != null) {
                val data = response.data
                Result.success(
                    ReplyPicture(
                        imgSrc = data.imageUrl,
                        imgWidth = data.imageWidth,
                        imgHeight = data.imageHeight,
                        imgSize = data.imgSize
                    )
                )
            } else {
                Logger.e(
                    "CommentRepo",
                    "uploadCommentImage failed: fileName=$fileName, mimeType=$mimeType, size=${bytes.size}, code=${response.code}, message=${response.message}"
                )
                Result.failure(Exception(response.message.ifEmpty { "图片上传失败 (${response.code})" }))
            }
        } catch (e: Exception) {
            Logger.e(
                "CommentRepo",
                "uploadCommentImage exception: fileName=$fileName, mimeType=$mimeType, size=${bytes.size}",
                e
            )
            Result.failure(e)
        }
    }

    internal fun buildPicturesPayload(pictures: List<ReplyPicture>): String? {
        if (pictures.isEmpty()) return null
        val payload = pictures.map { picture ->
            CommentPicturePayload(
                imgSrc = picture.imgSrc,
                imgWidth = picture.imgWidth,
                imgHeight = picture.imgHeight,
                imgSize = picture.imgSize
            )
        }
        return commentJson.encodeToString(payload)
    }
    
    /**
     * [新增] 点赞评论
     */
    suspend fun likeComment(aid: Long, rpid: Long, like: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.likeReply(
                oid = aid,
                type = 1,
                rpid = rpid,
                action = if (like) 1 else 0,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "操作失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 点踩评论
     */
    suspend fun hateComment(aid: Long, rpid: Long, hate: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.hateReply(
                oid = aid,
                type = 1,
                rpid = rpid,
                action = if (hate) 1 else 0,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "操作失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 删除评论
     */
    suspend fun deleteComment(aid: Long, rpid: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.deleteReply(
                oid = aid,
                type = 1,
                rpid = rpid,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                val errorMsg = when (response.code) {
                    -403 -> "无权删除此评论"
                    12022 -> "评论已被删除"
                    else -> response.message.ifEmpty { "删除失败" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * [新增] 举报评论
     * @param reason 举报原因: 0=其他, 1=垃圾广告, 2=色情, 3=刷屏, 4=引战, 5=剧透, 6=政治, 7=人身攻击
     */
    suspend fun reportComment(aid: Long, rpid: Long, reason: Int, content: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache
            if (csrf.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("请先登录"))
            }
            
            val response = api.reportReply(
                oid = aid,
                type = 1,
                rpid = rpid,
                reason = reason,
                content = content,
                csrf = csrf
            )
            
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                val errorMsg = when (response.code) {
                    12008 -> "已经举报过了"
                    12019 -> "举报过于频繁"
                    else -> response.message.ifEmpty { "举报失败" }
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 评论反诈检测 ====================

    /** 默认等待时间（毫秒），发送后等待系统处理 */
    private const val DEFAULT_WAIT_MS = 5000L
    /** 带图评论额外等待时间 */
    private const val IMAGE_EXTRA_WAIT_MS = 10000L
    /** 删除判定前的二次确认等待 */
    private const val DELETE_CONFIRM_RETRY_DELAY_MS = 2200L

    /**
     * [新增] 评论反诈检测 - 检查刚发送的评论是否被 ShadowBan / 秒删 / 审核
     *
     * 核心逻辑参考 biliSendCommAntifraud:
     * - ShadowBan 评论: 带 Cookie 能在列表中找到，不带 Cookie 找不到
     * - 秒删评论: 带 Cookie 请求回复页也提示"已经被删除了"
     * - 疑似审核: 带 Cookie 获取回复页成功，但不带 Cookie 获取回复页也成功
     *
     * @param aid 视频/稿件 aid (oid)
     * @param rpid 评论 rpid
     * @param rootId 根评论 rpid (0 表示自己就是根评论)
     * @param hasPictures 是否包含图片（影响等待时间）
     * @param waitMs 自定义等待时间（传0跳过等待）
     * @return CommentFraudStatus 检测结果
     */
    suspend fun checkCommentStatus(
        aid: Long,
        rpid: Long,
        rootId: Long = 0,
        hasPictures: Boolean = false,
        waitMs: Long = -1
    ): Result<CommentFraudStatus> = withContext(Dispatchers.IO) {
        try {
            // 1. 等待系统处理
            val actualWait = when {
                waitMs >= 0 -> waitMs
                hasPictures -> DEFAULT_WAIT_MS + IMAGE_EXTRA_WAIT_MS
                else -> DEFAULT_WAIT_MS
            }
            if (actualWait > 0) {
                Logger.d("CommentFraud", "等待 ${actualWait}ms 后开始检测...")
                delay(actualWait)
            }

            val isReply = rootId > 0
            Logger.d("CommentFraud", "开始检测: aid=$aid, rpid=$rpid, root=$rootId, isReply=$isReply")

            if (isReply) {
                Result.success(checkReplyComment(aid, rpid, rootId))
            } else {
                Result.success(checkRootComment(aid, rpid))
            }
        } catch (e: Exception) {
            Logger.e("CommentFraud", "检测异常: ${e.message}", e)
            Result.success(CommentFraudStatus.UNKNOWN)
        }
    }

    /**
     * 检查回复评论（楼中楼）的状态
     * 流程:
     * 1) guest seek_rpid 精确探测
     * 2) auth seek_rpid 精确探测
     * 3) 仅在双端持续未命中时才判秒删，避免瞬时延迟误判
     */
    private suspend fun checkReplyComment(aid: Long, rpid: Long, rootId: Long): CommentFraudStatus {
        Logger.d("CommentFraud", "[回复] Step1: guest seek_rpid 检测 rpid=$rpid root=$rootId")
        val guestProbe = probeCommentPresenceBySeekRpid(
            apiClient = guestApi,
            aid = aid,
            targetRpid = rpid
        )
        if (guestProbe.requestSucceeded && guestProbe.found) {
            Logger.d("CommentFraud", "[回复] ✅ guest 已找到评论")
            return CommentFraudStatus.NORMAL
        }

        Logger.d("CommentFraud", "[回复] Step2: auth seek_rpid 检测 rpid=$rpid")
        val authProbe = probeCommentPresenceBySeekRpid(
            apiClient = api,
            aid = aid,
            targetRpid = rpid
        )

        var confirmedNotFoundAfterRetry = false
        if (guestProbe.requestSucceeded &&
            !guestProbe.found &&
            authProbe.requestSucceeded &&
            !authProbe.found &&
            !authProbe.deletedHint
        ) {
            Logger.d("CommentFraud", "[回复] Step3: 二次确认未命中，避免瞬时误判")
            confirmedNotFoundAfterRetry = confirmDeletedBySecondProbe(aid = aid, rpid = rpid)
        }

        val status = resolveReplyFraudStatus(
            guestProbe = guestProbe,
            authProbe = authProbe,
            confirmedNotFoundAfterRetry = confirmedNotFoundAfterRetry
        )
        Logger.d(
            "CommentFraud",
            "[回复] 判定结果=$status guest=$guestProbe auth=$authProbe retry=$confirmedNotFoundAfterRetry"
        )
        return status
    }

    /**
     * 检查根评论的状态
     * 流程:
     * 1) guest seek_rpid 精确探测
     * 2) auth seek_rpid 精确探测
     * 3) 若 auth 找到而 guest 未找到，再用 guest 取回复页区分审核中/ShadowBan
     * 4) 仅在双端持续未命中时才判秒删，避免瞬时延迟误判
     */
    private suspend fun checkRootComment(aid: Long, rpid: Long): CommentFraudStatus {
        Logger.d("CommentFraud", "[根评论] Step1: guest seek_rpid 检测 rpid=$rpid")
        val guestSeekProbe = probeCommentPresenceBySeekRpid(
            apiClient = guestApi,
            aid = aid,
            targetRpid = rpid
        )
        if (guestSeekProbe.requestSucceeded && guestSeekProbe.found) {
            Logger.d("CommentFraud", "[根评论] ✅ guest 已找到评论")
            return CommentFraudStatus.NORMAL
        }

        Logger.d("CommentFraud", "[根评论] Step2: auth seek_rpid 检测 rpid=$rpid")
        val authSeekProbe = probeCommentPresenceBySeekRpid(
            apiClient = api,
            aid = aid,
            targetRpid = rpid
        )

        val guestReplyPageVisible: Boolean? =
            if (authSeekProbe.requestSucceeded &&
                authSeekProbe.found &&
                guestSeekProbe.requestSucceeded &&
                !guestSeekProbe.found
            ) {
                Logger.d("CommentFraud", "[根评论] Step3: guest 回复页可见性检测 root=$rpid")
                probeGuestReplyPageVisibility(aid = aid, rootRpid = rpid)
            } else {
                null
            }

        var confirmedNotFoundAfterRetry = false
        if (guestSeekProbe.requestSucceeded &&
            !guestSeekProbe.found &&
            authSeekProbe.requestSucceeded &&
            !authSeekProbe.found &&
            !authSeekProbe.deletedHint
        ) {
            Logger.d("CommentFraud", "[根评论] Step4: 二次确认未命中，避免瞬时误判")
            confirmedNotFoundAfterRetry = confirmDeletedBySecondProbe(aid = aid, rpid = rpid)
        }

        val status = resolveRootFraudStatus(
            guestSeekProbe = guestSeekProbe,
            authSeekProbe = authSeekProbe,
            guestReplyPageVisible = guestReplyPageVisible,
            confirmedNotFoundAfterRetry = confirmedNotFoundAfterRetry
        )
        Logger.d(
            "CommentFraud",
            "[根评论] 判定结果=$status guest=$guestSeekProbe auth=$authSeekProbe guestReply=$guestReplyPageVisible retry=$confirmedNotFoundAfterRetry"
        )
        return status
    }

    private suspend fun probeCommentPresenceBySeekRpid(
        apiClient: BilibiliApi,
        aid: Long,
        targetRpid: Long
    ): CommentPresenceProbe {
        return try {
            val (imgKey, subKey) = getWbiKeys()
            val params = TreeMap<String, String>().apply {
                put("oid", aid.toString())
                put("type", "1")
                put("mode", "2") // 时间排序
                put("next", "1")
                put("ps", "20")
                put("seek_rpid", targetRpid.toString())
            }
            val signedParams = WbiUtils.sign(params, imgKey, subKey)
            val response = apiClient.getReplyList(signedParams)
            when (response.code) {
                0 -> {
                    CommentPresenceProbe(
                        requestSucceeded = true,
                        found = containsTargetRpid(response.data, targetRpid),
                        deletedHint = false
                    )
                }
                12002, 12009 -> {
                    CommentPresenceProbe(
                        requestSucceeded = true,
                        found = false,
                        deletedHint = true
                    )
                }
                else -> {
                    Logger.w("CommentFraud", "seek_rpid probe failed: code=${response.code}, message=${response.message}")
                    CommentPresenceProbe(
                        requestSucceeded = false,
                        found = false,
                        deletedHint = false
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("CommentFraud", "seek_rpid probe exception: ${e.message}")
            CommentPresenceProbe(
                requestSucceeded = false,
                found = false,
                deletedHint = false
            )
        }
    }

    private fun containsTargetRpid(data: ReplyData?, targetRpid: Long): Boolean {
        if (targetRpid <= 0L || data == null) return false
        val inReplies = data.replies.orEmpty().any { reply ->
            reply.rpid == targetRpid ||
                reply.replies.orEmpty().any { sub -> sub.rpid == targetRpid }
        }
        if (inReplies) return true

        val inHots = data.hots.orEmpty().any { reply ->
            reply.rpid == targetRpid ||
                reply.replies.orEmpty().any { sub -> sub.rpid == targetRpid }
        }
        if (inHots) return true

        return data.collectTopReplies().any { reply ->
            reply.rpid == targetRpid ||
                reply.replies.orEmpty().any { sub -> sub.rpid == targetRpid }
        }
    }

    private suspend fun probeGuestReplyPageVisibility(aid: Long, rootRpid: Long): Boolean? {
        try {
            val guestReplyResponse = guestApi.getReplyReply(
                oid = aid,
                root = rootRpid,
                pn = 1,
                ps = 1
            )
            return when (guestReplyResponse.code) {
                0 -> true
                12002, 12009 -> false
                else -> {
                    Logger.w("CommentFraud", "guest reply page probe failed: code=${guestReplyResponse.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e("CommentFraud", "guest reply page probe exception: ${e.message}")
            return null
        }
    }

    private suspend fun confirmDeletedBySecondProbe(aid: Long, rpid: Long): Boolean {
        delay(DELETE_CONFIRM_RETRY_DELAY_MS)
        val guestRetryProbe = probeCommentPresenceBySeekRpid(
            apiClient = guestApi,
            aid = aid,
            targetRpid = rpid
        )
        if (!guestRetryProbe.requestSucceeded || guestRetryProbe.found) {
            return false
        }
        val authRetryProbe = probeCommentPresenceBySeekRpid(
            apiClient = api,
            aid = aid,
            targetRpid = rpid
        )
        return authRetryProbe.requestSucceeded && !authRetryProbe.found
    }

}

