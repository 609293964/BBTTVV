package com.bbttvv.app.core.plugin

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PluginRegistryTest {
    private class TestPlugin(override val id: String) : Plugin {
        override val name = id
        override val description = "test"
        override val version = "1"
        var active = false
        var failEnable = false
        var enables = 0
        override suspend fun onEnable() { enables++; active = true; if (failEnable) error("fixture failure") }
        override suspend fun onDisable() { active = false }
    }

    @Test fun `startup failure cleans up and does not block other plugins`() = runBlocking {
        val persisted = mutableMapOf<String, Boolean>()
        val registry = PluginRegistry({ true }, { id, enabled -> persisted[id] = enabled }, { _, _ -> }, { _, _ -> })
        val broken = TestPlugin("broken").apply { failEnable = true }
        val healthy = TestPlugin("healthy")
        registry.register(broken)
        registry.register(healthy)
        registry.awaitPluginReady("broken")
        assertFalse(broken.active)
        assertEquals(false, persisted["broken"])
        assertEquals(listOf(false, true), registry.pluginsFlow.value.map { it.enabled })
    }

    @Test fun `store failure restores the previous lifecycle and published state`() = runBlocking {
        val registry = PluginRegistry({ false }, { _, _ -> error("fixture write failure") }, { _, _ -> }, { _, _ -> })
        val plugin = TestPlugin("plugin")
        registry.register(plugin)
        registry.setEnabled(plugin.id, true)
        assertFalse(plugin.active)
        assertFalse(registry.pluginsFlow.value.single().enabled)
    }

    @Test fun `pre-registration choice and duplicate registration are serialized`() = runBlocking {
        val persisted = mutableMapOf<String, Boolean>()
        val registry = PluginRegistry({ persisted[it] ?: true }, { id, enabled -> persisted[id] = enabled }, { _, _ -> }, { _, _ -> })
        val plugin = TestPlugin("plugin")
        registry.setEnabled(plugin.id, false)
        coroutineScope { repeat(8) { launch { registry.register(plugin) } } }
        assertEquals(1, registry.pluginsFlow.value.size)
        assertEquals(0, plugin.enables)
        assertFalse(registry.pluginsFlow.value.single().enabled)
    }

    @Test fun `cancellation during enabling releases partially initialized resources`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var active = false
        val plugin = object : Plugin {
            override val id = "cancelled"
            override val name = id
            override val description = "test"
            override val version = "1"
            override suspend fun onEnable() { active = true; entered.complete(Unit); awaitCancellation() }
            override suspend fun onDisable() { active = false }
        }
        val registry = PluginRegistry({ true }, { _, _ -> }, { _, _ -> }, { _, _ -> })
        val job = launch { registry.register(plugin) }
        entered.await()
        job.cancelAndJoin()
        assertFalse(active)
        assertFalse(registry.pluginsFlow.value.single().enabled)
    }

    @Test fun `cancellation after a store commit restores both disk and lifecycle`() = runBlocking {
        var persisted = false
        val committed = CompletableDeferred<Unit>()
        val plugin = TestPlugin("plugin")
        val registry = PluginRegistry({ false }, { _, enabled ->
            persisted = enabled
            if (enabled) { committed.complete(Unit); awaitCancellation() }
        }, { _, _ -> }, { _, _ -> })
        registry.register(plugin)
        val job = launch { registry.setEnabled(plugin.id, true) }
        committed.await()
        job.cancelAndJoin()
        assertFalse(persisted)
        assertFalse(plugin.active)
        assertFalse(registry.pluginsFlow.value.single().enabled)
    }
}
