package com.bbttvv.app.data.service.video
import com.bbttvv.app.core.store.AccountSessionEpoch
import org.junit.Assert.*
import org.junit.Test
class AccountCacheEpochTest {
    @Test fun oldRequestCannotOverwriteNewAccountEntry() {
        val cache = SynchronizedLruMap<String, Int>(4)
        val oldEpoch = AccountSessionEpoch.current()
        cache["video"] = 1
        AccountSessionEpoch.advance()
        assertNull(cache["video"])
        cache["video"] = 2
        cache.putForEpoch("video", 3, oldEpoch)
        assertEquals(2, cache["video"])
        assertEquals(1, cache.size())
    }
    @Test fun navCacheRejectsOldAccountResult() {
        val epoch = AccountSessionEpoch.current()
        AccountSessionEpoch.advance()
        VideoCacheService.putNavInfo(com.bbttvv.app.data.model.response.NavData(isLogin = true), epoch)
        assertNull(VideoCacheService.cachedNavInfo)
    }
}
