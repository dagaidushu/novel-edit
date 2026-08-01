package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.*
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class DesktopDatabase(path: Path) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")

    init {
        connection.createStatement().use {
            it.execute("PRAGMA foreign_keys=ON")
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA busy_timeout=5000")
        }
        migrate()
        recoverInterruptedJobs()
    }

    private fun migrate() = transaction {
        val version = queryInt("PRAGMA user_version")
        if (version < 1) {
            execute("""
                CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,genre TEXT NOT NULL,premise TEXT NOT NULL,styleGuide TEXT NOT NULL DEFAULT '',outlineRevisionReport TEXT NOT NULL DEFAULT '',summary TEXT NOT NULL DEFAULT '',tags TEXT NOT NULL DEFAULT '',targetAudience TEXT NOT NULL DEFAULT '',protagonistName TEXT NOT NULL DEFAULT '',longFormBlueprint TEXT NOT NULL DEFAULT '',targetChapterCount INTEGER NOT NULL DEFAULT 0,targetWordCount INTEGER NOT NULL DEFAULT 0,pacingProfile TEXT NOT NULL DEFAULT '均衡',forbiddenContent TEXT NOT NULL DEFAULT '',automationLevel TEXT NOT NULL DEFAULT '半自动',targetChapterWordCount INTEGER NOT NULL DEFAULT 3000,targetChapterWordCountMax INTEGER NOT NULL DEFAULT 5000,coverPath TEXT NOT NULL DEFAULT '',createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,syncId TEXT NOT NULL DEFAULT '');
                CREATE TABLE chapters(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,number INTEGER NOT NULL,title TEXT NOT NULL,content TEXT NOT NULL DEFAULT '',outline TEXT NOT NULL DEFAULT '',beatSheet TEXT NOT NULL DEFAULT '',targetWordCount INTEGER NOT NULL DEFAULT 0,qualityStatus TEXT NOT NULL DEFAULT 'ready',qualityIssueSummary TEXT NOT NULL DEFAULT '',lifecycleStatus TEXT NOT NULL DEFAULT 'manual',lifecycleDetail TEXT NOT NULL DEFAULT '',memoryUpdatedAt INTEGER NOT NULL DEFAULT 0,autoWriteRunId INTEGER NOT NULL DEFAULT 0,gateFailureCount INTEGER NOT NULL DEFAULT 0,requiresHumanReview INTEGER NOT NULL DEFAULT 0,updatedAt INTEGER NOT NULL);
                CREATE INDEX chapters_project ON chapters(projectId,number);
                CREATE TABLE chapter_revisions(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,chapterId INTEGER NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,previousContent TEXT NOT NULL,reason TEXT NOT NULL,createdAt INTEGER NOT NULL);
                CREATE TABLE story_items(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,kind TEXT NOT NULL,name TEXT NOT NULL,detail TEXT NOT NULL,status TEXT NOT NULL DEFAULT '活跃',updatedAt INTEGER NOT NULL,cascadePending INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE story_anchors(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,startChapter INTEGER NOT NULL,endChapter INTEGER NOT NULL,title TEXT NOT NULL,coreConflict TEXT NOT NULL,allowedPlot TEXT NOT NULL DEFAULT '',forbiddenReveals TEXT NOT NULL DEFAULT '',mandatoryTension TEXT NOT NULL DEFAULT '',cascadePending INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE story_edges(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,sourceItemId INTEGER NOT NULL,targetItemId INTEGER NOT NULL,relation TEXT NOT NULL,strength REAL NOT NULL DEFAULT .5,description TEXT NOT NULL DEFAULT '',sinceChapter INTEGER NOT NULL DEFAULT 1,cascadePending INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE research_notes(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,title TEXT NOT NULL,sourceUrl TEXT NOT NULL DEFAULT '',tags TEXT NOT NULL DEFAULT '',content TEXT NOT NULL,rightsConfirmed INTEGER NOT NULL DEFAULT 0,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL);
                CREATE TABLE editorial_reviews(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,chapterId INTEGER NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,content TEXT NOT NULL,createdAt INTEGER NOT NULL);
                CREATE TABLE style_profiles(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,genre TEXT NOT NULL DEFAULT '',guide TEXT NOT NULL,sourceProjectId INTEGER NOT NULL DEFAULT 0,metrics TEXT NOT NULL DEFAULT '',keywords TEXT NOT NULL DEFAULT '',createdAt INTEGER NOT NULL);
                CREATE TABLE chapter_pacing_events(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,chapterId INTEGER NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,chapterNumber INTEGER NOT NULL,eventType TEXT NOT NULL,pace TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',createdAt INTEGER NOT NULL);
                CREATE TABLE event_matrix_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,ruleKey TEXT NOT NULL,label TEXT NOT NULL,cooldown INTEGER NOT NULL,category TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1,createdAt INTEGER NOT NULL);
                CREATE TABLE chapter_gate_reports(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,chapterId INTEGER NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,stage TEXT NOT NULL,passed INTEGER NOT NULL,content TEXT NOT NULL,contextSnapshot TEXT NOT NULL DEFAULT '',createdAt INTEGER NOT NULL);
                CREATE TABLE batch_review_runs(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,startChapter INTEGER NOT NULL,endChapter INTEGER NOT NULL,round INTEGER NOT NULL DEFAULT 1,report TEXT NOT NULL,createdAt INTEGER NOT NULL);
                CREATE TABLE review_issues(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,reviewRunId INTEGER NOT NULL DEFAULT 0,chapterNumber INTEGER NOT NULL DEFAULT 0,severity TEXT NOT NULL,summary TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'open',createdAt INTEGER NOT NULL);
                CREATE TABLE rag_chunks(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL,chapterId INTEGER NOT NULL,chapterNumber INTEGER NOT NULL,ordinal INTEGER NOT NULL,content TEXT NOT NULL,terms TEXT NOT NULL,updatedAt INTEGER NOT NULL);
                CREATE TABLE chapter_continuity_snapshots(chapterId INTEGER PRIMARY KEY,projectId INTEGER NOT NULL,predecessorChapterId INTEGER NOT NULL DEFAULT 0,predecessorTail TEXT NOT NULL DEFAULT '',contextPrompt TEXT NOT NULL,confirmationStatus TEXT NOT NULL DEFAULT 'confirmed',createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL);
                CREATE TABLE chapter_lifecycle_jobs(chapterId INTEGER PRIMARY KEY,projectId INTEGER NOT NULL,contentFingerprint TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'queued',attempts INTEGER NOT NULL DEFAULT 0,detail TEXT NOT NULL,afterSuccessAction TEXT NOT NULL DEFAULT '',createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL);
                CREATE TABLE auto_write_runs(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,requestedCount INTEGER NOT NULL,completedCount INTEGER NOT NULL DEFAULT 0,status TEXT NOT NULL,detail TEXT NOT NULL DEFAULT '',createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL);
                CREATE TABLE import_analysis_runs(projectId INTEGER PRIMARY KEY,status TEXT NOT NULL,stage TEXT NOT NULL,progress INTEGER NOT NULL,detail TEXT NOT NULL,updatedAt INTEGER NOT NULL);
                CREATE TABLE chapter_story_mentions(id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL,chapterId INTEGER NOT NULL,storyItemId INTEGER NOT NULL,createdAt INTEGER NOT NULL);
                CREATE TABLE ideation_drafts(id INTEGER PRIMARY KEY AUTOINCREMENT,step INTEGER NOT NULL,title TEXT NOT NULL,genre TEXT NOT NULL,premise TEXT NOT NULL,protagonist TEXT NOT NULL,conflict TEXT NOT NULL,promise TEXT NOT NULL,targetAudience TEXT NOT NULL,writingStyle TEXT NOT NULL,forbiddenContent TEXT NOT NULL,automationLevel TEXT NOT NULL,targetChapterWordCount INTEGER NOT NULL,targetChapterWordCountMax INTEGER NOT NULL,targetWordCount INTEGER NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL);
                PRAGMA user_version=2;
            """.trimIndent())
        }
        if (version == 1) {
            execute("ALTER TABLE projects ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
            execute("UPDATE projects SET syncId=lower(hex(randomblob(16))) WHERE syncId='' ")
            execute("PRAGMA user_version=2")
        }
    }

    fun projects(): List<NovelProject> = query("SELECT * FROM projects ORDER BY updatedAt DESC", ::project)
    fun project(id: Long): NovelProject? = query("SELECT * FROM projects WHERE id=?", ::project, id).firstOrNull()
    fun latestIdeationDraft(): IdeationDraft? = query("SELECT * FROM ideation_drafts ORDER BY updatedAt DESC,id DESC LIMIT 1", ::ideationDraft).firstOrNull()
    fun saveIdeationDraft(value: IdeationDraft): IdeationDraft = transaction {
        val now = System.currentTimeMillis()
        execute("DELETE FROM ideation_drafts")
        val saved = value.copy(id = 0, createdAt = now, updatedAt = now)
        val id = insert(
            "INSERT INTO ideation_drafts(step,title,genre,premise,protagonist,conflict,promise,targetAudience,writingStyle,forbiddenContent,automationLevel,targetChapterWordCount,targetChapterWordCountMax,targetWordCount,createdAt,updatedAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            saved.step, saved.title, saved.genre, saved.premise, saved.protagonist, saved.conflict, saved.promise,
            saved.targetAudience, saved.writingStyle, saved.forbiddenContent, saved.automationLevel,
            saved.targetChapterWordCount, saved.targetChapterWordCountMax, saved.targetWordCount, saved.createdAt, saved.updatedAt,
        )
        saved.copy(id = id)
    }
    fun deleteIdeationDraft(id: Long) = execute("DELETE FROM ideation_drafts WHERE id=?", id)
    fun chapters(projectId: Long): List<Chapter> = query("SELECT * FROM chapters WHERE projectId=? ORDER BY number", ::chapter, projectId)
    fun chapter(id: Long): Chapter? = query("SELECT * FROM chapters WHERE id=?", ::chapter, id).firstOrNull()
    fun revisions(chapterId: Long): List<ChapterRevision> = query("SELECT * FROM chapter_revisions WHERE chapterId=? ORDER BY createdAt DESC", { r -> ChapterRevision(r.long("id"),r.long("projectId"),r.long("chapterId"),r.string("previousContent"),r.string("reason"),r.long("createdAt")) }, chapterId)
    fun storyItems(projectId: Long): List<StoryItem> = query("SELECT * FROM story_items WHERE projectId=? ORDER BY kind,name", { r -> StoryItem(r.long("id"),r.long("projectId"),r.string("kind"),r.string("name"),r.string("detail"),r.string("status"),r.long("updatedAt"),r.bool("cascadePending")) }, projectId)
    fun anchors(projectId: Long): List<StoryAnchor> = query("SELECT * FROM story_anchors WHERE projectId=? ORDER BY startChapter", { r -> StoryAnchor(r.long("id"),r.long("projectId"),r.int("startChapter"),r.int("endChapter"),r.string("title"),r.string("coreConflict"),r.string("allowedPlot"),r.string("forbiddenReveals"),r.string("mandatoryTension"),r.bool("cascadePending")) }, projectId)
    fun notes(projectId: Long): List<ResearchNote> = query("SELECT * FROM research_notes WHERE projectId=? ORDER BY updatedAt DESC", { r -> ResearchNote(r.long("id"),r.long("projectId"),r.string("title"),r.string("sourceUrl"),r.string("tags"),r.string("content"),r.bool("rightsConfirmed"),r.long("createdAt"),r.long("updatedAt")) }, projectId)
    fun editorialReviews(projectId: Long, chapterId: Long): List<EditorialReview> = query("SELECT * FROM editorial_reviews WHERE projectId=? AND chapterId=? ORDER BY createdAt DESC", { r -> EditorialReview(r.long("id"), r.long("projectId"), r.long("chapterId"), r.string("content"), r.long("createdAt")) }, projectId, chapterId)
    fun batchReviewRuns(projectId: Long): List<BatchReviewRun> = query("SELECT * FROM batch_review_runs WHERE projectId=? ORDER BY createdAt DESC", { r -> BatchReviewRun(r.long("id"),r.long("projectId"),r.int("startChapter"),r.int("endChapter"),r.int("round"),r.string("report"),r.long("createdAt")) }, projectId)
    fun reviewIssues(projectId: Long): List<ReviewIssue> = query("SELECT * FROM review_issues WHERE projectId=? ORDER BY CASE severity WHEN 'P0' THEN 0 WHEN 'P1' THEN 1 ELSE 2 END,createdAt DESC", { r -> ReviewIssue(r.long("id"),r.long("projectId"),r.long("reviewRunId"),r.int("chapterNumber"),r.string("severity"),r.string("summary"),r.string("status"),r.long("createdAt")) }, projectId)
    fun pacingEvents(projectId: Long): List<ChapterPacingEvent> = query("SELECT * FROM chapter_pacing_events WHERE projectId=? ORDER BY chapterNumber,id", { r -> ChapterPacingEvent(r.long("id"),r.long("projectId"),r.long("chapterId"),r.int("chapterNumber"),r.string("eventType"),r.string("pace"),r.string("note"),r.long("createdAt")) }, projectId)
    fun eventMatrixRules(projectId: Long): List<EventMatrixRule> = query("SELECT * FROM event_matrix_rules WHERE projectId=? ORDER BY category,label", { r -> EventMatrixRule(r.long("id"),r.long("projectId"),r.string("ruleKey"),r.string("label"),r.int("cooldown"),r.string("category"),r.bool("enabled"),r.long("createdAt")) }, projectId)
    fun styleProfiles(projectId: Long): List<StyleProfile> = query("SELECT * FROM style_profiles WHERE sourceProjectId=? ORDER BY createdAt DESC", { r -> StyleProfile(r.long("id"),r.string("name"),r.string("genre"),r.string("guide"),r.long("sourceProjectId"),r.string("metrics"),r.string("keywords"),r.long("createdAt")) }, projectId)
    fun autoWriteRuns(projectId: Long): List<AutoWriteRun> = query("SELECT * FROM auto_write_runs WHERE projectId=? ORDER BY updatedAt DESC", ::autoWriteRun, projectId)
    fun resumableAutoWriteRun(projectId: Long): AutoWriteRun? = query("SELECT * FROM auto_write_runs WHERE projectId=? AND status IN ('running','paused') ORDER BY updatedAt DESC LIMIT 1", ::autoWriteRun, projectId).firstOrNull()
    fun importAnalysisRun(projectId: Long): ImportAnalysisRun? = query("SELECT * FROM import_analysis_runs WHERE projectId=?", ::importAnalysisRun, projectId).firstOrNull()
    fun saveImportAnalysisRun(value: ImportAnalysisRun): ImportAnalysisRun {
        val saved = value.copy(progress = value.progress.coerceIn(0, 100), updatedAt = System.currentTimeMillis())
        execute("INSERT OR REPLACE INTO import_analysis_runs(projectId,status,stage,progress,detail,updatedAt) VALUES(?,?,?,?,?,?)", saved.projectId, saved.status, saved.stage, saved.progress, saved.detail, saved.updatedAt)
        return saved
    }
    fun ragChunks(projectId: Long): List<RagChunk> = query("SELECT * FROM rag_chunks WHERE projectId=? ORDER BY chapterNumber,ordinal", { r -> RagChunk(r.long("id"),r.long("projectId"),r.long("chapterId"),r.int("chapterNumber"),r.int("ordinal"),r.string("content"),r.string("terms"),r.long("updatedAt")) }, projectId)
    fun continuitySnapshot(chapterId: Long): ChapterContinuitySnapshot? = query("SELECT * FROM chapter_continuity_snapshots WHERE chapterId=?", { r -> ChapterContinuitySnapshot(r.long("chapterId"),r.long("projectId"),r.long("predecessorChapterId"),r.string("predecessorTail"),r.string("contextPrompt"),r.string("confirmationStatus"),r.long("createdAt"),r.long("updatedAt")) }, chapterId).firstOrNull()
    fun gateReports(chapterId: Long): List<ChapterGateReport> = query("SELECT * FROM chapter_gate_reports WHERE chapterId=? ORDER BY createdAt DESC", { r -> ChapterGateReport(r.long("id"),r.long("projectId"),r.long("chapterId"),r.string("stage"),r.bool("passed"),r.string("content"),r.string("contextSnapshot"),r.long("createdAt")) }, chapterId)
    fun edges(projectId: Long): List<StoryEdge> = query("SELECT * FROM story_edges WHERE projectId=? ORDER BY sinceChapter,id", { r -> StoryEdge(r.long("id"),r.long("projectId"),r.long("sourceItemId"),r.long("targetItemId"),r.string("relation"),r.getFloat("strength"),r.string("description"),r.int("sinceChapter"),r.bool("cascadePending")) }, projectId)

    fun createProject(title: String, genre: String, premise: String): Long = transaction {
        val now = System.currentTimeMillis()
        insert("INSERT INTO projects(title,genre,premise,createdAt,updatedAt,syncId) VALUES(?,?,?,?,?,?)", title, genre, premise, now, now, newSyncId()).also { projectId ->
            insert("INSERT INTO chapters(projectId,number,title,updatedAt) VALUES(?,?,?,?)", projectId, 1, "第1章", now)
        }
    }

    fun updateProject(value: NovelProject) = execute("UPDATE projects SET title=?,genre=?,premise=?,styleGuide=?,summary=?,tags=?,targetAudience=?,protagonistName=?,longFormBlueprint=?,targetChapterCount=?,targetWordCount=?,pacingProfile=?,forbiddenContent=?,automationLevel=?,targetChapterWordCount=?,targetChapterWordCountMax=?,coverPath=?,updatedAt=? WHERE id=?", value.title,value.genre,value.premise,value.styleGuide,value.summary,value.tags,value.targetAudience,value.protagonistName,value.longFormBlueprint,value.targetChapterCount,value.targetWordCount,value.pacingProfile,value.forbiddenContent,value.automationLevel,value.targetChapterWordCount,value.targetChapterWordCountMax,value.coverPath,System.currentTimeMillis(),value.id)
    fun addChapter(projectId: Long): Long { val number=(chapters(projectId).maxOfOrNull { it.number }?:0)+1; return insert("INSERT INTO chapters(projectId,number,title,updatedAt) VALUES(?,?,?,?)",projectId,number,"第${number}章",System.currentTimeMillis()) }
    fun saveChapter(value: Chapter, reason: String = "自动保存") = transaction {
        val old = chapter(value.id)
        if (old != null && old.content != value.content) insert("INSERT INTO chapter_revisions(projectId,chapterId,previousContent,reason,createdAt) VALUES(?,?,?,?,?)",value.projectId,value.id,old.content,reason,System.currentTimeMillis())
        execute("UPDATE chapters SET title=?,content=?,outline=?,beatSheet=?,targetWordCount=?,qualityStatus=?,qualityIssueSummary=?,lifecycleStatus=?,lifecycleDetail=?,memoryUpdatedAt=?,autoWriteRunId=?,gateFailureCount=?,requiresHumanReview=?,updatedAt=? WHERE id=?",value.title,value.content,value.outline,value.beatSheet,value.targetWordCount,value.qualityStatus,value.qualityIssueSummary,value.lifecycleStatus,value.lifecycleDetail,value.memoryUpdatedAt,value.autoWriteRunId,value.gateFailureCount,if(value.requiresHumanReview)1 else 0,System.currentTimeMillis(),value.id)
        execute("UPDATE projects SET updatedAt=? WHERE id=?",System.currentTimeMillis(),value.projectId)
    }
    fun restoreRevision(chapterId: Long, revisionId: Long) = transaction { val c=chapter(chapterId)?:return@transaction; val r=revisions(chapterId).firstOrNull{it.id==revisionId}?:return@transaction; saveChapter(c.copy(content=r.previousContent),"恢复历史版本") }
    fun deleteRevision(chapterId: Long, revisionId: Long) = execute("DELETE FROM chapter_revisions WHERE id=? AND chapterId=?", revisionId, chapterId)
    fun deleteChapter(id: Long) = execute("DELETE FROM chapters WHERE id=?",id)
    fun deleteProject(id: Long) = execute("DELETE FROM projects WHERE id=?",id)
    fun addStoryItem(projectId: Long, kind: String, name: String, detail: String, status: String = StoryItemStatus.ACTIVE) = insert("INSERT INTO story_items(projectId,kind,name,detail,status,updatedAt) VALUES(?,?,?,?,?,?)",projectId,kind,name,detail,status,System.currentTimeMillis())
    fun updateStoryItem(value: StoryItem) = execute("UPDATE story_items SET kind=?,name=?,detail=?,status=?,updatedAt=? WHERE id=?",value.kind,value.name,value.detail,value.status,System.currentTimeMillis(),value.id)
    fun deleteStoryItem(id: Long) = transaction {
        execute("DELETE FROM story_edges WHERE sourceItemId=? OR targetItemId=?", id, id)
        execute("DELETE FROM chapter_story_mentions WHERE storyItemId=?", id)
        execute("DELETE FROM story_items WHERE id=?", id)
    }
    fun addEdge(projectId:Long,sourceItemId:Long,targetItemId:Long,relation:String,description:String,sinceChapter:Int)=insert("INSERT INTO story_edges(projectId,sourceItemId,targetItemId,relation,description,sinceChapter) VALUES(?,?,?,?,?,?)",projectId,sourceItemId,targetItemId,relation,description,sinceChapter)
    fun deleteEdge(id: Long) = execute("DELETE FROM story_edges WHERE id=?", id)
    fun addAnchor(projectId: Long, startChapter: Int, endChapter: Int, title: String, coreConflict: String, allowedPlot: String, forbiddenReveals: String, mandatoryTension: String) = insert(
        "INSERT INTO story_anchors(projectId,startChapter,endChapter,title,coreConflict,allowedPlot,forbiddenReveals,mandatoryTension) VALUES(?,?,?,?,?,?,?,?)",
        projectId, startChapter, endChapter, title, coreConflict, allowedPlot, forbiddenReveals, mandatoryTension,
    )
    fun updateAnchor(value: StoryAnchor) = execute("UPDATE story_anchors SET startChapter=?,endChapter=?,title=?,coreConflict=?,allowedPlot=?,forbiddenReveals=?,mandatoryTension=?,cascadePending=? WHERE id=?", value.startChapter,value.endChapter,value.title,value.coreConflict,value.allowedPlot,value.forbiddenReveals,value.mandatoryTension,if(value.cascadePending)1 else 0,value.id)
    fun deleteAnchor(id: Long) = execute("DELETE FROM story_anchors WHERE id=?", id)
    fun replaceChapterMentions(chapter:Chapter,itemIds:Collection<Long>)=transaction{execute("DELETE FROM chapter_story_mentions WHERE chapterId=?",chapter.id);itemIds.distinct().forEach{itemId->insert("INSERT INTO chapter_story_mentions(projectId,chapterId,storyItemId,createdAt) VALUES(?,?,?,?)",chapter.projectId,chapter.id,itemId,System.currentTimeMillis())}}
    fun addNote(projectId: Long, title: String, content: String, sourceUrl: String = "", tags: String = "", rightsConfirmed: Boolean = false) = insert(
        "INSERT INTO research_notes(projectId,title,sourceUrl,tags,content,rightsConfirmed,createdAt,updatedAt) VALUES(?,?,?,?,?,?,?,?)",
        projectId, title, sourceUrl, tags, content, if (rightsConfirmed) 1 else 0, System.currentTimeMillis(), System.currentTimeMillis(),
    )
    fun updateNote(value: ResearchNote) = execute("UPDATE research_notes SET title=?,sourceUrl=?,tags=?,content=?,rightsConfirmed=?,updatedAt=? WHERE id=?", value.title, value.sourceUrl, value.tags, value.content, if (value.rightsConfirmed) 1 else 0, System.currentTimeMillis(), value.id)
    fun deleteNote(id: Long) = execute("DELETE FROM research_notes WHERE id=?", id)
    fun addEditorialReview(projectId: Long, chapterId: Long, content: String) = insert("INSERT INTO editorial_reviews(projectId,chapterId,content,createdAt) VALUES(?,?,?,?)", projectId, chapterId, content, System.currentTimeMillis())
    fun addBatchReview(projectId: Long, startChapter: Int, endChapter: Int, round: Int, report: String, issues: List<ReviewIssue>): Long = transaction {
        val runId = insert("INSERT INTO batch_review_runs(projectId,startChapter,endChapter,round,report,createdAt) VALUES(?,?,?,?,?,?)", projectId,startChapter,endChapter,round,report.trim(),System.currentTimeMillis())
        issues.forEach { issue -> insert("INSERT INTO review_issues(projectId,reviewRunId,chapterNumber,severity,summary,status,createdAt) VALUES(?,?,?,?,?,?,?)", projectId,runId,issue.chapterNumber,issue.severity,issue.summary,issue.status,System.currentTimeMillis()) }
        runId
    }
    fun updateReviewIssueStatus(id: Long, status: String) = execute("UPDATE review_issues SET status=? WHERE id=?", status, id)
    fun savePacingEvent(value: ChapterPacingEvent) = transaction {
        execute("DELETE FROM chapter_pacing_events WHERE chapterId=?", value.chapterId)
        insert("INSERT INTO chapter_pacing_events(projectId,chapterId,chapterNumber,eventType,pace,note,createdAt) VALUES(?,?,?,?,?,?,?)", value.projectId,value.chapterId,value.chapterNumber,value.eventType,value.pace,value.note,System.currentTimeMillis())
    }
    fun addEventMatrixRule(projectId: Long, key: String, label: String, cooldown: Int, category: String) = insert("INSERT INTO event_matrix_rules(projectId,ruleKey,label,cooldown,category,createdAt) VALUES(?,?,?,?,?,?)",projectId,key,label,cooldown.coerceIn(0,20),category,System.currentTimeMillis())
    fun ensureDefaultEventMatrixRules(projectId: Long) {
        if (eventMatrixRules(projectId).isNotEmpty()) return
        listOf("冲突升级" to 3, "信息揭示" to 2, "章节钩子" to 1).forEachIndexed { index, (label, cooldown) ->
            addEventMatrixRule(projectId, "default-$index", label, cooldown, "默认")
        }
    }
    fun updateEventMatrixRule(value: EventMatrixRule) = execute("UPDATE event_matrix_rules SET label=?,cooldown=?,category=?,enabled=? WHERE id=?",value.label,value.cooldown.coerceIn(0,20),value.category,if(value.enabled)1 else 0,value.id)
    fun deleteEventMatrixRule(id: Long) = execute("DELETE FROM event_matrix_rules WHERE id=?", id)
    fun saveStyleProfile(value: StyleProfile) = insert("INSERT INTO style_profiles(name,genre,guide,sourceProjectId,metrics,keywords,createdAt) VALUES(?,?,?,?,?,?,?)",value.name,value.genre,value.guide,value.sourceProjectId,value.metrics,value.keywords,System.currentTimeMillis())
    fun deleteStyleProfile(id: Long) = execute("DELETE FROM style_profiles WHERE id=?", id)
    fun markOutlineCascade(report: OutlineCascadeReport) = transaction {
        report.affectedAnchorIds.forEach { execute("UPDATE story_anchors SET cascadePending=1 WHERE id=?", it) }
        report.affectedItemIds.forEach { execute("UPDATE story_items SET cascadePending=1 WHERE id=?", it) }
        report.affectedEdgeIds.forEach { execute("UPDATE story_edges SET cascadePending=1 WHERE id=?", it) }
    }
    fun resolveOutlineCascade(projectId: Long) = transaction {
        execute("UPDATE story_anchors SET cascadePending=0 WHERE projectId=?", projectId)
        execute("UPDATE story_items SET cascadePending=0 WHERE projectId=?", projectId)
        execute("UPDATE story_edges SET cascadePending=0 WHERE projectId=?", projectId)
    }
    fun createAutoWriteRun(projectId: Long, requestedCount: Int): AutoWriteRun { val now=System.currentTimeMillis(); val id=insert("INSERT INTO auto_write_runs(projectId,requestedCount,completedCount,status,detail,createdAt,updatedAt) VALUES(?,?,?,?,?,?,?)",projectId,requestedCount,0,AutoWriteRunStatus.RUNNING,"正在准备第 1 章",now,now); return autoWriteRuns(projectId).first { it.id==id } }
    fun updateAutoWriteRun(run: AutoWriteRun, completedCount: Int, status: String, detail: String): AutoWriteRun { val updated=run.copy(completedCount=completedCount.coerceIn(0,run.requestedCount),status=status,detail=detail,updatedAt=System.currentTimeMillis());execute("UPDATE auto_write_runs SET completedCount=?,status=?,detail=?,updatedAt=? WHERE id=?",updated.completedCount,updated.status,updated.detail,updated.updatedAt,updated.id);return updated }
    fun addGeneratedChapter(projectId: Long, number: Int, title: String, content: String, outline: String, beatSheet: String, autoWriteRunId: Long): Chapter { val now=System.currentTimeMillis(); val id=insert("INSERT INTO chapters(projectId,number,title,content,outline,beatSheet,lifecycleStatus,lifecycleDetail,autoWriteRunId,updatedAt) VALUES(?,?,?,?,?,?,?,?,?,?)",projectId,number,title,content,outline,beatSheet,ChapterLifecycleStatus.PROCESSING,"正在同步本章记忆与质量门禁",autoWriteRunId,now);execute("UPDATE projects SET updatedAt=? WHERE id=?",now,projectId);return chapter(id)!! }
    fun rebuildRagChunks(chapter: Chapter) = transaction { execute("DELETE FROM rag_chunks WHERE chapterId=?",chapter.id); val now=System.currentTimeMillis(); chapter.content.split(Regex("\\n\\s*\\n")).filter(String::isNotBlank).chunked(3).forEachIndexed { index, parts -> val body=parts.joinToString("\n\n").take(1600); val terms=Regex("[\\p{IsHan}]{2,}").findAll(body).joinToString(" "){it.value}; insert("INSERT INTO rag_chunks(projectId,chapterId,chapterNumber,ordinal,content,terms,updatedAt) VALUES(?,?,?,?,?,?,?)",chapter.projectId,chapter.id,chapter.number,index,body,terms,now) } }
    fun saveContinuitySnapshot(value: ChapterContinuitySnapshot) { execute("INSERT OR REPLACE INTO chapter_continuity_snapshots(chapterId,projectId,predecessorChapterId,predecessorTail,contextPrompt,confirmationStatus,createdAt,updatedAt) VALUES(?,?,?,?,?,?,?,?)",value.chapterId,value.projectId,value.predecessorChapterId,value.predecessorTail,value.contextPrompt,value.confirmationStatus,value.createdAt,System.currentTimeMillis()) }
    fun addGateReport(value: ChapterGateReport) = insert("INSERT INTO chapter_gate_reports(projectId,chapterId,stage,passed,content,contextSnapshot,createdAt) VALUES(?,?,?,?,?,?,?)",value.projectId,value.chapterId,value.stage,if(value.passed)1 else 0,value.content,value.contextSnapshot,value.createdAt)
    fun lifecycleJob(chapterId: Long): ChapterLifecycleJob? = query("SELECT * FROM chapter_lifecycle_jobs WHERE chapterId=?", ::lifecycleJob, chapterId).firstOrNull()
    fun nextQueuedLifecycle(projectId: Long): ChapterLifecycleJob? = query("SELECT * FROM chapter_lifecycle_jobs WHERE projectId=? AND status='queued' ORDER BY updatedAt LIMIT 1", ::lifecycleJob, projectId).firstOrNull()
    fun enqueueLifecycle(chapter: Chapter): ChapterLifecycleJob = transaction {
        val now = System.currentTimeMillis()
        val fingerprint = fingerprint(chapter.content)
        val existing = lifecycleJob(chapter.id)
        val job = ChapterLifecycleJob(
            chapterId = chapter.id,
            projectId = chapter.projectId,
            contentFingerprint = fingerprint,
            status = ChapterLifecycleJobStatus.QUEUED,
            attempts = if (existing?.contentFingerprint == fingerprint) existing.attempts else 0,
            detail = "等待后台记忆与质量闭环",
            afterSuccessAction = existing?.afterSuccessAction.orEmpty(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        execute("INSERT OR REPLACE INTO chapter_lifecycle_jobs(chapterId,projectId,contentFingerprint,status,attempts,detail,afterSuccessAction,createdAt,updatedAt) VALUES(?,?,?,?,?,?,?,?,?)", job.chapterId,job.projectId,job.contentFingerprint,job.status,job.attempts,job.detail,job.afterSuccessAction,job.createdAt,job.updatedAt)
        job
    }
    fun claimLifecycle(job: ChapterLifecycleJob): ChapterLifecycleJob? = transaction {
        val current = lifecycleJob(job.chapterId) ?: return@transaction null
        if (current.status != ChapterLifecycleJobStatus.QUEUED || current.contentFingerprint != job.contentFingerprint) return@transaction null
        val claimed = current.copy(status = ChapterLifecycleJobStatus.RUNNING, attempts = current.attempts + 1, detail = "正在同步记忆与质量门禁", updatedAt = System.currentTimeMillis())
        execute("UPDATE chapter_lifecycle_jobs SET status=?,attempts=?,detail=?,updatedAt=? WHERE chapterId=?", claimed.status,claimed.attempts,claimed.detail,claimed.updatedAt,claimed.chapterId)
        claimed
    }
    fun finishLifecycle(job: ChapterLifecycleJob, passed: Boolean, detail: String) {
        execute("UPDATE chapter_lifecycle_jobs SET status=?,detail=?,updatedAt=? WHERE chapterId=? AND contentFingerprint=?", if (passed) ChapterLifecycleJobStatus.COMPLETED else ChapterLifecycleJobStatus.FAILED,detail.trim(),System.currentTimeMillis(),job.chapterId,job.contentFingerprint)
    }
    fun requeueLifecycle(job: ChapterLifecycleJob, detail: String) {
        execute("UPDATE chapter_lifecycle_jobs SET status=?,detail=?,updatedAt=? WHERE chapterId=? AND contentFingerprint=?", ChapterLifecycleJobStatus.QUEUED,detail.trim(),System.currentTimeMillis(),job.chapterId,job.contentFingerprint)
    }

    fun rawRows(table: String, projectId: Long): List<Map<String, Any?>> {
        require(table in PROJECT_TABLES)
        return query("SELECT * FROM $table WHERE projectId=?", { rs -> rowMap(rs) }, projectId)
    }

    fun rawProject(projectId:Long):Map<String,Any?> = query("SELECT * FROM projects WHERE id=?",{rs->rowMap(rs)},projectId).first()
    fun rawStyleProfiles(projectId:Long):List<Map<String,Any?>> = query("SELECT * FROM style_profiles WHERE sourceProjectId=?",{rs->rowMap(rs)},projectId)

    fun importRows(project:Map<String,Any?>,tables:Map<String,List<Map<String,Any?>>>):Long=transaction{
        val values=project.filterKeys{it!="id"}.toMutableMap().apply{this["updatedAt"]=System.currentTimeMillis();if(this["syncId"].toString().isBlank())this["syncId"]=newSyncId()}
        val newProjectId=insertMap("projects",values)
        val chapterIds=mutableMapOf<Long,Long>();val itemIds=mutableMapOf<Long,Long>();val reviewIds=mutableMapOf<Long,Long>()
        fun insertGroup(table:String,idMap:MutableMap<Long,Long>){tables[table].orEmpty().forEach{source->val old=(source["id"] as? Number)?.toLong();val translated=translate(source,newProjectId,chapterIds,itemIds,reviewIds).filterKeys{it!="id"};val newId=insertMap(table,translated);if(old!=null)idMap[old]=newId}}
        insertGroup("chapters",chapterIds);insertGroup("story_items",itemIds);insertGroup("batch_review_runs",reviewIds)
        listOf("chapter_revisions","auto_write_runs","import_analysis_runs","chapter_story_mentions","research_notes","editorial_reviews","chapter_pacing_events","event_matrix_rules","chapter_gate_reports","review_issues","rag_chunks","chapter_continuity_snapshots","chapter_lifecycle_jobs","story_anchors","story_edges").forEach{table->tables[table].orEmpty().forEach{source->val translated=translate(source,newProjectId,chapterIds,itemIds,reviewIds).filterKeys{it!="id"}.toMutableMap();if(table=="auto_write_runs"&&translated["status"]=="running")translated["status"]="paused";if(table=="chapter_lifecycle_jobs"&&translated["status"]=="running")translated["status"]="failed";if(table=="import_analysis_runs"&&translated["status"]=="running")translated["status"]="cancelled";upsertMap(table,translated)}}
        tables["style_profiles"].orEmpty().forEach{source->insertMap("style_profiles",source.filterKeys{it!="id"}.toMutableMap().apply{this["sourceProjectId"]=newProjectId})}
        newProjectId
    }

    private fun translate(source:Map<String,Any?>,projectId:Long,chapters:Map<Long,Long>,items:Map<Long,Long>,reviews:Map<Long,Long>)=source.toMutableMap().apply{
        if(containsKey("projectId"))this["projectId"]=projectId
        listOf("chapterId","predecessorChapterId").forEach{k->(this[k] as? Number)?.toLong()?.takeIf{it!=0L}?.let{this[k]=chapters[it]?:0L}}
        listOf("storyItemId","sourceItemId","targetItemId").forEach{k->(this[k] as? Number)?.toLong()?.let{this[k]=items[it]?:0L}}
        (this["reviewRunId"] as? Number)?.toLong()?.takeIf{it!=0L}?.let{this["reviewRunId"]=reviews[it]?:0L}
    }
    private fun rowMap(rs:ResultSet)=(1..rs.metaData.columnCount).associate{i->rs.metaData.getColumnName(i) to rs.getObject(i)}
    private fun insertMap(table:String,values:Map<String,Any?>):Long{require(table=="projects"||table in PROJECT_TABLES||table=="style_profiles");val columns=values.keys.joinToString(",");val marks=values.keys.joinToString(","){"?"};return insert("INSERT INTO $table($columns) VALUES($marks)",*values.values.toTypedArray())}
    private fun upsertMap(table:String,values:Map<String,Any?>){require(table in PROJECT_TABLES);val columns=values.keys.joinToString(",");val marks=values.keys.joinToString(","){"?"};execute("INSERT OR REPLACE INTO $table($columns) VALUES($marks)",*values.values.toTypedArray())}

    private fun recoverInterruptedJobs() {
        execute("UPDATE auto_write_runs SET status='paused',detail='应用上次退出，任务已暂停' WHERE status='running'")
        execute("UPDATE chapter_lifecycle_jobs SET status='failed',detail='应用上次退出，可重新执行' WHERE status='running'")
        execute("UPDATE chapters SET lifecycleStatus='memory_failed',lifecycleDetail='上次处理未完成，请重新运行章节闭环' WHERE lifecycleStatus='processing'")
        execute("UPDATE import_analysis_runs SET status='cancelled',detail='应用上次退出，导入分析已暂停，可随时重新开始' WHERE status='running'")
    }

    private fun project(r: ResultSet)=NovelProject(r.long("id"),r.string("title"),r.string("genre"),r.string("premise"),r.string("styleGuide"),r.string("outlineRevisionReport"),r.string("summary"),r.string("tags"),r.string("targetAudience"),r.string("protagonistName"),r.string("longFormBlueprint"),r.int("targetChapterCount"),r.int("targetWordCount"),r.string("pacingProfile"),r.string("forbiddenContent"),r.string("automationLevel"),r.int("targetChapterWordCount"),r.int("targetChapterWordCountMax"),r.string("coverPath"),r.long("createdAt"),r.long("updatedAt"),r.string("syncId"))
    private fun ideationDraft(r: ResultSet)=IdeationDraft(r.long("id"),r.int("step"),r.string("title"),r.string("genre"),r.string("premise"),r.string("protagonist"),r.string("conflict"),r.string("promise"),r.string("targetAudience"),r.string("writingStyle"),r.string("forbiddenContent"),r.string("automationLevel"),r.int("targetChapterWordCount"),r.int("targetChapterWordCountMax"),r.int("targetWordCount"),r.long("createdAt"),r.long("updatedAt"))
    private fun chapter(r: ResultSet)=Chapter(r.long("id"),r.long("projectId"),r.int("number"),r.string("title"),r.string("content"),r.string("outline"),r.string("beatSheet"),r.int("targetWordCount"),r.string("qualityStatus"),r.string("qualityIssueSummary"),r.string("lifecycleStatus"),r.string("lifecycleDetail"),r.long("memoryUpdatedAt"),r.long("autoWriteRunId"),r.int("gateFailureCount"),r.bool("requiresHumanReview"),r.long("updatedAt"))
    private fun autoWriteRun(r: ResultSet)=AutoWriteRun(r.long("id"),r.long("projectId"),r.int("requestedCount"),r.int("completedCount"),r.string("status"),r.string("detail"),r.long("createdAt"),r.long("updatedAt"))
    private fun importAnalysisRun(r: ResultSet)=ImportAnalysisRun(r.long("projectId"),r.string("status"),r.string("stage"),r.int("progress"),r.string("detail"),r.long("updatedAt"))
    private fun lifecycleJob(r: ResultSet)=ChapterLifecycleJob(r.long("chapterId"),r.long("projectId"),r.string("contentFingerprint"),r.string("status"),r.int("attempts"),r.string("detail"),r.string("afterSuccessAction"),r.long("createdAt"),r.long("updatedAt"))
    private fun fingerprint(content: String) = "${content.length}:${content.hashCode()}"
    private fun newSyncId() = java.util.UUID.randomUUID().toString().replace("-", "")
    private fun ResultSet.long(name:String)=getLong(name); private fun ResultSet.int(name:String)=getInt(name); private fun ResultSet.string(name:String)=getString(name).orEmpty(); private fun ResultSet.bool(name:String)=getInt(name)!=0
    private fun queryInt(sql:String)=connection.createStatement().use{ st->st.executeQuery(sql).use{rs->rs.next();rs.getInt(1)}}
    private fun execute(sql:String,vararg args:Any?){ connection.prepareStatement(sql).use{st->args.forEachIndexed{i,v->st.setObject(i+1,v)};st.executeUpdate()} }
    private fun insert(sql:String,vararg args:Any?):Long=connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS).use{st->args.forEachIndexed{i,v->st.setObject(i+1,v)};st.executeUpdate();st.generatedKeys.use{it.next();it.getLong(1)}}
    private fun execute(sql:String)=connection.createStatement().use{ statement -> sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach(statement::execute) }
    private fun <T> query(sql:String,mapper:(ResultSet)->T,vararg args:Any?):List<T> = connection.prepareStatement(sql).use{st->args.forEachIndexed{i,v->st.setObject(i+1,v)};st.executeQuery().use{rs->buildList{while(rs.next())add(mapper(rs))}}}
    private fun <T> transaction(block:()->T):T { val old=connection.autoCommit;connection.autoCommit=false;return try{block().also{connection.commit()}}catch(t:Throwable){connection.rollback();throw t}finally{connection.autoCommit=old} }
    override fun close()=connection.close()

    companion object { val PROJECT_TABLES=setOf("chapters","chapter_revisions","auto_write_runs","import_analysis_runs","chapter_story_mentions","research_notes","editorial_reviews","chapter_pacing_events","event_matrix_rules","chapter_gate_reports","batch_review_runs","review_issues","rag_chunks","chapter_continuity_snapshots","chapter_lifecycle_jobs","story_items","story_anchors","story_edges") }
}
