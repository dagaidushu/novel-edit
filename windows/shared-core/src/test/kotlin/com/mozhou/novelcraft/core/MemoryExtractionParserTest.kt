package com.mozhou.novelcraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractionParserTest {
    @Test
    fun parsesStructuredItemsAndRelationships() {
        val extraction = MemoryExtractionParser.parse(
            """{"items":[{"kind":"人物","name":"沈舟","detail":"在旧港调查姐姐失踪","status":"活跃"}],"edges":[{"source":"沈舟","target":"月契","relation":"持有","description":"本章发现"}]}""",
        )

        assertEquals("沈舟", extraction.items.single().name)
        assertEquals(StoryItemStatus.ACTIVE, extraction.items.single().status)
        assertTrue(extraction.edges.single().relation == "持有")
    }

    @Test
    fun rejectsIncompleteGraphEntries() {
        val extraction = MemoryExtractionParser.parse("""{"items":[{"name":"无类型"}],"edges":[{"source":"甲","target":"甲","relation":"同盟"}]}""")

        assertTrue(extraction.items.isEmpty())
        assertTrue(extraction.edges.isEmpty())
    }
}

