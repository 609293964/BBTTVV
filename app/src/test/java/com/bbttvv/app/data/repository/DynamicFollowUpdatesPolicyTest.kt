package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.DynamicPortalUpItem
import com.bbttvv.app.data.model.response.FollowingUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicFollowUpdatesPolicyTest {
    @Test
    fun defaultItemsKeepFixedEntriesVisibleBeforeNetworkReturns() {
        assertEquals(
            listOf("fixed:special", "fixed:default"),
            defaultDynamicFollowUpdateItems().map { it.key }
        )
    }

    @Test
    fun emptyNetworkDataStillReturnsFixedEntries() {
        val items = buildDynamicFollowUpdateItems(
            followings = emptyList(),
            portalItems = emptyList()
        )

        assertEquals(
            listOf("fixed:special", "fixed:default"),
            items.map { it.key }
        )
    }

    @Test
    fun updatedUpsUsePortalOrderThenRemainingFollowingsKeepOriginalOrder() {
        val items = buildDynamicFollowUpdateItems(
            followings = listOf(
                following(mid = 1, name = "one"),
                following(mid = 2, name = "two"),
                following(mid = 3, name = "three"),
                following(mid = 4, name = "four")
            ),
            portalItems = listOf(
                portal(mid = 3, name = "three portal", hasUpdate = true),
                portal(mid = 2, name = "two portal", hasUpdate = true),
                portal(mid = 4, name = "four portal", hasUpdate = false)
            )
        )

        assertEquals(
            listOf("fixed:special", "fixed:default", "up:3", "up:2", "up:1", "up:4"),
            items.map { it.key }
        )

        val upItems = items.filterIsInstance<DynamicFollowUpdateUpItem>()
        assertEquals(listOf(3L, 2L, 1L, 4L), upItems.map { it.mid })
        assertEquals(listOf(true, true, false, false), upItems.map { it.hasUpdate })
        assertEquals("three portal", upItems[0].name)
        assertEquals("one", upItems[2].name)
    }

    @Test
    fun optimisticConsumeClearsOnlyTargetUpRedPoint() {
        val items = listOf<DynamicFollowUpdateItem>(
            DynamicFollowUpdateFixedItem(DynamicFollowUpdateFixedKind.SPECIAL),
            DynamicFollowUpdateUpItem(mid = 1, name = "one", face = "", hasUpdate = true),
            DynamicFollowUpdateUpItem(mid = 2, name = "two", face = "", hasUpdate = true)
        )

        val updated = clearDynamicFollowUpdatePrompt(items, mid = 1)
        val upItems = updated.filterIsInstance<DynamicFollowUpdateUpItem>()

        assertFalse(upItems[0].hasUpdate)
        assertTrue(upItems[1].hasUpdate)
    }

    @Test
    fun optimisticConsumeKeepsSameListWhenTargetHasNoRedPoint() {
        val items = listOf<DynamicFollowUpdateItem>(
            DynamicFollowUpdateFixedItem(DynamicFollowUpdateFixedKind.SPECIAL),
            DynamicFollowUpdateUpItem(mid = 1, name = "one", face = "", hasUpdate = false)
        )

        assertSame(items, clearDynamicFollowUpdatePrompt(items, mid = 1))
    }

    @Test
    fun upItemsAreLimitedToFifteenPeople() {
        val followings = (1L..20L).map { mid ->
            following(mid = mid, name = "user-$mid")
        }
        val portalItems = (20L downTo 1L).map { mid ->
            portal(mid = mid, name = "portal-$mid", hasUpdate = true)
        }

        val upItems = buildDynamicFollowUpdateItems(
            followings = followings,
            portalItems = portalItems
        ).filterIsInstance<DynamicFollowUpdateUpItem>()

        assertEquals(15, upItems.size)
        assertEquals((20L downTo 6L).toList(), upItems.map { it.mid })
    }

    private fun following(mid: Long, name: String): FollowingUser {
        return FollowingUser(
            mid = mid,
            uname = name,
            face = "face-$mid"
        )
    }

    private fun portal(mid: Long, name: String, hasUpdate: Boolean): DynamicPortalUpItem {
        return DynamicPortalUpItem(
            mid = mid,
            name = name,
            face = "portal-face-$mid",
            has_update = hasUpdate
        )
    }
}
