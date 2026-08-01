package com.mozhou.novelcraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAndQualityTest {
    private val project = NovelProject(id = 1, title = "雾港来信", genre = "悬疑", premise = "沈舟寻找失踪的姐姐")

    @Test
    fun retrievesNamedMemoryAndRelevantPreviousChapter() {
        val previous = Chapter(id = 2, projectId = 1, number = 1, title = "旧码头", content = "沈舟在旧码头发现姐姐留下的月契。")
        val current = Chapter(id = 3, projectId = 1, number = 2, title = "雨夜", content = "沈舟握紧月契，朝仓库的灯光走去。")
        val memory = StoryItem(id = 4, projectId = 1, kind = "人物", name = "沈舟", detail = "姐姐失踪后独自追查。")
        val resolved = StoryItem(id = 5, projectId = 1, kind = "伏笔", name = "月契", detail = "已经在上一章回收。", status = StoryItemStatus.RESOLVED)
        val anchor = StoryAnchor(projectId = 1, startChapter = 2, endChapter = 8, title = "港口迷局", coreConflict = "沈舟必须找到失踪姐姐的线索", forbiddenReveals = "姐姐的真实下落")

        val packet = ContextEngine.build(project.copy(styleGuide = "第三人称限知，短句推进，章末留钩子", forbiddenContent = "姐姐下落不得提前揭露", automationLevel = "自动推进", targetChapterWordCount = 4200, targetChapterWordCountMax = 6000), current, listOf(previous, current), listOf(memory, resolved), listOf(anchor))

        assertEquals(memory, packet.relevantItems.single())
        assertEquals(previous, packet.relevantChapters.single())
        assertTrue(packet.prompt.contains("必须优先遵守的本地设定"))
        assertTrue(packet.prompt.contains("旧码头"))
        assertEquals(anchor, packet.activeAnchor)
        assertTrue(packet.prompt.contains("本章严禁揭露"))
        assertTrue(packet.prompt.contains("项目文风档案"))
        assertTrue(packet.prompt.contains("本章目标篇幅：4200-6000 字。自动化等级：自动推进。"))
        assertTrue(packet.prompt.contains("项目核心禁区（必须遵守）：姐姐下落不得提前揭露"))
    }

    @Test
    fun writingContextAlwaysForcesThirdPersonNarration() {
        val chapter = Chapter(id = 3, projectId = 1, number = 2, title = "雨夜", content = "沈舟走进空仓库。")

        val packet = ContextEngine.build(
            project.copy(styleGuide = "第一人称限知叙事，短句推进"),
            chapter,
            listOf(chapter),
            emptyList(),
        )

        assertTrue(packet.prompt.contains("必须使用第三人称叙事；严禁使用“我”作为叙述主语"))
    }

    @Test
    fun warnsWithoutBlockingForDuplicatePlaceholderAndForbiddenReveal() {
        val chapter = Chapter(
            projectId = 1,
            number = 1,
            title = "试写",
            content = "TODO\n\n这是一段重复的测试正文，需要足够长度才能被视为段落。\n\n这是一段重复的测试正文，需要足够长度才能被视为段落。",
        )
        val forbidden = StoryItem(projectId = 1, kind = "禁区", name = "真实身份", detail = "第五十章前不得揭露")

        val issues = QualityGate.inspect(chapter, listOf(forbidden))

        assertTrue(issues.any { it.title == "发现占位文本" })
        assertTrue(issues.any { it.title == "发现重复段落" })
        assertTrue(issues.all { it.severity != QualitySeverity.INFO || it.title != "本地检查通过" })
    }

    @Test
    fun extractsParagraphsFromDocxDocumentXml() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>第1章 雨夜</w:t></w:r></w:p>
              <w:p><w:r><w:t>灯火映在水面。</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent()

        val text = DocumentTextExtractor.extractDocumentXml(xml)

        assertEquals("第1章 雨夜\n灯火映在水面。", text)
    }

    @Test
    fun flagsAnchorForbiddenReveal() {
        val chapter = Chapter(projectId = 1, number = 3, title = "揭露", content = "姐姐的真实下落就在港口的灯塔里。")
        val anchor = StoryAnchor(projectId = 1, startChapter = 1, endChapter = 10, title = "前卷", coreConflict = "寻找线索", forbiddenReveals = "姐姐的真实下落")

        val issues = QualityGate.inspect(chapter, emptyList(), listOf(anchor))

        assertTrue(issues.any { it.title == "可能提前揭露大纲禁区" })
    }

    @Test
    fun includesRelevantGraphRelationshipsInWritingContext() {
        val project = NovelProject(id = 1, title = "Harbor Case", genre = "Mystery", premise = "Lena seeks a Cipher")
        val chapter = Chapter(projectId = 1, number = 2, title = "Cipher Trail", content = "Lena follows the Cipher into the warehouse.")
        val lena = StoryItem(id = 10, projectId = 1, kind = "人物", name = "Lena", detail = "Lead investigator")
        val cipher = StoryItem(id = 11, projectId = 1, kind = "物品", name = "Cipher", detail = "A missing ledger")
        val edge = StoryEdge(projectId = 1, sourceItemId = lena.id, targetItemId = cipher.id, relation = "linked", description = "Lena protects the Cipher")

        val packet = ContextEngine.build(project, chapter, listOf(chapter), listOf(lena, cipher), edges = listOf(edge))

        assertEquals(edge, packet.relevantEdges.single())
        assertTrue(packet.prompt.contains("linked"))
        assertTrue(packet.prompt.contains("Lena"))
    }

    @Test
    fun cascadesFromFutureEdgesAcrossConnectedStoryItems() {
        val a = StoryItem(id = 1, projectId = 1, kind = "人物", name = "Lena", detail = "")
        val b = StoryItem(id = 2, projectId = 1, kind = "物品", name = "Cipher", detail = "")
        val c = StoryItem(id = 3, projectId = 1, kind = "地点", name = "Vault", detail = "")
        val report = OutlineCascadeAnalyzer.analyze(
            4,
            listOf(Chapter(projectId = 1, number = 4, title = "Change", outline = "Cipher route")),
            listOf(a, b, c),
            listOf(StoryAnchor(id = 9, projectId = 1, startChapter = 3, endChapter = 8, title = "Arc", coreConflict = "Test")),
            listOf(StoryEdge(id = 10, projectId = 1, sourceItemId = a.id, targetItemId = b.id, relation = "owns", sinceChapter = 4), StoryEdge(id = 11, projectId = 1, sourceItemId = b.id, targetItemId = c.id, relation = "hidden at", sinceChapter = 1)),
            "Change route",
        )

        assertEquals(setOf(1L, 2L, 3L), report.affectedItemIds)
        assertEquals(setOf(10L, 11L), report.affectedEdgeIds)
        assertEquals(setOf(9L), report.affectedAnchorIds)
    }
}

