package com.mozhou.novelcraft.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationConcurrencyPolicyTest {
    @Test
    fun lifecycleCanRunAlongsideWritingButWritingTasksRemainExclusive() {
        assertTrue(GenerationTask.CHAPTER_LIFECYCLE.canRunAlongside(GenerationTask.CONTINUATION))
        assertTrue(GenerationTask.CONTINUATION.canRunAlongside(GenerationTask.CHAPTER_LIFECYCLE))
        assertFalse(GenerationTask.CONTINUATION.canRunAlongside(GenerationTask.OPENING_CHAPTER))
    }
}

