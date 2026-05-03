package com.bbttvv.app.core.di

import com.bbttvv.app.data.repository.ActionRepository
import com.bbttvv.app.data.repository.FeedRepository
import com.bbttvv.app.data.repository.HomeFeedRepository
import com.bbttvv.app.data.repository.UserActionRepository
import com.bbttvv.app.domain.usecase.UserInteractionUseCase

/**
 * 手动依赖注入容器
 *
 * 使用 Kotlin object 单例实现，不使用 Hilt / Dagger / Koin 等框架。
 * 持有 Repository 单例引用，UseCase 通过工厂方法按需创建。
 */
object AppContainer {
    val feedRepository: HomeFeedRepository = FeedRepository
    val userActionRepository: UserActionRepository = ActionRepository

    fun userInteractionUseCase(): UserInteractionUseCase {
        return UserInteractionUseCase(actionRepository = userActionRepository)
    }
}
