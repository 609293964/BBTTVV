package com.bbttvv.app.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCapabilityManifestPolicyTest {
    @Test
    fun `recommendation access requires candidates capability`() {
        val grants = PluginCapabilityGrants(
            granted = setOf(PluginCapability.LOCAL_HISTORY_READ, PluginCapability.LOCAL_FEEDBACK_READ)
        )

        val decision = validateRecommendationPluginAccess(grants)

        assertTrue(decision is RecommendationPluginAccessDecision.MissingCapabilities)
        assertEquals(
            setOf(PluginCapability.RECOMMENDATION_CANDIDATES),
            (decision as RecommendationPluginAccessDecision.MissingCapabilities).missing
        )
    }

    @Test
    fun `capability grants only include user approved capabilities`() {
        val manifest = PluginCapabilityManifest(
            pluginId = "today_watch",
            displayName = "今日推荐单",
            version = "1.0.0",
            apiVersion = 1,
            entryClassName = "Example",
            capabilities = setOf(
                PluginCapability.RECOMMENDATION_CANDIDATES,
                PluginCapability.LOCAL_HISTORY_READ
            )
        )

        val grants = resolvePluginCapabilityGrants(
            manifest = manifest,
            userApprovedCapabilities = setOf(PluginCapability.RECOMMENDATION_CANDIDATES)
        )

        assertTrue(grants.isGranted(PluginCapability.RECOMMENDATION_CANDIDATES))
        assertEquals(setOf(PluginCapability.RECOMMENDATION_CANDIDATES), grants.granted)
    }
}
