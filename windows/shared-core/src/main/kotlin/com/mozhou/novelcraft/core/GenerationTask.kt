package com.mozhou.novelcraft.core

import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap

enum class GenerationTask(val label: String) {
    OPENING_CHAPTER("第一章"),
    CONTINUATION("续写"),
    AUTO_WRITE("批量写作"),
    CHAPTER_PLAN("本章计划"),
    BEAT_SHEET("场景分镜"),
    STYLE_GUIDE("文风提取"),
    MEMORY_EXTRACTION("知识图谱"),
    CHAPTER_LIFECYCLE("章节闭环"),
    REPAIR_PLAN("修复计划"),
    CHAPTER_REWRITE("AI 改写"),
    HUMANIZE("去 AI 味润色"),
    EDITORIAL_REVIEW("编辑审稿"),
    EDITORIAL_TEAM("编辑团队"),
    BATCH_REVIEW("批量审稿"),
    REFERENCE_ANALYSIS("结构提炼"),
    COVER("封面"),
    PROJECT_PROFILE("作品设定"),
    LONG_FORM_BLUEPRINT("长篇路线图"),
}

internal enum class ChapterAdvanceMode {
    WAIT_FOR_SUCCESS,
}

internal fun chapterAdvanceMode(@Suppress("UNUSED_PARAMETER") previousLifecycleStatus: String): ChapterAdvanceMode {
    return ChapterAdvanceMode.WAIT_FOR_SUCCESS
}

enum class NextChapterAction(val storageValue: String) {
    CREATE_BLANK("create_blank"),
    GENERATE_WITH_AI("generate_with_ai");

    companion object {
        fun fromStorage(value: String): NextChapterAction? = entries.firstOrNull { it.storageValue == value }
    }
}

internal fun GenerationTask.canRunAlongside(other: GenerationTask): Boolean =
    this != other && (this == GenerationTask.CHAPTER_LIFECYCLE || other == GenerationTask.CHAPTER_LIFECYCLE)

internal fun shouldKeepGenerationForeground(activeTasks: Set<GenerationTask>): Boolean = activeTasks.isNotEmpty()

class GenerationRequest {
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()

    @Volatile
    var isCancelled: Boolean = false

    fun attach(connection: HttpURLConnection) {
        connections += connection
        if (isCancelled) connection.disconnect()
    }

    fun detach(connection: HttpURLConnection) {
        connections -= connection
    }

    fun cancel() {
        isCancelled = true
        connections.forEach { it.disconnect() }
        connections.clear()
    }
}

