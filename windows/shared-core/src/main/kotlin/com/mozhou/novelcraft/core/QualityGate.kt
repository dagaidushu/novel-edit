package com.mozhou.novelcraft.core

enum class QualitySeverity { INFO, WARNING }

data class QualityIssue(
    val severity: QualitySeverity,
    val title: String,
    val detail: String,
)

object QualityGate {
    private val placeholders = listOf("TODO", "待补", "待写", "此处补充", "XXX")

    fun inspect(chapter: Chapter?, storyItems: List<StoryItem>, anchors: List<StoryAnchor> = emptyList(), project: NovelProject? = null): List<QualityIssue> {
        if (chapter == null) return listOf(QualityIssue(QualitySeverity.WARNING, "尚未选择章节", "请选择一个章节后再检查。"))
        val content = chapter.content.trim()
        val visibleCount = content.count { !it.isWhitespace() }
        val issues = mutableListOf<QualityIssue>()
        if (visibleCount < 400) issues += QualityIssue(QualitySeverity.WARNING, "篇幅偏短", "当前 $visibleCount 字，建议完成一个完整场景后再发布。")
        placeholders.filter { content.contains(it, ignoreCase = true) }.forEach {
            issues += QualityIssue(QualitySeverity.WARNING, "发现占位文本", "正文中包含“$it”，发布前建议替换。")
        }
        duplicateParagraph(content)?.let {
            issues += QualityIssue(QualitySeverity.WARNING, "发现重复段落", "“${it.take(28)}”在本章出现多次。")
        }
        storyItems.filter { it.kind.contains("禁区") || it.kind.contains("保密") }
            .filter { it.name.isNotBlank() && content.contains(it.name) }
            .forEach {
                issues += QualityIssue(QualitySeverity.WARNING, "可能触及保密设定", "“${it.name}”被标为${it.kind}，请确认不是提前揭露。")
            }
        val anchor = anchors.firstOrNull { chapter.number in it.startChapter..it.endChapter }
        anchor?.forbiddenReveals
            ?.split(Regex("[、,，\\n]"))
            ?.map(String::trim)
            ?.filter { it.length >= 2 }
            ?.filter { content.contains(it) }
            ?.forEach {
                issues += QualityIssue(QualitySeverity.WARNING, "可能提前揭露大纲禁区", "“$it”被当前锚点标为禁止揭露，请确认剧情进度。")
            }
        if (content.isNotBlank() && content.takeLast(140).count { it in "。！？!?" } < 1) {
            issues += QualityIssue(QualitySeverity.INFO, "结尾钩子可再明确", "结尾没有完整收束句，确认是否需要留下下一章的动作或悬念。")
        }
        issues += PacingGuard.inspect(project, chapter)
        val aiTrace = AiTraceDetector.inspect(content)
        if (aiTrace.score >= 45) {
            issues += QualityIssue(QualitySeverity.WARNING, "AI 痕迹风险偏高", aiTrace.findings.take(2).joinToString("；"))
        }
        if (issues.isEmpty()) issues += QualityIssue(QualitySeverity.INFO, "本地检查通过", "未发现篇幅、占位符、重复段落或保密设定风险。")
        return issues
    }

    private fun duplicateParagraph(content: String): String? = content
        .split(Regex("\\n\\s*\\n"))
        .map { it.replace(Regex("\\s+"), "").trim() }
        .filter { it.length >= 24 }
        .groupBy { it }
        .entries
        .firstOrNull { it.value.size > 1 }
        ?.key
}

