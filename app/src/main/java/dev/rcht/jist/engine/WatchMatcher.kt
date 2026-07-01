package dev.rcht.jist.engine

import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.WatchTopicEntity

class WatchMatcher {

    fun matches(
        notification: NotificationEntity,
        topic: WatchTopicEntity
    ): String? {
        val text = "${notification.title} ${notification.content}"
        val keywordList = parseKeywords(topic.keywords)

        for (keyword in keywordList) {
            if (text.contains(keyword, ignoreCase = true)) {
                return keyword
            }
        }
        return null
    }

    fun parseKeywords(json: String): List<String> {
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                kotlinx.serialization.json.Json
                    .decodeFromString<List<String>>(trimmed)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
