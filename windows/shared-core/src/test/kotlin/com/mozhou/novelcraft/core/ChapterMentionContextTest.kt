package com.mozhou.novelcraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterMentionContextTest {
    @Test
    fun prioritizes_chapters_linked_to_relevant_story_items() {
        val project = NovelProject(id = 1, title = "测试", genre = "玄幻", premise = "寻找古剑")
        val item = StoryItem(id = 10, projectId = 1, kind = "物品", name = "古剑", detail = "主线线索")
        val linked = Chapter(id = 2, projectId = 1, number = 1, title = "雨夜", content = "沈舟把古剑藏进袖中。")
        val unrelated = Chapter(id = 3, projectId = 1, number = 2, title = "闲谈", content = "城门外的风吹过石阶。")
        val current = Chapter(id = 4, projectId = 1, number = 3, title = "追查", content = "古剑的剑鞘突然发热。")

        val packet = ContextEngine.build(
            project, current, listOf(linked, unrelated, current), listOf(item),
            mentions = listOf(ChapterStoryMention(projectId = 1, chapterId = linked.id, storyItemId = item.id)),
        )

        assertEquals(unrelated, packet.relevantChapters.first())
        assertTrue(packet.relevantChapters.contains(linked))
    }

    @Test
    fun always_includes_the_immediate_previous_chapter_in_continuity_context() {
        val project = NovelProject(id = 1, title = "测试", genre = "悬疑", premise = "寻找月契")
        val olderRelevant = Chapter(id = 1, projectId = 1, number = 1, title = "月契", content = "沈舟发现月契的秘密。")
        val immediatePrevious = Chapter(id = 2, projectId = 1, number = 2, title = "雨巷", content = "沈舟带着钥匙走进雨巷。")
        val current = Chapter(id = 3, projectId = 1, number = 3, title = "追查", content = "月契在掌心发烫。")

        val packet = ContextEngine.build(project, current, listOf(olderRelevant, immediatePrevious, current), emptyList())

        assertEquals(immediatePrevious, packet.relevantChapters.first())
        assertEquals(2, packet.relevantChapters.size)
    }
}

