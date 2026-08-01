package com.mozhou.novelcraft.core

object StoryGraphExport {
    fun asMermaid(items: List<StoryItem>, edges: List<StoryEdge>): String {
        if (items.isEmpty()) return "graph TD\n  empty[\"尚未建立资料卡\"]"
        val nodes = items.associateBy { it.id }
        return buildString {
            appendLine("graph TD")
            items.forEach { item ->
                appendLine("  item${item.id}[\"${safe(item.name.ifBlank { item.kind })}\\n${safe(item.kind)}\"]")
            }
            edges.forEach { edge ->
                if (edge.sourceItemId in nodes && edge.targetItemId in nodes) {
                    appendLine("  item${edge.sourceItemId} -->|${safe(edge.relation)}| item${edge.targetItemId}")
                }
            }
        }.trimEnd()
    }

    private fun safe(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "'")
        .replace("[", "(")
        .replace("]", ")")
        .replace("|", "/")
        .replace("\n", " ")
        .trim()
}

