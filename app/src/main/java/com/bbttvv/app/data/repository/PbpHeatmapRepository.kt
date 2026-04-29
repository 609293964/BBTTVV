package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.PbpHeatmapResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PbpHeatmapRepository {
    suspend fun getHeatmap(
        cid: Long,
        aid: Long,
        bvid: String,
    ): Result<PbpHeatmapResponse> = withContext(Dispatchers.IO) {
        runCatching {
            NetworkModule.guestApi.getPbpHeatmap(
                cid = cid,
                aid = aid.takeIf { it > 0L },
                bvid = bvid.takeIf { it.isNotBlank() },
            )
        }
    }
}
