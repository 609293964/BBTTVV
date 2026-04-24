package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
interface MessageApi {
    // 获取未读私信数
    @GET("session_svr/v1/session_svr/single_unread")
    suspend fun getUnreadCount(
        @Query("unread_type") unreadType: Int = 0,
        @Query("show_unfollow_list") showUnfollowList: Int = 1,
        @Query("show_dustbin") showDustbin: Int = 1,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.bbttvv.app.data.model.response.MessageUnreadResponse

    @GET("https://api.bilibili.com/x/msgfeed/unread")
    suspend fun getFeedUnread(
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.1365"
    ): com.bbttvv.app.data.model.response.MessageFeedUnreadResponse

    @GET("https://api.bilibili.com/x/msgfeed/reply")
    suspend fun getReplyFeed(
        @Query("id") cursor: Long? = null,
        @Query("reply_time") cursorTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164"
    ): com.bbttvv.app.data.model.response.MessageFeedReplyResponse

    @GET("https://api.bilibili.com/x/msgfeed/at")
    suspend fun getAtFeed(
        @Query("id") cursor: Long? = null,
        @Query("at_time") cursorTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164"
    ): com.bbttvv.app.data.model.response.MessageFeedAtResponse

    @GET("https://api.bilibili.com/x/msgfeed/like")
    suspend fun getLikeFeed(
        @Query("id") cursor: Long? = null,
        @Query("like_time") cursorTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164"
    ): com.bbttvv.app.data.model.response.MessageFeedLikeResponse

    @GET("https://message.bilibili.com/x/sys-msg/query_notify_list")
    suspend fun getSystemNotices(
        @Query("cursor") cursor: Long? = null,
        @Query("page_size") pageSize: Int = 20,
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0
    ): com.bbttvv.app.data.model.response.SystemNoticeResponse

    @GET("https://message.bilibili.com/x/sys-msg/update_cursor")
    suspend fun updateSystemNoticeCursor(
        @Query("csrf") csrf: String,
        @Query("cursor") cursor: Long,
        @Query("has_up") hasUp: Int = 0,
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.bbttvv.app.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.bilibili.com/x/msgfeed/del")
    suspend fun deleteFeedItem(
        @retrofit2.http.Field("tp") type: Int,
        @retrofit2.http.Field("id") id: Long,
        @retrofit2.http.Field("build") build: Int = 0,
        @retrofit2.http.Field("mobi_app") mobiApp: String = "web",
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.bbttvv.app.data.model.response.SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.bilibili.com/x/msgfeed/notice")
    suspend fun setFeedNotice(
        @retrofit2.http.Field("id") id: Long,
        @retrofit2.http.Field("notice_state") noticeState: Int,
        @retrofit2.http.Field("tp") type: Int = 0,
        @retrofit2.http.Field("platform") platform: String = "web",
        @retrofit2.http.Field("build") build: Int = 0,
        @retrofit2.http.Field("mobi_app") mobiApp: String = "web",
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.bbttvv.app.data.model.response.SimpleApiResponse

    // 获取会话列表
    @GET("session_svr/v1/session_svr/get_sessions")
    suspend fun getSessions(
        @Query("session_type") sessionType: Int = 4,  // 4=所有
        @Query("group_fold") groupFold: Int = 1,
        @Query("unfollow_fold") unfollowFold: Int = 0,
        @Query("sort_rule") sortRule: Int = 2,
        @Query("size") size: Int = 20,
        @Query("build") build: Int = 0,  //  需要此参数才能获取account_info
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.999",  //  网页位置标识
        @Query("pn") pn: Int = 1,  //  页码 (第几页)
        @Query("end_ts") endTs: Long = 0  //  结束时间戳 (游标)
    ): com.bbttvv.app.data.model.response.SessionListResponse
    
    // 获取私信消息记录
    @GET("svr_sync/v1/svr_sync/fetch_session_msgs")
    suspend fun fetchSessionMsgs(
        @Query("talker_id") talkerId: Long,
        @Query("session_type") sessionType: Int = 1,
        @Query("size") size: Int = 20,
        @Query("begin_seqno") beginSeqno: Long = 0,
        @Query("end_seqno") endSeqno: Long = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.bbttvv.app.data.model.response.MessageHistoryResponse
    
    // 发送私信
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("web_im/v1/web_im/send_msg")
    suspend fun sendMsg(
        @retrofit2.http.Field("msg[sender_uid]") senderUid: Long,
        @retrofit2.http.Field("msg[receiver_id]") receiverId: Long,
        @retrofit2.http.Field("msg[receiver_type]") receiverType: Int = 1,
        @retrofit2.http.Field("msg[msg_type]") msgType: Int = 1,
        @retrofit2.http.Field("msg[content]") content: String,
        @retrofit2.http.Field("msg[timestamp]") timestamp: Long,
        @retrofit2.http.Field("msg[dev_id]") devId: String,
        @retrofit2.http.Field("msg[new_face_version]") newFaceVersion: Int = 1,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.bbttvv.app.data.model.response.SendMessageResponse
    
    // 设置会话已读
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/update_ack")
    suspend fun updateAck(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("ack_seqno") ackSeqno: Long,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.bbttvv.app.data.model.response.SimpleApiResponse
    
    // 置顶/取消置顶会话
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/set_top")
    suspend fun setTop(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("op_type") opType: Int,  // 0=置顶, 1=取消置顶
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.bbttvv.app.data.model.response.SimpleApiResponse
    
    // 移除会话
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/remove_session")
    suspend fun removeSession(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.bbttvv.app.data.model.response.SimpleApiResponse
}
