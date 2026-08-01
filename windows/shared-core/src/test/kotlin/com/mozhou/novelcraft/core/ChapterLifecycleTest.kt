package com.mozhou.novelcraft.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterLifecycleTest {
    @Test
    fun only_unfinished_ai_lifecycle_states_block_automatic_writing() {
        assertTrue(ChapterLifecycleStatus.blocksAutomaticWriting(ChapterLifecycleStatus.PROCESSING))
        assertTrue(ChapterLifecycleStatus.blocksAutomaticWriting(ChapterLifecycleStatus.WAITING_REVIEW))
        assertTrue(ChapterLifecycleStatus.blocksAutomaticWriting(ChapterLifecycleStatus.MEMORY_FAILED))
        assertFalse(ChapterLifecycleStatus.blocksAutomaticWriting(ChapterLifecycleStatus.MANUAL))
        assertFalse(ChapterLifecycleStatus.blocksAutomaticWriting(ChapterLifecycleStatus.PASSED))
    }
}

