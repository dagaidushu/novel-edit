package com.mozhou.novelcraft.core

import org.junit.Assert.assertTrue
import org.junit.Test

class PacingGuardTest {
    @Test
    fun flags_premature_resolution_before_final_stage() {
        val project = NovelProject(id = 1, title = "测试", genre = "玄幻", premise = "", targetChapterCount = 100)
        val chapter = Chapter(projectId = 1, number = 20, title = "收束", content = "所有敌人败退，一切结束。")

        assertTrue(PacingGuard.inspect(project, chapter).any { it.title == "可能过早收束主线" })
    }
}

