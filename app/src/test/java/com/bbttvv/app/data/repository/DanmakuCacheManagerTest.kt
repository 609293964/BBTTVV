package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuCacheManagerTest {
    @Test
    fun `raw xml cache evicts least recently used entry by byte capacity`() {
        val manager = DanmakuCacheManager(
            DanmakuCacheProfile(rawXmlMaxBytes = 8L, segmentMaxBytes = 32L)
        )

        manager.putRawXml(1L, ByteArray(4) { 1 })
        manager.putRawXml(2L, ByteArray(4) { 2 })
        assertNotNull(manager.getRawXml(1L))

        manager.putRawXml(3L, ByteArray(4) { 3 })

        assertNotNull(manager.getRawXml(1L))
        assertNull(manager.getRawXml(2L))
        assertNotNull(manager.getRawXml(3L))
        assertEquals(2, manager.stats().rawEntryCount)
        assertEquals(8L, manager.stats().rawBytes)
    }

    @Test
    fun `segment cache stores independent segment keys`() {
        val manager = DanmakuCacheManager(
            DanmakuCacheProfile(rawXmlMaxBytes = 8L, segmentMaxBytes = 8L)
        )
        val first = DanmakuSegmentCacheKey(cid = 10L, segmentIndex = 1)
        val second = DanmakuSegmentCacheKey(cid = 10L, segmentIndex = 2)
        val otherCid = DanmakuSegmentCacheKey(cid = 11L, segmentIndex = 1)

        manager.putSegment(first, ByteArray(4) { 1 })
        manager.putSegment(second, ByteArray(4) { 2 })
        assertNotNull(manager.getSegment(first))

        manager.putSegment(otherCid, ByteArray(4) { 3 })

        assertNotNull(manager.getSegment(first))
        assertNull(manager.getSegment(second))
        assertNotNull(manager.getSegment(otherCid))
        assertEquals(2, manager.stats().segmentEntryCount)
    }

    @Test
    fun `trimDanmakuCache reduces caches to target bytes`() {
        val manager = DanmakuCacheManager(
            DanmakuCacheProfile(rawXmlMaxBytes = 8L, segmentMaxBytes = 8L)
        )

        manager.putRawXml(1L, ByteArray(4))
        manager.putRawXml(2L, ByteArray(4))
        manager.putSegment(DanmakuSegmentCacheKey(1L, 1), ByteArray(4))
        manager.putSegment(DanmakuSegmentCacheKey(1L, 2), ByteArray(4))

        manager.trimDanmakuCache(8L)

        val stats = manager.stats()
        assertTrue(stats.totalBytes <= 8L)
        assertEquals(4L, stats.rawBytes)
        assertEquals(4L, stats.segmentBytes)
    }

    @Test
    fun `trimDanmakuCache with explicit limits drops raw xml and keeps recent segments`() {
        val manager = DanmakuCacheManager(
            DanmakuCacheProfile(rawXmlMaxBytes = 8L, segmentMaxBytes = 12L)
        )
        val firstSegment = DanmakuSegmentCacheKey(1L, 1)
        val secondSegment = DanmakuSegmentCacheKey(1L, 2)
        val thirdSegment = DanmakuSegmentCacheKey(1L, 3)

        manager.putRawXml(1L, ByteArray(4))
        manager.putRawXml(2L, ByteArray(4))
        manager.putSegment(firstSegment, ByteArray(4))
        manager.putSegment(secondSegment, ByteArray(4))
        manager.putSegment(thirdSegment, ByteArray(4))
        assertNotNull(manager.getSegment(firstSegment))

        manager.trimDanmakuCache(maxRawBytes = 0L, maxSegmentBytes = 8L)

        val stats = manager.stats()
        assertEquals(0, stats.rawEntryCount)
        assertEquals(0L, stats.rawBytes)
        assertEquals(2, stats.segmentEntryCount)
        assertEquals(8L, stats.segmentBytes)
        assertNull(manager.getRawXml(1L))
        assertNull(manager.getRawXml(2L))
        assertNotNull(manager.getSegment(firstSegment))
        assertNull(manager.getSegment(secondSegment))
        assertNotNull(manager.getSegment(thirdSegment))
    }

    @Test
    fun `clear drops all cache stats`() {
        val manager = DanmakuCacheManager(
            DanmakuCacheProfile(rawXmlMaxBytes = 8L, segmentMaxBytes = 8L)
        )
        manager.putRawXml(1L, ByteArray(4))
        manager.putSegment(DanmakuSegmentCacheKey(1L, 1), ByteArray(4))

        manager.clear()

        val stats = manager.stats()
        assertEquals(0, stats.rawEntryCount)
        assertEquals(0, stats.segmentEntryCount)
        assertEquals(0L, stats.totalBytes)
    }

    @Test
    fun `profile resolves low normal and high memory tiers`() {
        assertEquals(
            DanmakuCacheProfile.LOW_RAM,
            DanmakuCacheProfile.resolve(isLowRamDevice = true, memoryClassMb = 512)
        )
        assertEquals(
            DanmakuCacheProfile.NORMAL,
            DanmakuCacheProfile.resolve(isLowRamDevice = false, memoryClassMb = 256)
        )
        assertEquals(
            DanmakuCacheProfile.HIGH_MEMORY,
            DanmakuCacheProfile.resolve(isLowRamDevice = false, memoryClassMb = 384)
        )
    }

    @Test
    fun `oversized entries are not cached`() {
        val manager = DanmakuCacheManager(
            DanmakuCacheProfile(rawXmlMaxBytes = 4L, segmentMaxBytes = 4L)
        )

        manager.putRawXml(1L, ByteArray(8))
        manager.putSegment(DanmakuSegmentCacheKey(1L, 1), ByteArray(8))

        val stats = manager.stats()
        assertFalse(manager.getRawXml(1L) != null)
        assertEquals(0, stats.rawEntryCount)
        assertEquals(0, stats.segmentEntryCount)
    }
}
