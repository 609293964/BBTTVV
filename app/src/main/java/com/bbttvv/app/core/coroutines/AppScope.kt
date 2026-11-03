package com.bbttvv.app.core.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal object AppScope {
    private val appJob = SupervisorJob()

    val ioScope: CoroutineScope = CoroutineScope(appJob + Dispatchers.IO)
}

