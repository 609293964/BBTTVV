package com.bbttvv.app.data.repository

import android.content.Context
import com.bbttvv.app.core.network.AppSignUtils
import com.bbttvv.app.core.network.RecommendationSession
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.defaultDislikeReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecommendationFeedbackRepository {
    /** A generic dismiss never invents reason ids or chooses report/creator/category actions. */
    suspend fun dismiss(context: Context, video: VideoItem): Result<Boolean> = withContext(Dispatchers.IO) {
        val metadata = video.recommendationFeedback ?: return@withContext Result.success(false)
        val reason = metadata.defaultDislikeReason() ?: return@withContext Result.success(false)
        if (!metadata.supportsServerSync || metadata.accountMid <= 0 || metadata.goto.isBlank() || metadata.param.isBlank()) {
            return@withContext Result.success(false)
        }
        try {
            val session = RecommendationSession.capture(context, metadata.accountMid)
                ?: return@withContext Result.success(false)
            if (session.accessToken.isBlank()) return@withContext Result.success(false)
            val params = AppSignUtils.signForTvLogin(mapOf(
                "goto" to metadata.goto, "id" to metadata.param, "reason_id" to reason.id.toString(),
                "build" to "1", "mobi_app" to "android", "access_key" to session.accessToken,
                "appkey" to AppSignUtils.TV_APP_KEY, "ts" to AppSignUtils.getTimestamp().toString(),
            ))
            val response = session.api.submitMobileFeedDislike(params)
            if (response.code != 0) throw IllegalStateException("推荐反馈失败(${response.code})")
            Result.success(true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
}
