package com.mozhou.novelcraft.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterLifecycleGatePolicyTest {
    @Test
    fun localAndStyleFindingsAreAdvisoryWhileP0EditorialFindingsBlock() {
        assertFalse(blocksChapterLifecycle("本地一致性与节奏", "WARN: 篇幅偏短"))
        assertFalse(blocksChapterLifecycle("文风校准", "FAIL: 叙述节奏偏离文风档案"))
        assertFalse(blocksChapterLifecycle("文风校准", "UNAVAILABLE: 文风校准请求失败"))
        assertTrue(blocksChapterLifecycle("独立审稿", "[P0] 人物在上一章死亡后再次出场"))
        assertTrue(blocksChapterLifecycle("文字校对", "FAIL: [P0] 正文包含未处理占位符"))
    }
}

