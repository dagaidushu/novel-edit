package com.mozhou.novelcraft.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterAdvancePolicyTest {
    @Test
    fun nextChapterWaitsForSuccessfulLifecycleBeforeGeneration() {
        listOf(
            ChapterLifecycleStatus.PROCESSING,
            ChapterLifecycleStatus.WAITING_REVIEW,
            ChapterLifecycleStatus.MEMORY_FAILED,
            ChapterLifecycleStatus.PASSED,
        ).forEach { status ->
            assertEquals(ChapterAdvanceMode.WAIT_FOR_SUCCESS, chapterAdvanceMode(status))
        }
    }
}

