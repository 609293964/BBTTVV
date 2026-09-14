// 文件路径: core/plugin/json/JsonRulePlugin.kt
package com.bbttvv.app.core.plugin.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * JSON 规则插件数据模型
 */
@Serializable
data class JsonRulePlugin(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val author: String = "Unknown",
    val type: String,
    val iconUrl: String? = null,
    val rules: List<Rule>
)

@Serializable(with = ConditionSerializer::class)
sealed class Condition {
    data class Simple(
        val field: String,
        val op: String,
        val value: JsonElement
    ) : Condition()

    data class And(
        val conditions: List<Condition>
    ) : Condition()

    data class Or(
        val conditions: List<Condition>
    ) : Condition()
}

/**
 * Condition 自定义序列化器。
 *
 * 反序列化时直接执行深度、节点数和单层 fan-out 限制，避免恶意 JSON 在进入
 * JsonPluginManager.validatePlugin() 之前就通过递归解析耗尽栈空间。
 */
object ConditionSerializer : KSerializer<Condition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Condition")

    override fun deserialize(decoder: Decoder): Condition {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("只支持 JSON 解码")
        val jsonElement = jsonDecoder.decodeJsonElement()
        val remainingNodes = intArrayOf(JSON_PLUGIN_MAX_CONDITION_NODES)
        return parseCondition(jsonElement, depth = 1, remainingNodes = remainingNodes)
    }

    override fun serialize(encoder: Encoder, value: Condition) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("只支持 JSON 编码")
        val remainingNodes = intArrayOf(JSON_PLUGIN_MAX_CONDITION_NODES)
        jsonEncoder.encodeJsonElement(
            encodeConditionToJson(
                condition = value,
                depth = 1,
                remainingNodes = remainingNodes
            )
        )
    }

    private fun parseCondition(
        element: JsonElement,
        depth: Int,
        remainingNodes: IntArray
    ): Condition {
        require(depth <= JSON_PLUGIN_MAX_CONDITION_DEPTH) {
            "条件嵌套超过 $JSON_PLUGIN_MAX_CONDITION_DEPTH 层"
        }
        require(remainingNodes[0] > 0) {
            "条件节点超过上限 $JSON_PLUGIN_MAX_CONDITION_NODES"
        }
        remainingNodes[0] -= 1

        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("条件必须是 JSON 对象")

        return when {
            "and" in obj -> {
                val children = obj["and"] as? JsonArray
                    ?: throw IllegalArgumentException("and 必须是数组")
                require(children.isNotEmpty()) { "and 条件不能为空" }
                require(children.size <= JSON_PLUGIN_MAX_CONDITION_CHILDREN) {
                    "and 子条件超过上限 $JSON_PLUGIN_MAX_CONDITION_CHILDREN"
                }
                Condition.And(
                    children.map { child ->
                        parseCondition(child, depth + 1, remainingNodes)
                    }
                )
            }
            "or" in obj -> {
                val children = obj["or"] as? JsonArray
                    ?: throw IllegalArgumentException("or 必须是数组")
                require(children.isNotEmpty()) { "or 条件不能为空" }
                require(children.size <= JSON_PLUGIN_MAX_CONDITION_CHILDREN) {
                    "or 子条件超过上限 $JSON_PLUGIN_MAX_CONDITION_CHILDREN"
                }
                Condition.Or(
                    children.map { child ->
                        parseCondition(child, depth + 1, remainingNodes)
                    }
                )
            }
            "field" in obj -> {
                val fieldPrimitive = obj["field"] as? JsonPrimitive
                    ?: throw IllegalArgumentException("field 必须是字符串")
                val opPrimitive = obj["op"] as? JsonPrimitive
                    ?: throw IllegalArgumentException("op 必须是字符串")
                if (!fieldPrimitive.isString) throw IllegalArgumentException("field 必须是字符串")
                if (!opPrimitive.isString) throw IllegalArgumentException("op 必须是字符串")
                val value = obj["value"]
                    ?: throw IllegalArgumentException("value 不能为空")
                Condition.Simple(
                    field = fieldPrimitive.content,
                    op = opPrimitive.content,
                    value = value
                )
            }
            else -> throw IllegalArgumentException("无法识别的条件格式")
        }
    }

    private fun encodeConditionToJson(
        condition: Condition,
        depth: Int,
        remainingNodes: IntArray
    ): JsonElement {
        require(depth <= JSON_PLUGIN_MAX_CONDITION_DEPTH) {
            "条件嵌套超过 $JSON_PLUGIN_MAX_CONDITION_DEPTH 层"
        }
        require(remainingNodes[0] > 0) {
            "条件节点超过上限 $JSON_PLUGIN_MAX_CONDITION_NODES"
        }
        remainingNodes[0] -= 1

        return when (condition) {
            is Condition.Simple -> buildJsonObject {
                put("field", condition.field)
                put("op", condition.op)
                put("value", condition.value)
            }
            is Condition.And -> {
                require(condition.conditions.isNotEmpty()) { "and 条件不能为空" }
                require(condition.conditions.size <= JSON_PLUGIN_MAX_CONDITION_CHILDREN) {
                    "and 子条件超过上限 $JSON_PLUGIN_MAX_CONDITION_CHILDREN"
                }
                buildJsonObject {
                    putJsonArray("and") {
                        condition.conditions.forEach { child ->
                            add(encodeConditionToJson(child, depth + 1, remainingNodes))
                        }
                    }
                }
            }
            is Condition.Or -> {
                require(condition.conditions.isNotEmpty()) { "or 条件不能为空" }
                require(condition.conditions.size <= JSON_PLUGIN_MAX_CONDITION_CHILDREN) {
                    "or 子条件超过上限 $JSON_PLUGIN_MAX_CONDITION_CHILDREN"
                }
                buildJsonObject {
                    putJsonArray("or") {
                        condition.conditions.forEach { child ->
                            add(encodeConditionToJson(child, depth + 1, remainingNodes))
                        }
                    }
                }
            }
        }
    }
}

@Serializable
data class Rule(
    val field: String? = null,
    val op: String? = null,
    val value: JsonElement? = null,
    val condition: Condition? = null,
    val action: String,
    val style: HighlightStyle? = null
) {
    fun toCondition(): Condition? {
        if (condition != null) return condition
        if (field != null && op != null && value != null) {
            return Condition.Simple(field, op, value)
        }
        return null
    }
}

@Serializable
data class HighlightStyle(
    val color: String? = null,
    val bold: Boolean = false,
    val scale: Float = 1.0f
)

object RuleOperator {
    const val EQ = "eq"
    const val NE = "ne"
    const val LT = "lt"
    const val LE = "le"
    const val GT = "gt"
    const val GE = "ge"
    const val CONTAINS = "contains"
    const val STARTS_WITH = "startsWith"
    const val ENDS_WITH = "endsWith"
    const val REGEX = "regex"
    const val IN = "in"
}

object RuleAction {
    const val HIDE = "hide"
    const val HIGHLIGHT = "highlight"
}
