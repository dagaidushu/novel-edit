package com.mozhou.novelcraft.core

data class ImportedChapter(
    val number: Int,
    val title: String,
    val content: String,
)

object ChapterImporter {
    private val chapterHeading = Regex(
        """^\s*(?:#{1,6}\s*)?第\s*([0-9一二三四五六七八九十百千零两]+)\s*章\s*([^\n]*)\s*$""",
        RegexOption.MULTILINE,
    )

    fun parse(source: String): List<ImportedChapter> {
        val matches = chapterHeading.findAll(source).toList()
        if (matches.isEmpty()) {
            return listOf(ImportedChapter(1, "导入正文", source.trim()))
        }

        return matches.mapIndexed { index, match ->
            val chapterNumber = parseNumber(match.groupValues[1]) ?: index + 1
            val title = match.groupValues[2].trim().ifBlank { "第" + chapterNumber + "章" }
            val bodyStart = match.range.last + 1
            val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: source.length
            ImportedChapter(chapterNumber, title, source.substring(bodyStart, bodyEnd).trim())
        }
    }

    private fun parseNumber(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        val units = mapOf('十' to 10, '百' to 100, '千' to 1_000)
        var result = 0
        var current = 0
        value.forEach { char ->
            when {
                char in digits -> current = digits.getValue(char)
                char in units -> {
                    result += (if (current == 0) 1 else current) * units.getValue(char)
                    current = 0
                }
                else -> return null
            }
        }
        return (result + current).takeIf { it > 0 }
    }
}

