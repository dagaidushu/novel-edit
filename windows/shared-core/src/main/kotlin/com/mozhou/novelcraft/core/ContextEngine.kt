package com.mozhou.novelcraft.core

data class ContextPacket(
    val relevantItems: List<StoryItem> = emptyList(),
    val relevantEdges: List<StoryEdge> = emptyList(),
    val relevantChapters: List<Chapter> = emptyList(),
    val relevantResearch: List<ResearchNote> = emptyList(),
    val relevantChunks: List<RagChunk> = emptyList(),
    val activeAnchor: StoryAnchor? = null,
    val prompt: String = "",
)

/**
 * A deterministic, offline retrieval pass. It deliberately keeps the selection explainable:
 * named items score highest, then shared Chinese or Latin terms, followed by recent chapters.
 */
object ContextEngine {
    private val termPattern = Regex("[\\p{IsHan}]{2,}|[A-Za-z0-9_]{3,}")

    fun build(
        project: NovelProject,
        current: Chapter,
        chapters: List<Chapter>,
        storyItems: List<StoryItem>,
        anchors: List<StoryAnchor> = emptyList(),
        edges: List<StoryEdge> = emptyList(),
        mentions: List<ChapterStoryMention> = emptyList(),
        researchNotes: List<ResearchNote> = emptyList(),
        ragChunks: List<RagChunk> = emptyList(),
    ): ContextPacket {
        val query = listOf(project.title, project.premise, current.title, current.content.takeLast(1_600)).joinToString("\n")
        val queryTerms = terms(query)
        val relevantItems = storyItems
            .filter { it.status != StoryItemStatus.RESOLVED }
            .map { it to score(query, queryTerms, it.name + "\n" + it.detail) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { it.second }
            .take(8)
            .map { it.first }

        val mentionChapterIds = mentions
            .filter { it.storyItemId in relevantItems.map { item -> item.id }.toSet() }
            .map { it.chapterId }
            .toSet()
        val immediatePrevious = chapters
            .filter { it.number < current.number && it.content.isNotBlank() }
            .maxByOrNull { it.number }
        val rankedChapters = chapters
            .filter { it.id != current.id && it.content.isNotBlank() }
            .map { chapter ->
                val mentionBonus = if (chapter.id in mentionChapterIds) 40 else 0
                chapter to score(query, queryTerms, chapter.title + "\n" + chapter.content.takeLast(900)) + mentionBonus
            }
            .sortedWith(compareByDescending<Pair<Chapter, Int>> { it.second }.thenByDescending { it.first.number })
            .filter { it.second > 0 }
            .take(2)
            .map { it.first }
            .ifEmpty { chapters.filter { it.number < current.number && it.content.isNotBlank() }.takeLast(2) }
        // The direct predecessor is non-negotiable continuity context, even when term ranking favors older lore.
        val retrievedChapters = (listOfNotNull(immediatePrevious) + rankedChapters)
            .distinctBy { it.id }
            .take(3)

        val anchor = anchors.firstOrNull { current.number in it.startChapter..it.endChapter }
        val relevantResearch = researchNotes
            .map { it to score(query, queryTerms, it.title + "\n" + it.tags + "\n" + it.content) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }
        val relevantChunks = ragChunks.filter { it.chapterId != current.id }.map { chunk ->
            chunk to score(query, queryTerms, chunk.terms + "\n" + chunk.content) + if (chunk.chapterId in mentionChapterIds) 30 else 0
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(4).map { it.first }
        val itemsById = storyItems.associateBy { it.id }
        val seedItemIds = relevantItems.map { it.id }.toSet()
        val relevantEdges = edges
            .filter { it.sourceItemId in seedItemIds || it.targetItemId in seedItemIds }
            .take(8)
        val edgeItemIds = relevantEdges.flatMap { listOf(it.sourceItemId, it.targetItemId) }.toSet()
        val packetItems = (relevantItems + edgeItemIds.mapNotNull(itemsById::get))
            .distinctBy { it.id }
        val packet = ContextPacket(packetItems, relevantEdges, retrievedChapters, relevantResearch, relevantChunks, anchor)
        return packet.copy(prompt = buildPrompt(project, current, packet))
    }

    private fun buildPrompt(project: NovelProject, current: Chapter, packet: ContextPacket): String = buildString {
        appendLine("作品：${project.title}")
        appendLine("题材：${project.genre}")
        if (project.premise.isNotBlank()) appendLine("核心设定：${project.premise}")
        if (project.longFormBlueprint.isNotBlank()) appendLine("长篇路线图（必须遵守阶段目标与未解问题）：${project.longFormBlueprint.take(4_000)}")
        if (project.targetChapterCount > 0) appendLine("全书节奏：${project.pacingProfile}，目标 ${project.targetChapterCount} 章 / ${project.targetWordCount.takeIf { it > 0 } ?: "未设"} 字；不得过早收束主线。")
        val wordRange = normalizeChapterWordRange(project.targetChapterWordCount, project.targetChapterWordCountMax)
        appendLine("本章目标篇幅：${wordRange.min}-${wordRange.max} 字。自动化等级：${project.automationLevel}。")
        if (project.forbiddenContent.isNotBlank()) appendLine("项目核心禁区（必须遵守）：${project.forbiddenContent}")
        if (project.styleGuide.isNotBlank()) appendLine("项目文风档案（必须遵守）：${project.styleGuide}")
        appendLine("当前章节：第${current.number}章 ${current.title}")
        if (current.outline.isNotBlank()) appendLine("本章计划：${current.outline}")
        if (current.beatSheet.isNotBlank()) appendLine("本章分镜（必须按顺序展开，不得跳过）：${current.beatSheet}")
        packet.activeAnchor?.let { anchor ->
            appendLine("当前大纲锚点：第${anchor.startChapter}-${anchor.endChapter}章 ${anchor.title}")
            appendLine("本段核心冲突：${anchor.coreConflict}")
            if (anchor.allowedPlot.isNotBlank()) appendLine("本章允许推进：${anchor.allowedPlot}")
            if (anchor.forbiddenReveals.isNotBlank()) appendLine("本章严禁揭露：${anchor.forbiddenReveals}")
            if (anchor.mandatoryTension.isNotBlank()) appendLine("章末必须保留：${anchor.mandatoryTension}")
        }
        appendLine("当前正文末尾：")
        appendLine(current.content.takeLast(2_200))
        if (packet.relevantItems.isNotEmpty()) {
            appendLine("必须优先遵守的本地设定：")
            packet.relevantItems.forEach { appendLine("- [${it.kind}] ${it.name}：${it.detail}") }
        }
        if (packet.relevantEdges.isNotEmpty()) {
            val namesById = packet.relevantItems.associateBy({ it.id }, { it.name })
            appendLine("相关人物与设定关系：")
            packet.relevantEdges.forEach { edge ->
                val source = namesById[edge.sourceItemId] ?: "资料#${edge.sourceItemId}"
                val target = namesById[edge.targetItemId] ?: "资料#${edge.targetItemId}"
                appendLine("- $source ${edge.relation} $target：${edge.description}")
            }
        }
        if (packet.relevantChapters.isNotEmpty()) {
            appendLine("相关已写章节摘录（实体引用优先）：")
            packet.relevantChapters.forEach {
                appendLine("- 第${it.number}章 ${it.title}：${it.content.takeLast(500)}")
            }
        }
        if (packet.relevantChunks.isNotEmpty()) {
            appendLine("长期索引命中片段（按相关度排序）：")
            packet.relevantChunks.forEach { appendLine("- 第${it.chapterNumber}章片段：${it.content.take(500)}") }
        }
        if (packet.relevantResearch.isNotEmpty()) {
            appendLine("相关调研事实（仅作创作背景，不得编造来源外信息）：")
            packet.relevantResearch.forEach { note -> appendLine("- ${note.title}：${note.content.take(700)}") }
        }
        appendLine("续写要求：必须使用第三人称叙事；严禁使用“我”作为叙述主语；保持时态一致；先推进一个可见动作；不要复述设定；不提前揭露未解谜底；结尾保留具体、可继续写的钩子。")
    }

    private fun score(query: String, queryTerms: Set<String>, candidate: String): Int {
        if (candidate.isBlank()) return 0
        val normalized = candidate.lowercase()
        var result = 0
        queryTerms.forEach { term -> if (normalized.contains(term.lowercase())) result += term.length }
        val directNameBonus = terms(candidate).maxOfOrNull { term -> if (query.contains(term)) term.length * 2 else 0 } ?: 0
        return result + directNameBonus
    }

    private fun terms(text: String): Set<String> = termPattern.findAll(text)
        .map { it.value.trim() }
        .filter { it.length >= 2 }
        .toSet()
}

