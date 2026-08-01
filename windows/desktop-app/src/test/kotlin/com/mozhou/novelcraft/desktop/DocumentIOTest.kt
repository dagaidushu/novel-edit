package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.Chapter
import com.mozhou.novelcraft.core.NovelProject
import com.mozhou.novelcraft.core.ResearchNote
import com.mozhou.novelcraft.core.StoryItem
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentIOTest {
    private val project=NovelProject(id=1,title="雾港来信",genre="悬疑",premise="寻找失踪的姐姐")
    private val chapters=listOf(Chapter(id=1,projectId=1,number=1,title="第一章 雨夜",content="灯火映在水面。\n沈舟走进旧码头。"))

    @Test fun exportsAndReadsSupportedDocuments(){
        val dir=Files.createTempDirectory("novelcraft-docs")
        val notes = listOf(ResearchNote(projectId = 1, title = "公开档案", sourceUrl = "https://example.test/source", content = "用于验证资料来源会写入导出的文档。"))
        val items = listOf(StoryItem(projectId = 1, kind = "人物", name = "沈舟", detail = "主角"))
        listOf("markdown" to "md","docx" to "docx","epub" to "epub","pdf" to "pdf").forEach{(format,extension)->
            val path=dir.resolve("novel.$extension")
            DocumentIO.export(path,format,project,chapters,items = items,notes = notes)
            assertTrue(Files.size(path)>100,"$format export should not be empty")
            val read = DocumentIO.read(path)
            assertTrue(read.contains("灯火"), "$format should round trip Chinese prose, got: $read")
        }
        assertTrue(Files.readString(dir.resolve("novel.md")).contains("资料来源"))
    }

    @Test fun readsEpubInOpfSpineOrder() {
        val path = Files.createTempFile("novelcraft-spine", ".epub")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            fun entry(name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() }
            entry("OEBPS/second.xhtml", "<html><body>${"后章内容。".repeat(12)}</body></html>")
            entry("OEBPS/first.xhtml", "<html><body>${"前章内容。".repeat(12)}</body></html>")
            entry("OEBPS/content.opf", """<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="first" href="first.xhtml"/><item id="second" href="second.xhtml"/></manifest><spine><itemref idref="first"/><itemref idref="second"/></spine></package>""")
        }
        val text = DocumentIO.read(path)
        assertTrue(text.indexOf("前章内容") < text.indexOf("后章内容"))
    }

    @Test fun exportedEpubStartsWithStoredMimetype() {
        val path = Files.createTempFile("novelcraft-valid", ".epub")
        DocumentIO.export(path, "epub", project, chapters)
        java.util.zip.ZipFile(path.toFile()).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
        }
    }
}
