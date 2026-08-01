package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.Chapter
import com.mozhou.novelcraft.core.NovelProject
import com.mozhou.novelcraft.core.ResearchNote
import com.mozhou.novelcraft.core.StoryEdge
import com.mozhou.novelcraft.core.StoryGraphExport
import com.mozhou.novelcraft.core.StoryItem
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.fontbox.ttf.TrueTypeCollection
import org.apache.fontbox.ttf.TrueTypeFont
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.io.StringReader
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object DocumentIO {
    fun read(path:Path):String=when(path.fileName.toString().substringAfterLast('.',"").lowercase()){
        "txt","md","markdown"->readText(path)
        "docx"->Files.newInputStream(path).use{input->XWPFDocument(input).use{doc->doc.paragraphs.joinToString("\n"){it.text}}}
        "pdf"->Loader.loadPDF(path.toFile()).use{PDFTextStripper().getText(it)}
        "epub"->readEpub(path)
        else->error("不支持的文件格式：${path.fileName}")
    }
    fun export(path:Path,format:String,project:NovelProject,chapters:List<Chapter>,items:List<StoryItem> = emptyList(),edges:List<StoryEdge> = emptyList(),notes:List<ResearchNote> = emptyList()){
        val temp=path.resolveSibling(path.fileName.toString()+".tmp")
        when(format.lowercase()){
            "md","markdown"->Files.writeString(temp,markdown(project,chapters,items,edges,notes))
            "docx"->writeDocx(temp,project,chapters,notes)
            "epub"->writeEpub(temp,project,chapters,notes)
            "pdf"->writePdf(temp,project,chapters,notes)
            else->error("不支持的导出格式：$format")
        }
        Files.move(temp,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    private fun readText(path:Path):String{val bytes=Files.readAllBytes(path);return runCatching{StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")}.getOrElse{String(bytes,Charset.forName("GB18030"))}}
    private fun readEpub(path: Path): String = ZipFile(path.toFile()).use { zip ->
        val entries = LinkedHashMap<String, ByteArray>()
        zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry -> entries[entry.name] = zip.getInputStream(entry).readBytes() }
        val opf = entries.entries.firstOrNull { it.key.endsWith(".opf", ignoreCase = true) }
        val ordered = opf?.let { (opfPath, bytes) -> epubSpine(opfPath, bytes.toString(Charsets.UTF_8)) }
            ?.mapNotNull { target -> entries.keys.firstOrNull { it.equals(target, ignoreCase = true) } }
            ?.takeIf { it.isNotEmpty() }
            ?: entries.keys.filter(::isHtmlDocument)
        // A short first chapter is still real prose. Do not discard it merely because a later
        // reference page happens to be longer than forty characters.
        val sections = ordered.mapNotNull { name -> entries[name]?.toString(Charsets.UTF_8)?.let(::htmlToText)?.takeIf { it.isNotBlank() } }
        require(sections.isNotEmpty()) { "EPUB 中没有找到可读取正文" }
        sections.joinToString("\n\n")
    }
    private fun isHtmlDocument(name: String) = name.lowercase().let { it.endsWith(".xhtml") || it.endsWith(".html") || it.endsWith(".htm") }
    private fun htmlToText(html: String) = html.replace(Regex("(?is)<script.*?</script>|<style.*?</style>|<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim()
    private fun epubSpine(opfPath: String, xml: String): List<String> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); setFeature("http://xml.org/sax/features/external-general-entities", false); setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val manifest = mutableMapOf<String, String>()
        val itemNodes = document.getElementsByTagNameNS("*", "item")
        for (index in 0 until itemNodes.length) itemNodes.item(index).attributes.let { attrs -> attrs.getNamedItem("id")?.nodeValue?.let { id -> attrs.getNamedItem("href")?.nodeValue?.let { manifest[id] = it } } }
        val parent = opfPath.substringBeforeLast('/', "")
        val refs = document.getElementsByTagNameNS("*", "itemref")
        (0 until refs.length).mapNotNull { refs.item(it).attributes.getNamedItem("idref")?.nodeValue?.let(manifest::get) }.map { href -> normalizeEpubPath(if (parent.isBlank()) href else "$parent/$href") }.filter(::isHtmlDocument)
    }.getOrDefault(emptyList())
    private fun normalizeEpubPath(path: String): String { val parts = mutableListOf<String>(); path.replace('\\', '/').split('/').forEach { when (it) { "", "." -> Unit; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts += it } }; return parts.joinToString("/") }
    private fun markdown(p:NovelProject,cs:List<Chapter>,items:List<StoryItem>,edges:List<StoryEdge>,notes:List<ResearchNote>)=buildString{appendLine("# ${p.title}");p.genre.takeIf{it.isNotBlank()}?.let{appendLine("题材：$it")};p.summary.takeIf{it.isNotBlank()}?.let{appendLine("简介：$it")};appendLine();cs.forEach{appendLine("## 第${it.number}章 ${it.title}");appendLine();appendLine(it.content.trim());appendLine()};appendLine("## 资料来源");notes.filter{it.sourceUrl.isNotBlank()}.ifEmpty{listOf()}.let{sources->if(sources.isEmpty())appendLine("无")else sources.forEachIndexed{index,note->appendLine("${index+1}. ${note.title}：${note.sourceUrl}")}};appendLine();appendLine("## 知识图谱");appendLine("```mermaid");appendLine(StoryGraphExport.asMermaid(items,edges));appendLine("```")}
    private fun writeDocx(path:Path,p:NovelProject,cs:List<Chapter>,notes:List<ResearchNote>){XWPFDocument().use{doc->doc.createParagraph().apply{style="Title";createRun().setText(p.title)};p.genre.takeIf{it.isNotBlank()}?.let{doc.createParagraph().createRun().setText("题材：$it")};p.summary.takeIf{it.isNotBlank()}?.let{doc.createParagraph().createRun().setText("简介：$it")};cs.forEach{c->doc.createParagraph().apply{style="Heading1";createRun().setText("第${c.number}章 ${c.title}")};c.content.lines().forEach{line->doc.createParagraph().createRun().setText(line)}};notes.filter{it.sourceUrl.isNotBlank()}.takeIf{it.isNotEmpty()}?.let{sources->doc.createParagraph().apply{style="Heading1";createRun().setText("资料来源")};sources.forEach{doc.createParagraph().createRun().setText("${it.title}：${it.sourceUrl}")}};Files.newOutputStream(path).use(doc::write)}}
    private fun writeEpub(path:Path,p:NovelProject,cs:List<Chapter>,notes:List<ResearchNote>){ZipOutputStream(Files.newOutputStream(path)).use{z->fun put(name:String,text:String){z.putNextEntry(ZipEntry(name));z.write(text.toByteArray(Charsets.UTF_8));z.closeEntry()};val mimetype="application/epub+zip".toByteArray(Charsets.US_ASCII);z.putNextEntry(ZipEntry("mimetype").apply{method=ZipEntry.STORED;size=mimetype.size.toLong();compressedSize=size;crc=CRC32().apply{update(mimetype)}.value});z.write(mimetype);z.closeEntry();put("META-INF/container.xml","""<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""");val sources=notes.filter{it.sourceUrl.isNotBlank()};val manifest=cs.joinToString(""){"<item id=\"c${it.number}\" href=\"chapter${it.number}.xhtml\" media-type=\"application/xhtml+xml\"/>"}+(if(sources.isNotEmpty())"<item id=\"references\" href=\"references.xhtml\" media-type=\"application/xhtml+xml\"/>" else "");val spine=cs.joinToString(""){"<itemref idref=\"c${it.number}\"/>"}+(if(sources.isNotEmpty())"<itemref idref=\"references\"/>" else "");put("OEBPS/content.opf","""<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book">novelcraft-${p.id}</dc:identifier><dc:title>${escape(p.title)}</dc:title><dc:language>zh-CN</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$manifest</manifest><spine>$spine</spine></package>""");put("OEBPS/nav.xhtml",xhtml(p.title,"<nav epub:type=\"toc\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><ol>${cs.joinToString(""){"<li><a href=\"chapter${it.number}.xhtml\">第${it.number}章 ${escape(it.title)}</a></li>"}}</ol></nav>"));cs.forEach{c->put("OEBPS/chapter${c.number}.xhtml",xhtml("第${c.number}章 ${c.title}","<h1>第${c.number}章 ${escape(c.title)}</h1>${c.content.lines().filter{it.isNotBlank()}.joinToString(""){"<p>${escape(it)}</p>"}}"))};if(sources.isNotEmpty())put("OEBPS/references.xhtml",xhtml("资料来源","<h1>资料来源</h1><ol>${sources.joinToString(""){"<li>${escape(it.title)}：<a href=\"${escape(it.sourceUrl)}\">${escape(it.sourceUrl)}</a></li>"}}</ol>"))}}
    private fun writePdf(path:Path,p:NovelProject,cs:List<Chapter>,notes:List<ResearchNote>){PDDocument().use{doc->val fontPath=Path.of(System.getenv("WINDIR")?:"C:\\Windows","Fonts","msyh.ttc");TrueTypeCollection(fontPath.toFile()).use{collection->var selected:TrueTypeFont?=null;collection.processAllFonts{candidate->if(selected==null)selected=candidate};val font=PDType0Font.load(doc,requireNotNull(selected){"系统微软雅黑字体不可用"},true);val lines=(listOf(p.title,"")+p.summary.takeIf{it.isNotBlank()}?.let{listOf(it,"")}.orEmpty()+cs.flatMap{listOf("第${it.number}章 ${it.title}","")+it.content.lines()+""}+notes.filter{it.sourceUrl.isNotBlank()}.takeIf{it.isNotEmpty()}?.let{listOf("资料来源")+it.map{note->"${note.title}：${note.sourceUrl}"}}.orEmpty());var page=PDPage();doc.addPage(page);var stream=PDPageContentStream(doc,page);var y=790f;fun newPage(){stream.close();page=PDPage();doc.addPage(page);stream=PDPageContentStream(doc,page);y=790f};lines.flatMap{line->if(line.isEmpty())listOf("")else line.chunked(36)}.forEach{line->if(y<60)newPage();stream.beginText();stream.setFont(font,if(line==p.title)18f else 11f);stream.newLineAtOffset(48f,y);stream.showText(line);stream.endText();y-=18};stream.close();doc.save(path.toFile())}}}
    private fun xhtml(title: String, body: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>${escape(title)}</title><meta charset=\"UTF-8\"/></head><body>$body</body></html>"
    private fun escape(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
}
