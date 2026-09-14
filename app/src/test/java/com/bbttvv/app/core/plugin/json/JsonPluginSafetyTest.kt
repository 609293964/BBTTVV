package com.bbttvv.app.core.plugin.json

import java.io.IOException
import java.nio.file.Files
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPluginSafetyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `oversized and deeply nested documents are rejected before decoding`() {
        val oversized = "x".repeat(JSON_PLUGIN_MAX_BYTES + 1)
        assertTrue(validateJsonPluginDocument(oversized)?.contains("过大") == true)

        val deep = buildString {
            repeat(JSON_PLUGIN_MAX_JSON_DEPTH + 1) { append('[') }
            append('0')
            repeat(JSON_PLUGIN_MAX_JSON_DEPTH + 1) { append(']') }
        }
        assertTrue(validateJsonPluginDocument(deep)?.contains("嵌套过深") == true)
    }

    @Test
    fun `condition serializer rejects depth before runtime`() {
        var condition = """{"field":"title","op":"eq","value":"x"}"""
        repeat(JSON_PLUGIN_MAX_CONDITION_DEPTH) {
            condition = """{"and":[$condition]}"""
        }

        assertThrows(Exception::class.java) {
            json.decodeFromString<Condition>(condition)
        }
    }

    @Test
    fun `plugin validation rejects too many rules and bad operator values`() {
        val validRule = Rule(
            field = "title",
            op = RuleOperator.CONTAINS,
            value = JsonPrimitive("keyword"),
            action = RuleAction.HIDE
        )
        val tooMany = plugin(rules = List(JSON_PLUGIN_MAX_RULES + 1) { validRule })
        assertTrue(validateJsonRulePlugin(tooMany)?.contains("规则数量") == true)

        val badType = plugin(
            rules = listOf(
                Rule(
                    field = "duration",
                    op = RuleOperator.GT,
                    value = JsonPrimitive("not-a-number"),
                    action = RuleAction.HIDE
                )
            )
        )
        assertTrue(validateJsonRulePlugin(badType)?.contains("数值") == true)
    }

    @Test
    fun `regex policy rejects nested quantifiers and accepts bounded simple patterns`() {
        assertTrue(validateSafeRegexPattern("(a+)+$") != null)
        assertTrue(validateSafeRegexPattern("(?=a)a") != null)
        assertTrue(validateSafeRegexPattern("(a|aa)+$") != null)
        assertNull(validateSafeRegexPattern("^foo-[0-9]+$"))
    }

    @Test
    fun `highlight style bounds are validated`() {
        val invalid = JsonRulePlugin(
            id = "style-test",
            name = "style",
            type = "danmaku",
            rules = listOf(
                Rule(
                    field = "content",
                    op = RuleOperator.CONTAINS,
                    value = JsonPrimitive("x"),
                    action = RuleAction.HIGHLIGHT,
                    style = HighlightStyle(color = "#FFFFFF", scale = 10f)
                )
            )
        )
        assertTrue(validateJsonRulePlugin(invalid)?.contains("scale") == true)
    }

    @Test
    fun `atomic save keeps previous target when move fails`() {
        val dir = Files.createTempDirectory("json-plugin-storage").toFile()
        val target = dir.resolve("plugin.json")
        try {
            JsonPluginStorage.writeAtomically(target, "first")
            assertEquals("first", target.readText())

            assertThrows(IOException::class.java) {
                JsonPluginStorage.writeAtomically(
                    target = target,
                    content = "second",
                    move = { _, _ -> throw IOException("forced move failure") }
                )
            }
            assertEquals("first", target.readText())
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `failed delete leaves plugin file intact`() {
        val dir = Files.createTempDirectory("json-plugin-delete").toFile()
        val target = dir.resolve("plugin.json").apply { writeText("payload") }
        try {
            val deleted = JsonPluginStorage.deleteConsistently(target) { false }
            assertFalse(deleted)
            assertTrue(target.exists())
            assertEquals("payload", target.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun plugin(rules: List<Rule>): JsonRulePlugin {
        return JsonRulePlugin(
            id = "test-plugin",
            name = "test",
            type = "feed",
            rules = rules
        )
    }
}
