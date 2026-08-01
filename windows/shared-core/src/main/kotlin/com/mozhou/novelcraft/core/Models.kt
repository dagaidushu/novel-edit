package com.mozhou.novelcraft.core

data class ModelConfig(
    val provider: String = "OpenAI 兼容",
    val protocol: String = "openai",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val imageModel: String = "",
    val imageProtocol: String = "openai",
    val reviewerBaseUrl: String = "",
    val reviewerApiKey: String = "",
    val reviewerModel: String = "",
    val reviewerProtocol: String = "",
)

data class NovelProject(
    val id: Long = 0,
    val title: String,
    val genre: String,
    val premise: String,
    val styleGuide: String = "",
    val outlineRevisionReport: String = "",
    val summary: String = "",
    val tags: String = "",
    val targetAudience: String = "",
    val protagonistName: String = "",
    val longFormBlueprint: String = "",
    val targetChapterCount: Int = 0,
    val targetWordCount: Int = 0,
    val pacingProfile: String = "均衡",
    val forbiddenContent: String = "",
    val automationLevel: String = "半自动",
    val targetChapterWordCount: Int = 3000,
    val targetChapterWordCountMax: Int = 5000,
    val coverPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = "",
)

data class Chapter(
    val id: Long = 0,
    val projectId: Long,
    val number: Int,
    val title: String,
    val content: String = "",
    val outline: String = "",
    val beatSheet: String = "",
    val targetWordCount: Int = 0,
    val qualityStatus: String = ChapterQualityStatus.READY,
    val qualityIssueSummary: String = "",
    val lifecycleStatus: String = ChapterLifecycleStatus.MANUAL,
    val lifecycleDetail: String = "",
    val memoryUpdatedAt: Long = 0,
    val autoWriteRunId: Long = 0,
    val gateFailureCount: Int = 0,
    val requiresHumanReview: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ChapterRevision(val id: Long = 0, val projectId: Long, val chapterId: Long, val previousContent: String, val reason: String, val createdAt: Long = System.currentTimeMillis())
data class AutoWriteRun(val id: Long = 0, val projectId: Long, val requestedCount: Int, val completedCount: Int = 0, val status: String = AutoWriteRunStatus.RUNNING, val detail: String = "", val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())
data class ImportAnalysisRun(val projectId: Long, val status: String = ImportAnalysisStatus.QUEUED, val stage: String = "等待开始", val progress: Int = 0, val detail: String = "", val updatedAt: Long = System.currentTimeMillis())
data class ChapterStoryMention(val id: Long = 0, val projectId: Long, val chapterId: Long, val storyItemId: Long, val createdAt: Long = System.currentTimeMillis())
data class ResearchNote(val id: Long = 0, val projectId: Long, val title: String, val sourceUrl: String = "", val tags: String = "", val content: String, val rightsConfirmed: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())
data class EditorialReview(val id: Long = 0, val projectId: Long, val chapterId: Long, val content: String, val createdAt: Long = System.currentTimeMillis())
data class IdeationDraft(val id: Long = 0, val step: Int = 1, val title: String = "", val genre: String = "", val premise: String = "", val protagonist: String = "", val conflict: String = "", val promise: String = "", val targetAudience: String = "", val writingStyle: String = "", val forbiddenContent: String = "", val automationLevel: String = "半自动", val targetChapterWordCount: Int = 3000, val targetChapterWordCountMax: Int = 5000, val targetWordCount: Int = 0, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())
data class ChapterPacingEvent(val id: Long = 0, val projectId: Long, val chapterId: Long, val chapterNumber: Int, val eventType: String, val pace: String, val note: String = "", val createdAt: Long = System.currentTimeMillis())
data class EventMatrixRule(val id: Long = 0, val projectId: Long, val ruleKey: String, val label: String, val cooldown: Int, val category: String, val enabled: Boolean = true, val createdAt: Long = System.currentTimeMillis())
data class ChapterGateReport(val id: Long = 0, val projectId: Long, val chapterId: Long, val stage: String, val passed: Boolean, val content: String, val contextSnapshot: String = "", val createdAt: Long = System.currentTimeMillis())
data class StyleProfile(val id: Long = 0, val name: String, val genre: String = "", val guide: String, val sourceProjectId: Long = 0, val metrics: String = "", val keywords: String = "", val createdAt: Long = System.currentTimeMillis())
data class BatchReviewRun(val id: Long = 0, val projectId: Long, val startChapter: Int, val endChapter: Int, val round: Int = 1, val report: String, val createdAt: Long = System.currentTimeMillis())
data class ReviewIssue(val id: Long = 0, val projectId: Long = 0, val reviewRunId: Long = 0, val chapterNumber: Int = 0, val severity: String, val summary: String, val status: String = "open", val createdAt: Long = System.currentTimeMillis())
data class RagChunk(val id: Long = 0, val projectId: Long, val chapterId: Long, val chapterNumber: Int, val ordinal: Int, val content: String, val terms: String, val updatedAt: Long = System.currentTimeMillis())
data class ChapterContinuitySnapshot(val chapterId: Long, val projectId: Long, val predecessorChapterId: Long = 0, val predecessorTail: String = "", val contextPrompt: String, val confirmationStatus: String = ContinuitySnapshotStatus.CONFIRMED, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())
data class ChapterLifecycleJob(val chapterId: Long, val projectId: Long, val contentFingerprint: String, val status: String = ChapterLifecycleJobStatus.QUEUED, val attempts: Int = 0, val detail: String = "等待后台闭环", val afterSuccessAction: String = "", val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())
data class StoryItem(val id: Long = 0, val projectId: Long, val kind: String, val name: String, val detail: String, val status: String = StoryItemStatus.ACTIVE, val updatedAt: Long = System.currentTimeMillis(), val cascadePending: Boolean = false)
data class StoryAnchor(val id: Long = 0, val projectId: Long, val startChapter: Int, val endChapter: Int, val title: String, val coreConflict: String, val allowedPlot: String = "", val forbiddenReveals: String = "", val mandatoryTension: String = "", val cascadePending: Boolean = false)
data class StoryEdge(val id: Long = 0, val projectId: Long, val sourceItemId: Long, val targetItemId: Long, val relation: String, val strength: Float = 0.5f, val description: String = "", val sinceChapter: Int = 1, val cascadePending: Boolean = false)

object ImportAnalysisStatus { const val QUEUED = "queued"; const val RUNNING = "running"; const val WAITING_FOR_CONFIG = "waiting_for_config"; const val WAITING_FOR_NETWORK = "waiting_for_network"; const val COMPLETED = "completed"; const val FAILED = "failed"; const val CANCELLED = "cancelled" }
object ContinuitySnapshotStatus { const val PENDING = "pending"; const val CONFIRMED = "confirmed" }
object ChapterLifecycleJobStatus { const val QUEUED = "queued"; const val RUNNING = "running"; const val FAILED = "failed"; const val COMPLETED = "completed" }
object AutoWriteRunStatus { const val RUNNING = "running"; const val PAUSED = "paused"; const val COMPLETED = "completed" }
object ChapterQualityStatus { const val READY = "ready"; const val NEEDS_REPAIR = "needs_repair" }
object StoryItemStatus { const val ACTIVE = "活跃"; const val RESOLVED = "已回收"; const val SECRET = "保密" }
