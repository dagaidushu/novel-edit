package com.mozhou.novelcraft.core

/**
 * Durable state for the post-generation pipeline. Manual edits are never blocked;
 * the state only controls automated progression to a later AI-written chapter.
 */
object ChapterLifecycleStatus {
    const val MANUAL = "manual"
    const val PROCESSING = "processing"
    const val WAITING_REVIEW = "waiting_review"
    const val PASSED = "passed"
    const val MEMORY_FAILED = "memory_failed"

    fun label(status: String): String = when (status) {
        PROCESSING -> "正在同步记忆"
        WAITING_REVIEW -> "等待作者处理"
        PASSED -> "写作闭环已通过"
        MEMORY_FAILED -> "记忆同步失败"
        else -> "手动创作"
    }

    fun blocksAutomaticWriting(status: String): Boolean =
        status == PROCESSING || status == WAITING_REVIEW || status == MEMORY_FAILED
}

data class ChapterLifecycleResult(
    val passed: Boolean,
    val message: String,
)

