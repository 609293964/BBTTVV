package com.bbttvv.app.core.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Only the routing choice is stored here; credentials remain in AccountSessionStore. */
object RecommendationAccountStore {
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()

    fun selectedMid(context: Context): Long? = context.getSharedPreferences("recommendation_account", Context.MODE_PRIVATE)
        .getLong("mid", 0L).takeIf { it > 0L }

    fun select(context: Context, mid: Long?) {
        require(mid == null || AccountSessionStore.getAccountsResult(context).getOrThrow().any { it.mid == mid })
        check(context.getSharedPreferences("recommendation_account", Context.MODE_PRIVATE)
            .edit().putLong("mid", mid ?: 0L).commit()) { "无法保存推荐账号" }
        revision.update { it + 1L }
    }
}
