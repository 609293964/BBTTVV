package com.bbttvv.app.app.startup

import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.data.repository.FeedRepository
import kotlinx.coroutines.CoroutineScope

internal object HomeBootstrapper {
    fun preload(scope: CoroutineScope = AppScope.ioScope) {
        FeedRepository.preloadHomeData(scope = scope)
    }
}
