package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.ChapterLifecycleJobStatus
import com.mozhou.novelcraft.core.StorySearch
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopDatabaseTest {
    @Test fun freshDatabaseStartsAtCloudSyncSchemaVersionAndAssignsStableSyncId() {
        val dir = Files.createTempDirectory("noveledit-schema")
        DesktopDatabase(dir.resolve("test.db")).use { db ->
            val project = db.createProject("同步", "测试", "测试数据")
            val stored = requireNotNull(db.project(project))
            assertTrue(stored.syncId.matches(Regex("[a-f0-9]{32}")))
        }
    }

    @Test fun importAnalysisRunPersistsAndInterruptedWorkIsRecoverable() {
        val dir = Files.createTempDirectory("novelcraft-import-analysis")
        val database = dir.resolve("test.db")
        val project = DesktopDatabase(database).use { db ->
            val id = db.createProject("导入", "待分类", "导入正文")
            db.saveImportAnalysisRun(com.mozhou.novelcraft.core.ImportAnalysisRun(id, "running", "提炼作品资料", 45, "处理中"))
            id
        }
        DesktopDatabase(database).use { db ->
            val recovered = requireNotNull(db.importAnalysisRun(project))
            assertEquals("cancelled", recovered.status)
            assertEquals(45, recovered.progress)
            assertTrue(recovered.detail.contains("可随时重新开始"))
        }
    }

    @Test fun workspaceLayoutPersistsWidthAndCollapsedPanels() {
        val dir = Files.createTempDirectory("novelcraft-workspace-layout")
        val paths = AppPaths(dir, dir.resolve("test.db"), dir.resolve("covers"), dir.resolve("recovery"), true)
        val layout = WorkspaceLayout(320f, 380f, chapterTreeCollapsed = true, aiPanelCollapsed = true)
        WorkspaceLayoutStore(paths).save(layout)
        assertEquals(layout, WorkspaceLayoutStore(paths).load())
    }

    @Test fun ideationDraftPersistsAcrossDatabaseRestart() {
        val dir = Files.createTempDirectory("novelcraft-ideation")
        val database = dir.resolve("test.db")
        val saved = DesktopDatabase(database).use { db ->
            db.saveIdeationDraft(
                com.mozhou.novelcraft.core.IdeationDraft(
                    step = 3,
                    title = "持久灵感",
                    genre = "悬疑",
                    premise = "一封延迟十年的来信",
                    protagonist = "林舟",
                    conflict = "真相会摧毁现在的生活",
                    promise = "每章揭开一层旧案",
                    targetAudience = "悬疑读者",
                    writingStyle = "克制紧凑",
                    forbiddenContent = "无",
                    automationLevel = "半自动",
                    targetChapterWordCount = 2800,
                    targetChapterWordCountMax = 4200,
                    targetWordCount = 300000,
                ),
            )
        }
        DesktopDatabase(database).use { db ->
            assertEquals(saved, db.latestIdeationDraft())
            db.deleteIdeationDraft(saved.id)
            assertEquals(null, db.latestIdeationDraft())
        }
    }

    @Test fun projectChapterRevisionAndCascade() {
        val dir=Files.createTempDirectory("novelcraft-db")
        DesktopDatabase(dir.resolve("test.db")).use { db ->
            val projectId=db.createProject("测试书","玄幻","测试设定")
            val first=db.chapters(projectId).single()
            db.saveChapter(first.copy(content="第一版"))
            db.saveChapter(first.copy(content="第二版"))
            assertTrue(db.revisions(first.id).isNotEmpty())
            db.addStoryItem(projectId,"人物","林舟","主角")
            assertEquals(1,db.storyItems(projectId).size)
            db.deleteProject(projectId)
            assertTrue(db.chapters(projectId).isEmpty())
        }
    }

    @Test fun backupRoundTripRemapsIds() {
        val dir=Files.createTempDirectory("novelcraft-backup")
        val paths=AppPaths(dir,dir.resolve("test.db"),dir.resolve("covers"),dir.resolve("recovery"),true)
        Files.createDirectories(paths.covers);Files.createDirectories(paths.recovery)
        DesktopDatabase(paths.database).use { db ->
            val source=db.createProject("迁移测试","科幻","跨平台备份")
            val chapter=db.chapters(source).single()
            db.saveChapter(chapter.copy(content="需要完整保留的正文"))
            db.addStoryItem(source,"人物","测试员","检查迁移")
            val file=dir.resolve("backup.json")
            val codec=ProjectBackup(db,paths)
            codec.export(source,file)
            val restored=codec.import(file)
            assertNotEquals(source,restored)
            assertEquals("需要完整保留的正文",db.chapters(restored).single().content)
            assertEquals("测试员",db.storyItems(restored).single().name)
        }
    }

    @Test fun graphCrudCascadesFromDeletedItem() {
        val dir = Files.createTempDirectory("novelcraft-graph")
        DesktopDatabase(dir.resolve("test.db")).use { db ->
            val project = db.createProject("图谱", "测试", "设定")
            val source = db.addStoryItem(project, "人物", "甲", "角色")
            val target = db.addStoryItem(project, "地点", "乙", "场景")
            val edge = db.addEdge(project, source, target, "认识", "", 1)
            assertEquals(1, db.edges(project).size)
            db.deleteStoryItem(source)
            assertTrue(db.storyItems(project).none { it.id == source })
            assertTrue(db.edges(project).none { it.id == edge })
        }
    }

    @Test fun lifecycleJobCanBeClaimedAndRequeued() {
        val dir = Files.createTempDirectory("novelcraft-lifecycle")
        DesktopDatabase(dir.resolve("test.db")).use { db ->
            val project = db.createProject("闭环", "测试", "设定")
            val chapter = db.chapters(project).single()
            val saved = db.chapter(chapter.id)!!.copy(content = "一段足够用于测试生命周期持久化的正文内容")
            db.saveChapter(saved)
            val queued = db.enqueueLifecycle(saved)
            assertEquals(ChapterLifecycleJobStatus.QUEUED, db.lifecycleJob(saved.id)?.status)
            val claimed = requireNotNull(db.claimLifecycle(queued))
            assertEquals(ChapterLifecycleJobStatus.RUNNING, claimed.status)
            db.requeueLifecycle(claimed, "暂停")
            assertEquals(ChapterLifecycleJobStatus.QUEUED, db.lifecycleJob(saved.id)?.status)
            val second = requireNotNull(db.claimLifecycle(requireNotNull(db.nextQueuedLifecycle(project))))
            db.finishLifecycle(second, true, "通过")
            assertEquals(ChapterLifecycleJobStatus.COMPLETED, db.lifecycleJob(saved.id)?.status)
        }
    }

    @Test fun resourcesPacingAndStyleProfilesRoundTrip() {
        val dir = Files.createTempDirectory("novelcraft-resources")
        DesktopDatabase(dir.resolve("test.db")).use { db ->
            val project = db.createProject("资料", "测试", "设定")
            val chapter = db.chapters(project).single()
            val anchorId = db.addAnchor(project, 1, 5, "第一幕", "主角必须做出选择", "调查", "最终真相", "持续压力")
            val anchor = db.anchors(project).single { it.id == anchorId }
            db.updateAnchor(anchor.copy(title = "开局"))
            assertEquals("开局", db.anchors(project).single().title)
            val noteId = db.addNote(project, "资料来源", "初始内容", "https://example.test/source", "调研,测试", rightsConfirmed = true)
            db.updateNote(db.notes(project).single { it.id == noteId }.copy(content = "更新内容", tags = "测试"))
            db.notes(project).single().let { note ->
                assertEquals("更新内容", note.content)
                assertEquals("https://example.test/source", note.sourceUrl)
                assertEquals("测试", note.tags)
                assertTrue(note.rightsConfirmed)
            }
            db.savePacingEvent(com.mozhou.novelcraft.core.ChapterPacingEvent(projectId = project, chapterId = chapter.id, chapterNumber = chapter.number, eventType = "冲突", pace = "快", note = "测试"))
            assertEquals(1, db.pacingEvents(project).size)
            db.addEventMatrixRule(project, "custom", "反转", 3, "节奏")
            val matrixRule = db.eventMatrixRules(project).single()
            db.updateEventMatrixRule(matrixRule.copy(label = "伏笔回收", cooldown = 5, enabled = false))
            db.eventMatrixRules(project).single().let { updated ->
                assertEquals("伏笔回收", updated.label)
                assertEquals(5, updated.cooldown)
                assertFalse(updated.enabled)
            }
            db.saveStyleProfile(com.mozhou.novelcraft.core.StyleProfile(name = "测试文风", genre = "测试", guide = "短句", sourceProjectId = project))
            assertEquals("测试文风", db.styleProfiles(project).single().name)
        }
    }

    @Test fun v2BackupPreservesRelationsCoverAndOperationalData() {
        val dir = Files.createTempDirectory("novelcraft-v2-backup")
        val paths = AppPaths(dir, dir.resolve("test.db"), dir.resolve("covers"), dir.resolve("recovery"), true)
        Files.createDirectories(paths.covers); Files.createDirectories(paths.recovery)
        DesktopDatabase(paths.database).use { db ->
            val project = db.createProject("完整备份", "奇幻", "跨平台数据")
            val first = db.chapters(project).single().copy(content = "第一章正文。\n\n这是一段用于 RAG 的内容。\n\n角色在城门相遇。")
            db.saveChapter(first)
            val second = db.chapter(db.addChapter(project))!!.copy(content = "第二章正文，继续推进冲突。")
            db.saveChapter(second)
            val hero = db.addStoryItem(project, "人物", "林舟", "主角")
            val city = db.addStoryItem(project, "地点", "北城", "故事起点")
            db.addEdge(project, hero, city, "居住于", "", 1)
            db.addAnchor(project, 1, 8, "开局", "寻找失踪者", "调查", "真相", "危机")
            db.addNote(project, "研究", "公开资料摘录", "https://example.test/research", "公开资料", rightsConfirmed = true)
            db.addEditorialReview(project, first.id, "P1：节奏可再加强")
            db.savePacingEvent(com.mozhou.novelcraft.core.ChapterPacingEvent(projectId = project, chapterId = first.id, chapterNumber = first.number, eventType = "冲突", pace = "中", note = "首次对抗"))
            db.addEventMatrixRule(project, "reversal", "反转", 3, "节奏")
            db.saveStyleProfile(com.mozhou.novelcraft.core.StyleProfile(name = "叙述文风", genre = "奇幻", guide = "克制描写", sourceProjectId = project))
            db.rebuildRagChunks(first)
            db.saveContinuitySnapshot(com.mozhou.novelcraft.core.ChapterContinuitySnapshot(first.id, project, 0, "", "连续性上下文"))
            db.addGateReport(com.mozhou.novelcraft.core.ChapterGateReport(projectId = project, chapterId = first.id, stage = "本地门禁", passed = true, content = "PASS"))
            val run = db.createAutoWriteRun(project, 2)
            db.updateAutoWriteRun(run, 1, "paused", "等待恢复")
            db.enqueueLifecycle(first)
            db.addBatchReview(project, 1, 2, 1, "[P1][第1章] 节奏", listOf(com.mozhou.novelcraft.core.ReviewIssue(projectId = project, chapterNumber = 1, severity = "P1", summary = "节奏")))
            val cover = byteArrayOf(1, 2, 3, 4, 5)
            val coverFile = paths.covers.resolve("source-cover.jpg")
            Files.write(coverFile, cover)
            db.updateProject(requireNotNull(db.project(project)).copy(coverPath = coverFile.toString()))

            val backup = dir.resolve("complete.novelcraft.json")
            val codec = ProjectBackup(db, paths)
            codec.export(project, backup)
            val restored = codec.import(backup)
            assertEquals(2, db.chapters(restored).size)
            assertEquals(2, db.storyItems(restored).size)
            assertEquals(1, db.edges(restored).size)
            assertEquals(1, db.anchors(restored).size)
            assertEquals(1, db.notes(restored).size)
            db.notes(restored).single().let { note ->
                assertEquals("https://example.test/research", note.sourceUrl)
                assertEquals("公开资料", note.tags)
                assertTrue(note.rightsConfirmed)
            }
            assertEquals(1, db.editorialReviews(restored, db.chapters(restored).first().id).size)
            assertTrue(db.ragChunks(restored).isNotEmpty())
            assertTrue(db.continuitySnapshot(db.chapters(restored).first().id) != null)
            assertTrue(db.gateReports(db.chapters(restored).first().id).isNotEmpty())
            assertEquals(1, db.autoWriteRuns(restored).size)
            assertEquals(1, db.reviewIssues(restored).size)
            assertContentEquals(cover, Files.readAllBytes(java.nio.file.Path.of(requireNotNull(db.project(restored)).coverPath)))
        }
    }

    @Test fun androidV1BackupAliasesImportAndRemapForeignKeys() {
        val dir = Files.createTempDirectory("novelcraft-v1-backup")
        val paths = AppPaths(dir, dir.resolve("test.db"), dir.resolve("covers"), dir.resolve("recovery"), true)
        Files.createDirectories(paths.covers); Files.createDirectories(paths.recovery)
        DesktopDatabase(paths.database).use { db ->
            val now = System.currentTimeMillis()
            val project = JSONObject().put("id", 9).put("title", "旧版作品").put("genre", "悬疑").put("premise", "旧备份").put("styleGuide", "").put("outlineRevisionReport", "").put("summary", "").put("tags", "").put("targetAudience", "").put("protagonistName", "").put("longFormBlueprint", "").put("targetChapterCount", 0).put("targetWordCount", 0).put("pacingProfile", "均衡").put("forbiddenContent", "").put("automationLevel", "半自动").put("targetChapterWordCount", 3000).put("targetChapterWordCountMax", 5000).put("coverPath", "").put("createdAt", now).put("updatedAt", now)
            val chapter = JSONObject().put("id", 101).put("projectId", 9).put("number", 1).put("title", "第一章").put("content", "旧版正文").put("updatedAt", now)
            val itemA = JSONObject().put("id", 201).put("projectId", 9).put("kind", "人物").put("name", "甲").put("detail", "主角").put("updatedAt", now)
            val itemB = JSONObject().put("id", 202).put("projectId", 9).put("kind", "地点").put("name", "乙").put("detail", "现场").put("updatedAt", now)
            val edge = JSONObject().put("id", 301).put("projectId", 9).put("sourceItemId", 201).put("targetItemId", 202).put("relation", "出现于").put("strength", 0.5).put("description", "").put("sinceChapter", 1)
            val root = JSONObject().put("format", "novelcraft-project-backup").put("version", 1).put("project", project).put("chapters", JSONArray().put(chapter)).put("items", JSONArray().put(itemA).put(itemB)).put("edges", JSONArray().put(edge)).put("notes", JSONArray()).put("anchors", JSONArray()).put("revisions", JSONArray()).put("autoWriteRuns", JSONArray()).put("mentions", JSONArray()).put("editorialReviews", JSONArray()).put("pacingEvents", JSONArray()).put("batchReviews", JSONArray()).put("reviewIssues", JSONArray()).put("styleProfiles", JSONArray()).put("eventMatrixRules", JSONArray()).put("gateReports", JSONArray())
            val file = dir.resolve("android-v1.json")
            Files.writeString(file, root.toString())
            val imported = ProjectBackup(db, paths).import(file)
            assertEquals("旧版正文", db.chapters(imported).single().content)
            assertEquals(2, db.storyItems(imported).size)
            val importedEdge = db.edges(imported).single()
            assertTrue(db.storyItems(imported).any { it.id == importedEdge.sourceItemId })
            assertTrue(db.storyItems(imported).any { it.id == importedEdge.targetItemId })
        }
    }

    @Test fun interruptedJobsAreRecoveredOnDatabaseRestart() {
        val dir = Files.createTempDirectory("novelcraft-recovery")
        val database = dir.resolve("test.db")
        var projectId = 0L
        var chapterId = 0L
        DesktopDatabase(database).use { db ->
            projectId = db.createProject("恢复", "测试", "设定")
            val chapter = db.chapters(projectId).single().copy(content = "用于恢复测试的正文", lifecycleStatus = com.mozhou.novelcraft.core.ChapterLifecycleStatus.PROCESSING)
            db.saveChapter(chapter)
            chapterId = chapter.id
            val job = db.enqueueLifecycle(chapter)
            db.claimLifecycle(job)
            val run = db.createAutoWriteRun(projectId, 2)
            db.updateAutoWriteRun(run, 0, "running", "进行中")
        }
        DesktopDatabase(database).use { db ->
            assertEquals(ChapterLifecycleJobStatus.FAILED, db.lifecycleJob(chapterId)?.status)
            assertEquals(com.mozhou.novelcraft.core.ChapterLifecycleStatus.MEMORY_FAILED, db.chapter(chapterId)?.lifecycleStatus)
            assertEquals("paused", db.resumableAutoWriteRun(projectId)?.status)
        }
    }

    @Test fun largeProjectSearchAndRagSmoke() {
        val dir = Files.createTempDirectory("novelcraft-large-project")
        DesktopDatabase(dir.resolve("test.db")).use { db ->
            val project = db.createProject("长篇", "测试", "百万字项目")
            val body = "主角在雨夜穿过旧城，线索仍未揭开。".repeat(180)
            repeat(300) { index ->
                val chapter = if (index == 0) db.chapters(project).single() else db.chapter(db.addChapter(project))!!
                val saved = chapter.copy(title = "第${index + 1}章", content = body + "关键标记${index + 1}")
                db.saveChapter(saved)
                db.rebuildRagChunks(saved)
            }
            val chapters = db.chapters(project)
            assertEquals(300, chapters.size)
            assertTrue(StorySearch.find(chapters, "关键标记300").isNotEmpty())
            assertTrue(db.ragChunks(project).isNotEmpty())
        }
    }
}
