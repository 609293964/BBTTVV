package com.bbttvv.app.core.di

import com.bbttvv.app.data.repository.FeedRepository
import com.bbttvv.app.data.repository.HomeFeedRepository

/**
 * 手动依赖注入容器
 *
 * 使用 Kotlin object 单例实现，不使用 Hilt / Dagger / Koin 等框架。
 * 持有应用共享的 Repository 引用。
 */
object AppContainer {
    val feedRepository: HomeFeedRepository = FeedRepository
}
