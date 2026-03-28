package com.android.purebilibili.feature.video.danmaku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class DanmakuManagerScopePolicyTest {

    @Test
    fun derivedManagerScope_detachesFromComposableJobButKeepsDispatcher() {
        val parentJob = Job()
        val sourceScope = CoroutineScope(Dispatchers.Main + parentJob)

        val managerScope = createDanmakuManagerScope(sourceScope)

        assertNotEquals(parentJob, managerScope.coroutineContext[Job])
        assertSame(Dispatchers.Main, managerScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor])
    }
}
