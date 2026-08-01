package com.mozhou.novelcraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterImporterTest {
    @Test
    fun splitsAChineseManuscriptIntoNumberedChapters() {
        val source = """
            序章
            雨下了一夜。
            
            第1章 旧港
            陈见川走进了仓库。
            
            第 2 章 名单
            那张名单少了一个名字。
        """.trimIndent()

        val chapters = ChapterImporter.parse(source)

        assertEquals(2, chapters.size)
        assertEquals(1, chapters[0].number)
        assertEquals("旧港", chapters[0].title)
        assertTrue(chapters[0].content.contains("陈见川"))
        assertEquals(2, chapters[1].number)
        assertEquals("名单", chapters[1].title)
    }

    @Test
    fun keepsUntitledTextAsASingleEditableChapter() {
        val chapters = ChapterImporter.parse("这是没有章节标题的完整正文。")

        assertEquals(1, chapters.size)
        assertEquals(1, chapters.single().number)
        assertEquals("导入正文", chapters.single().title)
    }

    @Test
    fun supportsChineseNumbersAndMarkdownHeadings() {
        val chapters = ChapterImporter.parse(
            """
            ## 第十二章 月下旧约
            第一段正文。

            第十三章 回港
            第二段正文。
            """.trimIndent(),
        )

        assertEquals(listOf(12, 13), chapters.map { it.number })
        assertEquals("月下旧约", chapters.first().title)
        assertTrue(chapters.last().content.contains("第二段"))
    }
}

