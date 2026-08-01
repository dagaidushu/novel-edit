package com.mozhou.novelcraft.core

import org.json.JSONArray
import org.json.JSONObject

data class ExtractedStoryItem(
    val kind: String,
    val name: String,
    val detail: String,
    val status: String,
)

data class ExtractedStoryEdge(
    val sourceName: String,
    val targetName: String,
    val relation: String,
    val description: String,
)

data class MemoryExtraction(
    val items: List<ExtractedStoryItem>,
    val edges: List<ExtractedStoryEdge>,
)

object MemoryExtractionParser {
    fun parse(source: String): MemoryExtraction {
        val root = JSONObject(source.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
        val items = root.optJSONArray("items").toItems()
        val edges = root.optJSONArray("edges").toEdges()
        return MemoryExtraction(items, edges)
    }

    private fun JSONArray?.toItems(): List<ExtractedStoryItem> = buildList {
        if (this@toItems == null) return@buildList
        for (index in 0 until minOf(length(), 15)) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val kind = item.optString("kind").trim()
            if (name.isBlank() || kind.isBlank()) continue
            val rawStatus = item.optString("status", StoryItemStatus.ACTIVE)
            val status = if (rawStatus in setOf(StoryItemStatus.ACTIVE, StoryItemStatus.RESOLVED, StoryItemStatus.SECRET)) rawStatus else StoryItemStatus.ACTIVE
            add(ExtractedStoryItem(kind, name, item.optString("detail").trim(), status))
        }
    }

    private fun JSONArray?.toEdges(): List<ExtractedStoryEdge> = buildList {
        if (this@toEdges == null) return@buildList
        for (index in 0 until minOf(length(), 20)) {
            val edge = optJSONObject(index) ?: continue
            val source = edge.optString("source").trim()
            val target = edge.optString("target").trim()
            val relation = edge.optString("relation").trim()
            if (source.isBlank() || target.isBlank() || relation.isBlank() || source == target) continue
            add(ExtractedStoryEdge(source, target, relation, edge.optString("description").trim()))
        }
    }
}

