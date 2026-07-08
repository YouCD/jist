package dev.rcht.jist.util

fun displayContent(content: String): String {
    if (!content.startsWith("<?xml") && !content.startsWith("<msg")) return content
    val title = extractTag(content, "title")
    val des = extractTag(content, "des")
    return when {
        title != null && des != null -> "[卡片] $title - $des"
        title != null -> "[卡片] $title"
        else -> "[分享]"
    }
}

private fun extractTag(text: String, tag: String): String? {
    val open = "<$tag>"
    val close = "</$tag>"
    val start = text.indexOf(open)
    if (start < 0) return null
    val cs = start + open.length
    val end = text.indexOf(close, cs)
    if (end < 0) return null
    val v = text.substring(cs, end).trim()
    return v.ifBlank { null }
}
