package com.bbttvv.app.feature.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileHorizontalListEdgePolicyTest {
    @Test
    fun `only first item exits left to sidebar`() {
        assertTrue(ProfileHorizontalListEdgePolicy.isLeftEdge(index = 0))
        assertFalse(ProfileHorizontalListEdgePolicy.isLeftEdge(index = 1))
        assertFalse(ProfileHorizontalListEdgePolicy.isLeftEdge(index = 4))
        assertFalse(ProfileHorizontalListEdgePolicy.isLeftEdge(index = -1))
    }
}
