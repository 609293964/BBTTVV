package com.android.purebilibili.feature.article

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface ArticleContentBlock {
    data class Heading(val text: String) : ArticleContentBlock
    data class Paragraph(val text: String) : ArticleContentBlock
    data class Image(
        val url: String,
        val width: Int = 0,
        val height: Int = 0
    ) : ArticleContentBlock
}

internal fun parseArticleContentBlocks(
    structuredParagraphs: List<JsonObject>,
    htmlContent: String?
): List<ArticleContentBlock> {
    val structuredBlocks = structuredParagraphs.flatMap(::parseStructuredParagraph)
    if (structuredBlocks.isNotEmpty()) return structuredBlocks
    return parseHtmlBlocks(htmlContent)
}

private fun parseStructuredParagraph(paragraph: JsonObject): List<ArticleContentBlock> {
    val blocks = mutableListOf<ArticleContentBlock>()

    extractInlineText(paragraph["heading"]).takeIf { it.isNotBlank() }?.let {
        blocks += ArticleContentBlock.Heading(it)
    }
    extractInlineText(paragraph["text"]).takeIf { it.isNotBlank() }?.let {
        blocks += ArticleContentBlock.Paragraph(it)
    }
    blocks += extractImages(paragraph)

    return blocks
}

private fun extractInlineText(element: JsonElement?): String {
    val nodes = runCatching { element?.jsonObject?.get("nodes")?.jsonArray }.getOrNull() ?: return ""
    return buildString {
        nodes.forEach { node ->
            val nodeObject = runCatching { node.jsonObject }.getOrNull() ?: return@forEach
            val word = nodeObject["word"]
                ?.jsonObject
                ?.get("words")
                ?.jsonPrimitive
                ?.contentOrNull
            val richText = nodeObject["rich"]
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: nodeObject["rich"]
                    ?.jsonObject
                    ?.get("orig_text")
                    ?.jsonPrimitive
                    ?.contentOrNull
            append(word ?: richText.orEmpty())
        }
    }.trim()
}

private fun extractImages(paragraph: JsonObject): List<ArticleContentBlock.Image> {
    val results = mutableListOf<ArticleContentBlock.Image>()
    val picObject = paragraph["pic"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val pics = picObject?.get("pics")?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
    pics.forEach { pic ->
        parseImageObject(runCatching { pic.jsonObject }.getOrNull())?.let(results::add)
    }
    if (results.isNotEmpty()) return results

    parseImageObject(picObject)?.let(results::add)
    return results
}

private fun parseImageObject(image: JsonObject?): ArticleContentBlock.Image? {
    if (image == null) return null
    val rawUrl = image["url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    if (rawUrl.isBlank()) return null
    return ArticleContentBlock.Image(
        url = normalizeImageUrl(rawUrl),
        width = image["width"]?.jsonPrimitive?.intOrNull ?: 0,
        height = image["height"]?.jsonPrimitive?.intOrNull ?: 0
    )
}

private fun parseHtmlBlocks(htmlContent: String?): List<ArticleContentBlock> {
    if (htmlContent.isNullOrBlank()) return emptyList()

    val blocks = mutableListOf<ArticleContentBlock>()
    val blockRegex = Regex("""(?is)<(h[1-6]|p|figure)\b[^>]*>(.*?)</\1>|<img\b[^>]*>""")
    blockRegex.findAll(htmlContent).forEach { match ->
        val tag = match.groupValues.getOrNull(1).orEmpty().lowercase()
        val content = if (tag.isBlank()) match.value else match.groupValues[2]
        when {
            tag.startsWith("h") -> cleanupHtmlText(content).takeIf { it.isNotBlank() }?.let {
                blocks += ArticleContentBlock.Heading(it)
            }

            tag == "p" -> cleanupHtmlText(content).takeIf { it.isNotBlank() }?.let {
                blocks += ArticleContentBlock.Paragraph(it)
            }

            tag == "figure" || match.value.startsWith("<img", ignoreCase = true) -> {
                parseHtmlImage(match.value)?.let { blocks += it }
            }
        }
    }
    return blocks
}

private fun parseHtmlImage(rawBlock: String): ArticleContentBlock.Image? {
    val imgTag = Regex("""(?is)<img\b[^>]*>""").find(rawBlock)?.value ?: rawBlock
    val url = extractHtmlAttribute(imgTag, "data-src")
        ?: extractHtmlAttribute(imgTag, "src")
        ?: return null
    return ArticleContentBlock.Image(
        url = normalizeImageUrl(url),
        width = extractHtmlAttribute(imgTag, "width")?.toIntOrNull() ?: 0,
        height = extractHtmlAttribute(imgTag, "height")?.toIntOrNull() ?: 0
    )
}

private fun extractHtmlAttribute(tag: String, name: String): String? {
    val regex = Regex("""(?is)\b$name\s*=\s*["']([^"']+)["']""")
    return regex.find(tag)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun cleanupHtmlText(raw: String): String {
    return raw
        .replace(Regex("""(?is)<br\s*/?>"""), "\n")
        .replace(Regex("""(?is)<[^>]+>"""), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}

private fun normalizeImageUrl(rawUrl: String): String {
    return when {
        rawUrl.startsWith("//") -> "https:$rawUrl"
        rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
        else -> rawUrl
    }
}
