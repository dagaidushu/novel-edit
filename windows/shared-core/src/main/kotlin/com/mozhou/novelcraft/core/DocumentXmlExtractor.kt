package com.mozhou.novelcraft.core

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object DocumentTextExtractor {
    fun extractDocumentXml(xml:String):String {
        val factory=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}
        val document=factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val paragraphs=document.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","p")
        return buildList { for(i in 0 until paragraphs.length){val texts=(paragraphs.item(i) as org.w3c.dom.Element).getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","t");add(buildString{for(j in 0 until texts.length)append(texts.item(j).textContent)})} }.filter(String::isNotBlank).joinToString("\n")
    }
}
