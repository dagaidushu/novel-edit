package com.mozhou.novelcraft.core

data class OutlineCascadeReport(
    val fromChapter: Int,
    val affectedAnchorIds: Set<Long>,
    val affectedItemIds: Set<Long>,
    val affectedEdgeIds: Set<Long>,
    val summary: String,
)

object OutlineCascadeAnalyzer {
    fun analyze(
        fromChapter: Int,
        chapters: List<Chapter>,
        items: List<StoryItem>,
        anchors: List<StoryAnchor>,
        edges: List<StoryEdge>,
        description: String,
    ): OutlineCascadeReport {
        val start = fromChapter.coerceAtLeast(1)
        val futureText = description + "\n" + chapters.filter { it.number >= start }
            .joinToString("\n") { "${it.title}\n${it.outline}\n${it.beatSheet}\n${it.content}" }
        val seedItems = items.filter { it.name.isNotBlank() && futureText.contains(it.name) }.map { it.id }.toMutableSet()
        edges.filter { it.sinceChapter >= start }.forEach { seedItems += it.sourceItemId; seedItems += it.targetItemId }
        val affectedEdges = mutableSetOf<Long>()
        var changed = true
        while (changed) {
            changed = false
            edges.forEach { edge ->
                if (edge.sourceItemId in seedItems || edge.targetItemId in seedItems) {
                    if (affectedEdges.add(edge.id)) changed = true
                    if (seedItems.add(edge.sourceItemId)) changed = true
                    if (seedItems.add(edge.targetItemId)) changed = true
                }
            }
        }
        val affectedAnchors = anchors.filter { it.endChapter >= start }.map { it.id }.toSet()
        val summary = "从第${start}章起改纲：锚点 ${affectedAnchors.size} 个，资料 ${seedItems.size} 项，关系 ${affectedEdges.size} 条。${description.trim()}"
        return OutlineCascadeReport(start, affectedAnchors, seedItems, affectedEdges, summary)
    }
}

