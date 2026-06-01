package com.bbttvv.app.app.startup

import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerRuntime
import com.bbttvv.app.core.plugin.PluginStore
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.repository.FeedRepository
import com.bbttvv.app.feature.plugin.HOME_FEED_ANONYMIZER_PLUGIN_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object HomeBootstrapper {
    fun preload(scope: CoroutineScope = AppScope.ioScope) {
        scope.launch {
            restoreHomeFeedAnonymizerState()
            FeedRepository.preloadHomeData(scope = scope)
        }
    }

    private suspend fun restoreHomeFeedAnonymizerState() {
        val context = NetworkModule.appContext ?: return
        runCatching {
            PluginStore.getEnabledOrNull(context, HOME_FEED_ANONYMIZER_PLUGIN_ID) == true
        }.onSuccess { enabled ->
            if (enabled && !HomeFeedAnonymizerRuntime.enabled) {
                HomeFeedAnonymizerRuntime.rotateAnonymousSession()
            }
            HomeFeedAnonymizerRuntime.setEnabled(enabled)
        }.onFailure { error ->
            Logger.w("HomeBootstrapper", "Failed to restore home feed anonymizer state", error)
        }
    }
}
