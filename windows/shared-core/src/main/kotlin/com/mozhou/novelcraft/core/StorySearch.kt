package com.mozhou.novelcraft.core

data class ChapterSearchResult(val chapter: Chapter, val preview: String, val count: Int)

object StorySearch {
    fun find(chapters: List<Chapter>, query: String): List<ChapterSearchResult> {
        val term = query.trim()
        if (term.length < 2) return emptyList()
        return chapters.mapNotNull { chapter ->
            val haystack = "${chapter.title}\n${chapter.content}"
            val count = Regex(Regex.escape(term), RegexOption.IGNORE_CASE).findAll(haystack).count()
            if (count == 0) null else {
                val at = haystack.indexOf(term, ignoreCase = true).coerceAtLeast(0)
                ChapterSearchResult(chapter, haystack.substring((at - 48).coerceAtLeast(0), (at + term.length + 96).coerceAtMost(haystack.length)).replace("\n", " "), count)
            }
        }.sortedWith(compareByDescending<ChapterSearchResult> { it.count }.thenBy { it.chapter.number })
    }
}

