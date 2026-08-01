package com.mozhou.novelcraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterGenerationPolicyTest {
    @Test
    fun shortGeneratedChapterRequiresExpansion() {
        assertTrue(needsChapterExpansion("短正文"))
        assertTrue(needsChapterExpansion("字".repeat(3_999)))
        assertFalse(needsChapterExpansion("字".repeat(MIN_GENERATED_CHAPTER_CHARS)))
    }

    @Test
    fun generatedTitleStripsChapterNumberAndNeverFallsBackToPlaceholder() {
        assertEquals("雨夜来信", chapterTitleOrFallback("第1章：雨夜来信", "第1章：主角的选择"))
        assertEquals("第1章：主角的选择", chapterTitleOrFallback("", "第1章：主角的选择"))
    }

    @Test
    fun openingGenerationOnlyReplacesTheUntouchedStarterDraft() {
        assertEquals("AI 正文", mergeOpeningChapterContent("默认开篇", "默认开篇", "AI 正文"))
        assertEquals("我的开头\n\nAI 正文", mergeOpeningChapterContent("默认开篇", "我的开头", "AI 正文"))
    }

    @Test
    fun firstGeneratedContentForANewChapterStartsOnTheFirstLine() {
        assertEquals("AI 正文", appendGeneratedChapterContent("", "AI 正文"))
        assertEquals("已有正文\n\nAI 正文", appendGeneratedChapterContent("已有正文", "AI 正文"))
    }

    @Test
    fun generatedNewChapterRequestsATitleButContinuationDoesNotReplaceOne() {
        assertTrue(shouldGenerateChapterTitle("第2章", ""))
        assertFalse(shouldGenerateChapterTitle("雨夜来信", "已有正文"))
    }

}

