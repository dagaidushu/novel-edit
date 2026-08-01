package com.mozhou.novelcraft.core

internal const val MIN_GENERATED_CHAPTER_CHARS = 4_000
internal const val MAX_OPENING_CHAPTER_GENERATION_ATTEMPTS = 4

internal data class ChapterWordRange(val min: Int, val max: Int)

internal fun normalizeChapterWordRange(min: Int, max: Int): ChapterWordRange {
    val normalizedMin = if (min > 0) min.coerceIn(3_000, 20_000) else 3_000
    val normalizedMax = if (max > 0) max.coerceIn(normalizedMin, 20_000) else 5_000.coerceAtLeast(normalizedMin)
    return ChapterWordRange(normalizedMin, normalizedMax)
}

/** Generated prose is stored as prose, never as a Markdown response. */
internal fun sanitizeNovelBody(raw: String): String = raw.lineSequence()
    .mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) null
        else line.replace(Regex("^\\s*#{1,6}\\s*"), "")
            .replace(Regex("^\\s*>\\s?"), "")
            .trimEnd()
    }
    .joinToString("\n")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

internal fun needsChapterExpansion(content: String): Boolean = content.trim().length < MIN_GENERATED_CHAPTER_CHARS

internal fun mergeOpeningChapterContent(openingDraft: String, currentContent: String, generatedContent: String): String =
    if (currentContent.trim() == openingDraft.trim()) generatedContent.trim()
    else currentContent.trimEnd() + "\n\n" + generatedContent.trimStart()

internal fun appendGeneratedChapterContent(currentContent: String, generatedContent: String): String =
    if (currentContent.isBlank()) generatedContent.trim()
    else currentContent.trimEnd() + "\n\n" + generatedContent.trimStart()

internal fun shouldGenerateChapterTitle(currentTitle: String, currentContent: String): Boolean =
    currentContent.isBlank() && currentTitle.trim().matches(Regex("^第\\s*\\d+\\s*章$"))

internal fun chapterTitleOrFallback(raw: String, fallback: String): String {
    val title = raw.lineSequence().firstOrNull().orEmpty()
        .removePrefix("#")
        .replace(Regex("^第\\s*\\d+\\s*章[：:、.-]*"), "")
        .trim()
        .trim('"', '“', '”', '《', '》')
        .take(24)
    return title.ifBlank { fallback }
}

