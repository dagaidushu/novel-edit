package com.mozhou.novelcraft.core

internal fun blocksChapterLifecycle(stage: String, content: String): Boolean = when (stage) {
    "独立审稿", "文字校对", "本地一致性与节奏" -> content.contains("[P0]", ignoreCase = true)
    else -> false
}

internal fun isBlockingLocalIssue(issue: QualityIssue): Boolean = issue.title in setOf(
    "发现占位文本",
    "发现重复段落",
    "可能提前揭露大纲禁区",
)

