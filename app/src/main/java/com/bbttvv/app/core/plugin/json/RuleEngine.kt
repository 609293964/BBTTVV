// 文件路径: core/plugin/json/RuleEngine.kt
package com.bbttvv.app.core.plugin.json

import androidx.compose.ui.graphics.Color
import com.bbttvv.app.core.plugin.DanmakuItem
import com.bbttvv.app.core.plugin.DanmakuStyle
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import java.util.LinkedHashMap
import java.util.WeakHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private const val TAG = "RuleEngine"
private const val RULE_REGEX_CACHE_SIZE = 96
private val conditionCacheLock = Any()
private val conditionCache = WeakHashMap<Rule, Condition?>()
private val regexCacheLock = Any()
private val regexCache = object : LinkedHashMap<String, Regex?>(RULE_REGEX_CACHE_SIZE, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex?>?): Boolean {
        return size > RULE_REGEX_CACHE_SIZE
    }
}

/**
 * JSON rule evaluator. Imported rules are validated before they reach this class, but the
 * runtime still enforces depth/regex limits as defense in depth for programmatic callers.
 */
object RuleEngine {
    fun shouldShowVideo(video: VideoItem, rules: List<Rule>): Boolean {
        for (rule in rules) {
            if (rule.action != RuleAction.HIDE) continue
            val condition = rule.cachedCondition() ?: continue
            if (evaluateCondition(condition, depth = 1) { field -> getVideoFieldValue(video, field) }) {
                Logger.d(TAG) { "🚫 隐藏视频: ${video.title} (规则匹配)" }
                return false
            }
        }
        return true
    }

    fun shouldShowDanmaku(danmaku: DanmakuItem, rules: List<Rule>): Boolean {
        for (rule in rules) {
            if (rule.action != RuleAction.HIDE) continue
            val condition = rule.cachedCondition() ?: continue
            if (evaluateCondition(condition, depth = 1) { field -> getDanmakuFieldValue(danmaku, field) }) {
                return false
            }
        }
        return true
    }

    fun getDanmakuHighlightStyle(danmaku: DanmakuItem, rules: List<Rule>): DanmakuStyle? {
        for (rule in rules) {
            if (rule.action != RuleAction.HIGHLIGHT) continue
            val condition = rule.cachedCondition() ?: continue
            if (evaluateCondition(condition, depth = 1) { field -> getDanmakuFieldValue(danmaku, field) }) {
                return rule.style?.toDanmakuStyle()
            }
        }
        return null
    }

    private fun evaluateCondition(
        condition: Condition,
        depth: Int,
        fieldValueGetter: (String) -> Any?
    ): Boolean {
        if (depth > JSON_PLUGIN_MAX_CONDITION_DEPTH) return false
        return when (condition) {
            is Condition.Simple -> {
                val fieldValue = fieldValueGetter(condition.field)
                evaluatePrimitive(fieldValue, condition.op, condition.value)
            }
            is Condition.And -> {
                if (condition.conditions.size > JSON_PLUGIN_MAX_CONDITION_CHILDREN) return false
                condition.conditions.all { child ->
                    evaluateCondition(child, depth + 1, fieldValueGetter)
                }
            }
            is Condition.Or -> {
                if (condition.conditions.size > JSON_PLUGIN_MAX_CONDITION_CHILDREN) return false
                condition.conditions.any { child ->
                    evaluateCondition(child, depth + 1, fieldValueGetter)
                }
            }
        }
    }

    private fun getVideoFieldValue(video: VideoItem, field: String): Any? {
        return when (field) {
            "title" -> video.title
            "duration" -> video.duration
            "bvid" -> video.bvid
            "owner.mid" -> video.owner.mid
            "owner.name" -> video.owner.name
            "stat.view" -> video.stat.view
            "stat.like" -> video.stat.like
            "stat.danmaku" -> video.stat.danmaku
            "stat.coin" -> video.stat.coin
            "stat.favorite" -> video.stat.favorite
            else -> null
        }
    }

    private fun getDanmakuFieldValue(danmaku: DanmakuItem, field: String): Any? {
        return when (field) {
            "content" -> danmaku.content
            "userId" -> danmaku.userId
            "type" -> danmaku.type
            else -> null
        }
    }

    private fun evaluatePrimitive(fieldValue: Any?, op: String, ruleValue: JsonElement): Boolean {
        if (fieldValue == null) return false

        return when (op) {
            RuleOperator.EQ -> compareEquals(fieldValue, ruleValue)
            RuleOperator.NE -> !compareEquals(fieldValue, ruleValue)
            RuleOperator.LT -> compareNumber(fieldValue, ruleValue) { a, b -> a < b }
            RuleOperator.LE -> compareNumber(fieldValue, ruleValue) { a, b -> a <= b }
            RuleOperator.GT -> compareNumber(fieldValue, ruleValue) { a, b -> a > b }
            RuleOperator.GE -> compareNumber(fieldValue, ruleValue) { a, b -> a >= b }
            RuleOperator.CONTAINS -> {
                val text = ruleValue.stringValueOrNull() ?: return false
                fieldValue.toString().contains(text, ignoreCase = true)
            }
            RuleOperator.STARTS_WITH -> {
                val text = ruleValue.stringValueOrNull() ?: return false
                fieldValue.toString().startsWith(text, ignoreCase = true)
            }
            RuleOperator.ENDS_WITH -> {
                val text = ruleValue.stringValueOrNull() ?: return false
                fieldValue.toString().endsWith(text, ignoreCase = true)
            }
            RuleOperator.REGEX -> {
                val pattern = ruleValue.stringValueOrNull() ?: return false
                if (validateSafeRegexPattern(pattern) != null) return false
                val boundedInput = fieldValue.toString().take(JSON_PLUGIN_MAX_REGEX_INPUT_CHARS)
                cachedRegex(pattern)?.containsMatchIn(boundedInput) == true
            }
            RuleOperator.IN -> {
                val values = ruleValue as? JsonArray ?: return false
                if (values.size > JSON_PLUGIN_MAX_ARRAY_ITEMS) return false
                values.any { compareEquals(fieldValue, it) }
            }
            else -> false
        }
    }

    private fun compareEquals(fieldValue: Any, ruleValue: JsonElement): Boolean {
        val primitive = ruleValue as? JsonPrimitive ?: return false
        return when (fieldValue) {
            is String -> fieldValue == primitive.contentOrNull
            is Int -> fieldValue == primitive.intOrNull
            is Long -> fieldValue == primitive.longOrNull
            is Double -> fieldValue == primitive.doubleOrNull
            is Float -> fieldValue.toDouble() == primitive.doubleOrNull
            is Boolean -> fieldValue == primitive.booleanOrNull
            else -> fieldValue.toString() == primitive.contentOrNull
        }
    }

    private fun compareNumber(
        fieldValue: Any,
        ruleValue: JsonElement,
        comparator: (Double, Double) -> Boolean
    ): Boolean {
        val a = when (fieldValue) {
            is Int -> fieldValue.toDouble()
            is Long -> fieldValue.toDouble()
            is Double -> fieldValue
            is Float -> fieldValue.toDouble()
            else -> return false
        }
        val b = (ruleValue as? JsonPrimitive)?.doubleOrNull ?: return false
        return comparator(a, b)
    }

    private fun Rule.cachedCondition(): Condition? {
        return synchronized(conditionCacheLock) {
            if (conditionCache.containsKey(this)) {
                conditionCache[this]
            } else {
                toCondition().also { conditionCache[this] = it }
            }
        }
    }

    private fun cachedRegex(pattern: String): Regex? {
        if (validateSafeRegexPattern(pattern) != null) return null
        return synchronized(regexCacheLock) {
            if (regexCache.containsKey(pattern)) {
                regexCache[pattern]
            } else {
                runCatching { Regex(pattern) }.getOrNull().also { regexCache[pattern] = it }
            }
        }
    }

    private fun HighlightStyle.toDanmakuStyle(): DanmakuStyle {
        val textColor = color?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (_: Exception) {
                null
            }
        }
        return DanmakuStyle(
            textColor = textColor,
            borderColor = null,
            backgroundColor = null,
            bold = bold,
            scale = scale.coerceIn(
                JSON_PLUGIN_MIN_HIGHLIGHT_SCALE,
                JSON_PLUGIN_MAX_HIGHLIGHT_SCALE
            )
        )
    }
}

private fun JsonElement.stringValueOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.takeIf { it.isString }?.content
}
