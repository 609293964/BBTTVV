package com.bbttvv.app.core.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal enum class ScopeOwner {
    APP,
    LIFECYCLE
}

internal enum class LaunchThread {
    MAIN,
    IO
}

internal data class AsyncLaunchPolicy(
    val scopeOwner: ScopeOwner,
    val thread: LaunchThread
)

internal fun resolveHomePreloadLaunchPolicy(): AsyncLaunchPolicy {
    return AsyncLaunchPolicy(scopeOwner = ScopeOwner.APP, thread = LaunchThread.IO)
}

internal fun resolveLocalProxyWarmupLaunchPolicy(): AsyncLaunchPolicy {
    return AsyncLaunchPolicy(scopeOwner = ScopeOwner.APP, thread = LaunchThread.IO)
}

internal fun resolveShortLinkLaunchPolicy(): AsyncLaunchPolicy {
    return AsyncLaunchPolicy(scopeOwner = ScopeOwner.LIFECYCLE, thread = LaunchThread.MAIN)
}

internal object AppScope {
    private val appJob = SupervisorJob()

    val ioScope: CoroutineScope = CoroutineScope(appJob + Dispatchers.IO)
}

