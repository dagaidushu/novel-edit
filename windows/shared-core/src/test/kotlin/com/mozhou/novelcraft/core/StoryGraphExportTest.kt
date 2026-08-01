package com.mozhou.novelcraft.core

import org.junit.Assert.assertTrue
import org.junit.Test

class StoryGraphExportTest {
    @Test
    fun exports_only_edges_with_known_endpoints_and_escapes_labels() {
        val source = StoryItem(id = 1, projectId = 1, kind = "人物", name = "沈\"舟", detail = "")
        val target = StoryItem(id = 2, projectId = 1, kind = "物品", name = "月契", detail = "")
        val valid = StoryEdge(projectId = 1, sourceItemId = 1, targetItemId = 2, relation = "持有")
        val escaped = StoryEdge(projectId = 1, sourceItemId = 2, targetItemId = 1, relation = "敌对|利用")
        val dangling = StoryEdge(projectId = 1, sourceItemId = 1, targetItemId = 99, relation = "未知")

        val output = StoryGraphExport.asMermaid(listOf(source, target), listOf(valid, escaped, dangling))

        assertTrue(output.contains("graph TD"))
        assertTrue(output.contains("item1 -->|持有| item2"))
        assertTrue(!output.contains("item99"))
        assertTrue(output.contains("沈'舟"))
        assertTrue(output.contains("敌对/利用"))
    }
}

