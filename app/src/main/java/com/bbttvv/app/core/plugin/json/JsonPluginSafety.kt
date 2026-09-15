package com.bbttvv.app.core.plugin.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

internal const val JSON_PLUGIN_MAX_BYTES = 256 * 1024
internal const val JSON_PLUGIN_MAX_RULES = 128
internal const val JSON_PLUGIN_MAX_CONDITION_DEPTH = 8
internal const val JSON_PLUGIN_MAX_CONDITION_CHILDREN = 32
internal const val JSON_PLUGIN_MAX_CONDITION_NODES = 256
internal const val JSON_PLUGIN_MAX_JSON_DEPTH = 24
internal const val JSON_PLUGIN_MAX_JSON_STRING_CHARS = 4096
internal const val JSON_PLUGIN_MAX_VALUE_STRING_CHARS = 512
internal const val JSON_PLUGIN_MAX_ARRAY_ITEMS = 64
internal const val JSON_PLUGIN_MAX_REGEX_CHARS = 96
internal const val JSON_PLUGIN_MAX_REGEX_INPUT_CHARS = 256
internal const val JSON_PLUGIN_MAX_METADATA_CHARS = 1024
internal const val JSON_PLUGIN_MIN_HIGHLIGHT_SCALE = 0.5f
internal const val JSON_PLUGIN_MAX_HIGHLIGHT_SCALE = 2.0f

private val COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
private val BACK_REFERENCE_PATTERN = Regex("\\\\[1-9]")
private val QUANTIFIED_GROUP_PATTERN = Regex("\\([^)]*\\)[*+{]")
private val NESTED_QUANTIFIER_PATTERN = Regex("[*+}][?+*{]")
private val REPEATED_WILDCARD_PATTERN = Regex("\\.([*+]).*\\.\\1")

private val feedFields = setOf(
    "title",
    "duration",
    "bvid",
    "owner.mid",
    "owner.name",
    "stat.view",
    "stat.like",
    "stat.danmaku",
    "stat.coin",
    "stat.favorite"
)
private val danmakuFields = setOf("content", "userId", "type")
private val stringFields = setOf("title", "bvid", "owner.name", "content", "userId", "type")
private val numericFields = setOf(
    "duration",
    "owner.mid",
    "stat.view",
    "stat.like",
    "stat.danmaku",
    "stat.coin",
    "stat.favorite"
)

/**
 * Performs a cheap structural scan before kotlinx.serialization sees the document.
 * This prevents a tiny-but-extremely-deep JSON document from overflowing parser stacks.
 */
internal fun validateJsonPluginDocument(content: String): String? {
    if (content.toByteArray(Charsets.UTF_8).size > JSON_PLUGIN_MAX_BYTES) {
        return "插件文件过大，最大 ${JSON_PLUGIN_MAX_BYTES / 1024}KB"
    }

    var depth = 0
    var inString = false
    var escaped = false
    var stringChars = 0

    content.forEach { ch ->
        if (inString) {
            if (escaped) {
                escaped = false
                return@forEach
            }
            when (ch) {
                '\\' -> escaped = true
                '"' -> {
                    inString = false
                    stringChars = 0
                }
                else -> {
                    stringChars += 1
                    if (stringChars > JSON_PLUGIN_MAX_JSON_STRING_CHARS) {
                        return "JSON 字符串过长"
                    }
                }
            }
            return@forEach
        }

        when (ch) {
            '"' -> {
                inString = true
                stringChars = 0
            }
            '{', '[' -> {
                depth += 1
                if (depth > JSON_PLUGIN_MAX_JSON_DEPTH) {
                    return "JSON 嵌套过深，最大 $JSON_PLUGIN_MAX_JSON_DEPTH 层"
                }
            }
            '}', ']' -> {
                depth -= 1
                if (depth < 0) return "JSON 结构不完整"
            }
        }
    }

    if (inString || depth != 0) return "JSON 结构不完整"
    return null
}

internal fun validateJsonRulePlugin(plugin: JsonRulePlugin): String? {
    if (plugin.id.isBlank()) return "插件 ID 不能为空"
    if (!Regex("^[a-zA-Z0-9_.-]{1,64}$").matches(plugin.id)) {
        return "插件 ID 格式无效，仅支持字母数字/._-"
    }
    if (plugin.name.isBlank()) return "插件名称不能为空"
    if (plugin.name.length > 128) return "插件名称过长"
    if (plugin.description.length > JSON_PLUGIN_MAX_METADATA_CHARS) return "插件描述过长"
    if (plugin.version.length > 64) return "插件版本字段过长"
    if (plugin.author.length > 128) return "插件作者字段过长"
    if ((plugin.iconUrl?.length ?: 0) > 2048) return "插件图标链接过长"
    if (plugin.type !in setOf("feed", "danmaku")) return "不支持的插件类型: ${plugin.type}"
    if (plugin.rules.isEmpty()) return "规则不能为空"
    if (plugin.rules.size > JSON_PLUGIN_MAX_RULES) {
        return "规则数量超过上限 $JSON_PLUGIN_MAX_RULES"
    }

    var nodeCount = 0
    plugin.rules.forEachIndexed { index, rule ->
        if (rule.action !in setOf(RuleAction.HIDE, RuleAction.HIGHLIGHT)) {
            return "规则 ${index + 1} 的 action 无效: ${rule.action}"
        }
        if (plugin.type == "feed" && rule.action == RuleAction.HIGHLIGHT) {
            return "feed 插件不支持 highlight 动作"
        }
        if (rule.action == RuleAction.HIGHLIGHT) {
            val style = rule.style ?: return "highlight 规则必须提供 style"
            if (!style.scale.isFinite() ||
                style.scale !in JSON_PLUGIN_MIN_HIGHLIGHT_SCALE..JSON_PLUGIN_MAX_HIGHLIGHT_SCALE
            ) {
                return "highlight scale 超出范围"
            }
            if (style.color != null && !COLOR_PATTERN.matches(style.color)) {
                return "highlight color 必须是 #RRGGBB 或 #RRGGBBAA"
            }
        }

        val condition = rule.toCondition()
            ?: return "规则 ${index + 1} 缺少 condition 或 field/op/value"
        validateCondition(
            condition = condition,
            pluginType = plugin.type,
            depth = 1,
            onNode = {
                nodeCount += 1
                nodeCount <= JSON_PLUGIN_MAX_CONDITION_NODES
            }
        )?.let { return "规则 ${index + 1}: $it" }
    }

    return null
}

private fun validateCondition(
    condition: Condition,
    pluginType: String,
    depth: Int,
    onNode: () -> Boolean
): String? {
    if (depth > JSON_PLUGIN_MAX_CONDITION_DEPTH) {
        return "条件嵌套超过 $JSON_PLUGIN_MAX_CONDITION_DEPTH 层"
    }
    if (!onNode()) return "条件节点超过上限 $JSON_PLUGIN_MAX_CONDITION_NODES"

    return when (condition) {
        is Condition.Simple -> validateSimpleCondition(condition, pluginType)
        is Condition.And -> {
            if (condition.conditions.isEmpty()) return "and 条件不能为空"
            if (condition.conditions.size > JSON_PLUGIN_MAX_CONDITION_CHILDREN) {
                return "and 子条件超过上限 $JSON_PLUGIN_MAX_CONDITION_CHILDREN"
            }
            condition.conditions.forEach { child ->
                validateCondition(child, pluginType, depth + 1, onNode)?.let { return it }
            }
            null
        }
        is Condition.Or -> {
            if (condition.conditions.isEmpty()) return "or 条件不能为空"
            if (condition.conditions.size > JSON_PLUGIN_MAX_CONDITION_CHILDREN) {
                return "or 子条件超过上限 $JSON_PLUGIN_MAX_CONDITION_CHILDREN"
            }
            condition.conditions.forEach { child ->
                validateCondition(child, pluginType, depth + 1, onNode)?.let { return it }
            }
            null
        }
    }
}

private fun validateSimpleCondition(condition: Condition.Simple, pluginType: String): String? {
    val allowedFields = if (pluginType == "feed") feedFields else danmakuFields
    if (condition.field !in allowedFields) return "不支持的字段: ${condition.field}"

    val op = condition.op
    val supportedOps = setOf(
        RuleOperator.EQ,
        RuleOperator.NE,
        RuleOperator.LT,
        RuleOperator.LE,
        RuleOperator.GT,
        RuleOperator.GE,
        RuleOperator.CONTAINS,
        RuleOperator.STARTS_WITH,
        RuleOperator.ENDS_WITH,
        RuleOperator.REGEX,
        RuleOperator.IN
    )
    if (op !in supportedOps) return "不支持的操作符: $op"

    val value = condition.value
    when (op) {
        RuleOperator.LT, RuleOperator.LE, RuleOperator.GT, RuleOperator.GE -> {
            if (condition.field !in numericFields) return "字段 ${condition.field} 不支持数值比较"
            if (value !is JsonPrimitive || value.doubleOrNull == null) return "$op 需要数值 value"
        }
        RuleOperator.CONTAINS, RuleOperator.STARTS_WITH, RuleOperator.ENDS_WITH -> {
            if (condition.field !in stringFields) return "字段 ${condition.field} 不支持字符串操作"
            val text = value.stringValueOrNull() ?: return "$op 需要字符串 value"
            if (text.length > JSON_PLUGIN_MAX_VALUE_STRING_CHARS) return "规则字符串过长"
        }
        RuleOperator.REGEX -> {
            if (condition.field !in stringFields) return "字段 ${condition.field} 不支持 regex"
            val pattern = value.stringValueOrNull() ?: return "regex 需要字符串 value"
            validateSafeRegexPattern(pattern)?.let { return it }
        }
        RuleOperator.IN -> {
            val array = value as? JsonArray ?: return "in 需要数组 value"
            if (array.isEmpty()) return "in 数组不能为空"
            if (array.size > JSON_PLUGIN_MAX_ARRAY_ITEMS) return "in 数组元素过多"
            if (array.any { it !is JsonPrimitive || it.toString().length > JSON_PLUGIN_MAX_VALUE_STRING_CHARS }) {
                return "in 数组只允许短的基础值"
            }
        }
        RuleOperator.EQ, RuleOperator.NE -> {
            if (value !is JsonPrimitive) return "$op 只支持基础值"
            if (value.isString && value.content.length > JSON_PLUGIN_MAX_VALUE_STRING_CHARS) {
                return "规则字符串过长"
            }
            if (!value.isString && value.doubleOrNull == null && value.booleanOrNull == null) {
                return "$op value 类型无效"
            }
        }
    }
    return null
}

internal fun validateSafeRegexPattern(pattern: String): String? {
    if (pattern.isBlank()) return "regex 不能为空"
    if (pattern.length > JSON_PLUGIN_MAX_REGEX_CHARS) {
        return "regex 过长，最大 $JSON_PLUGIN_MAX_REGEX_CHARS 字符"
    }
    if (pattern.contains("(?")) return "regex 不允许 lookaround/内联扩展"
    if (BACK_REFERENCE_PATTERN.containsMatchIn(pattern)) return "regex 不允许反向引用"
    if (QUANTIFIED_GROUP_PATTERN.containsMatchIn(pattern)) return "regex 不允许对分组重复量词"
    if (NESTED_QUANTIFIER_PATTERN.containsMatchIn(pattern)) return "regex 包含危险的嵌套量词"
    if (REPEATED_WILDCARD_PATTERN.containsMatchIn(pattern)) return "regex 包含高开销的重复通配"
    if (pattern.count { it == '|' } > 8) return "regex 分支过多"
    return runCatching { Regex(pattern) }
        .fold(onSuccess = { null }, onFailure = { "regex 语法无效" })
}

private fun JsonElement.stringValueOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.takeIf { it.isString }?.content
}
