package com.mozhou.novelcraft.desktop

import androidx.compose.runtime.*
import com.mozhou.novelcraft.core.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class MainSection { SHELF, WORKSPACE, SETTINGS }
enum class WorkspaceTab(val label:String){ WRITE("写作"),PROJECT("作品"),OUTLINE("大纲"),RESOURCES("资料"),REVIEW("审核") }
enum class SaveStatus(val label:String){ SAVED("已保存"),EDITING("编辑中"),SAVING("保存中"),FAILED("保存失败") }
private data class SyncResult(val revision: Int, val conflictProjectId: Long? = null)

class AppState(val paths:AppPaths=AppPaths.resolve()) : AutoCloseable {
    private val db=DesktopDatabase(paths.database)
    private val prefs=SecureModelPreferences(paths)
    private val cloudPrefs=SecureCloudSyncPreferences(paths)
    private val cloud=CloudSyncClient()
    private val updatePrefs=UpdatePreferences(paths)
    private val updates=UpdateService()
    private val backup=ProjectBackup(db,paths)
    private val ai=OpenAiCompatibleClient()
    private val workspaceLayoutStore=WorkspaceLayoutStore(paths)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var saveJob:Job?=null
    private var generationJob:Job?=null
    private var generationRequest:GenerationRequest?=null
    private var generationEpoch = 0L
    private var importAnalysisJob:Job?=null
    private var importAnalysisRequest:GenerationRequest?=null
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var applyingEditorHistory = false

    var section by mutableStateOf(MainSection.SHELF)
    var tab by mutableStateOf(WorkspaceTab.WRITE)
    var projects by mutableStateOf(db.projects()); private set
    var selectedProject by mutableStateOf<NovelProject?>(null); private set
    var chapters by mutableStateOf(emptyList<Chapter>()); private set
    var selectedChapter by mutableStateOf<Chapter?>(null); private set
    var storyItems by mutableStateOf(emptyList<StoryItem>()); private set
    var anchors by mutableStateOf(emptyList<StoryAnchor>()); private set
    var storyEdges by mutableStateOf(emptyList<StoryEdge>()); private set
    var notes by mutableStateOf(emptyList<ResearchNote>()); private set
    var ragChunks by mutableStateOf(emptyList<RagChunk>()); private set
    var revisions by mutableStateOf(emptyList<ChapterRevision>()); private set
    var editorialReviews by mutableStateOf(emptyList<EditorialReview>()); private set
    var gateReports by mutableStateOf(emptyList<ChapterGateReport>()); private set
    var batchReviewRuns by mutableStateOf(emptyList<BatchReviewRun>()); private set
    var reviewIssues by mutableStateOf(emptyList<ReviewIssue>()); private set
    var repairPlan by mutableStateOf(""); private set
    var referenceAnalysis by mutableStateOf(""); private set
    var onlineResearchResults by mutableStateOf(emptyList<OnlineResearchResult>()); private set
    var pacingEvents by mutableStateOf(emptyList<ChapterPacingEvent>()); private set
    var eventMatrixRules by mutableStateOf(emptyList<EventMatrixRule>()); private set
    var styleProfiles by mutableStateOf(emptyList<StyleProfile>()); private set
    var ideationDraft by mutableStateOf(db.latestIdeationDraft()); private set
    var importAnalysisRun by mutableStateOf<ImportAnalysisRun?>(null); private set
    var workspaceLayout by mutableStateOf(workspaceLayoutStore.load()); private set
    var outlineCascadeReport by mutableStateOf<OutlineCascadeReport?>(null); private set
    var resumableAutoWriteRun by mutableStateOf<AutoWriteRun?>(null); private set
    var editorText by mutableStateOf(""); private set
    var saveStatus by mutableStateOf(SaveStatus.SAVED); private set
    var modelConfig by mutableStateOf(prefs.load())
    var cloudConfig by mutableStateOf(cloudPrefs.load()); private set
    var updateManifestUrl by mutableStateOf(updatePrefs.load())
    var availableUpdate by mutableStateOf<UpdateInfo?>(null); private set
    var busy by mutableStateOf(false); private set
    var streamedText by mutableStateOf(""); private set
    var message by mutableStateOf<String?>(null)

    init {
        if (updateManifestUrl.isNotBlank()) checkForUpdates(notifyWhenLatest = false)
    }

    fun selectProject(id:Long){selectedProject=db.project(id);section=MainSection.WORKSPACE;refreshProject();chapters.firstOrNull()?.let{selectChapter(it.id)}}
    fun selectChapter(id:Long){flushSave();selectedChapter=db.chapter(id);editorText=selectedChapter?.content.orEmpty();undoStack.clear();redoStack.clear();revisions=db.revisions(id);editorialReviews=selectedProject?.let{db.editorialReviews(it.id,id)}?:emptyList();gateReports=db.gateReports(id);saveStatus=SaveStatus.SAVED}
    fun createProject(title:String,genre:String,premise:String){val id=db.createProject(title.ifBlank{"未命名作品"},genre.ifBlank{"待分类"},premise);db.ensureDefaultEventMatrixRules(id);projects=db.projects();selectProject(id)}
    fun generateGuidedIdeation(seed: String, genre: String) {
        launchTextGeneration(GenerationTask.PROJECT_PROFILE, { request ->
            ai.generateProjectProfile(modelConfig, "作者只有一个模糊灵感。请主动补足完整开书资料，给出明确主角起点、长期冲突和可持续悬念。灵感：${seed.ifBlank { "自由创作适合中文网文连载的故事" }}\n偏好题材：$genre", request).getOrElse { throw it }
        }) { raw ->
            val json = JSONObject(raw)
            ideationDraft = db.saveIdeationDraft(IdeationDraft(
                title = json.optString("title").ifBlank { "未命名作品" },
                genre = json.optString("genre").ifBlank { genre.ifBlank { "待分类" } },
                premise = json.optString("premise").ifBlank { seed },
                protagonist = json.optString("protagonistName"),
                conflict = json.optString("conflict"),
                promise = json.optString("promise").ifBlank { json.optString("summary") },
                targetAudience = json.optString("targetAudience"),
                writingStyle = json.optString("writingStyle"),
                forbiddenContent = json.optString("forbiddenContent"),
            ))
            message = "AI 已整理开书资料，确认后即可创建作品"
        }
    }
    fun createFromIdeationDraft() {
        val draft = ideationDraft ?: return
        val id = db.createProject(draft.title.ifBlank { "未命名作品" }, draft.genre.ifBlank { "待分类" }, draft.premise)
        db.ensureDefaultEventMatrixRules(id)
        val created = db.project(id) ?: return
        db.updateProject(created.copy(summary = draft.promise, protagonistName = draft.protagonist, targetAudience = draft.targetAudience, styleGuide = draft.writingStyle, forbiddenContent = draft.forbiddenContent))
        db.deleteIdeationDraft(draft.id)
        ideationDraft = null
        projects = db.projects()
        selectProject(id)
        if (modelConfig.baseUrl.isBlank() || modelConfig.apiKey.isBlank() || modelConfig.model.isBlank()) {
            message = "作品已从灵感草案创建；配置文本模型后可生成完整第一章"
        } else {
            message = "作品已创建，正在生成第一章"
            writeFullChapter()
        }
    }
    fun saveIdeationDraft(draft: IdeationDraft) {
        ideationDraft = db.saveIdeationDraft(draft.copy(title = draft.title.ifBlank { "未命名作品" }))
        message = "开书草案已保存，可稍后继续"
    }
    fun updateWorkspaceLayout(value: WorkspaceLayout, persist: Boolean = false) {
        workspaceLayout = value
        if (persist) workspaceLayoutStore.save(value)
    }
    fun deleteProject(){selectedProject?.let{db.deleteProject(it.id)};selectedProject=null;selectedChapter=null;projects=db.projects();section=MainSection.SHELF}
    fun addChapter(action: NextChapterAction = NextChapterAction.CREATE_BLANK) {
        val project = selectedProject ?: return
        val current = selectedChapter ?: run {
            selectChapter(db.addChapter(project.id))
            refreshProject()
            return
        }
        if (busy) return
        flushSave()
        val previous = db.chapter(current.id) ?: return
        if (previous.content.isBlank()) {
            message = "请先完成当前章节正文，再新建下一章"
            return
        }
        val request = GenerationRequest()
        val epoch = ++generationEpoch
        generationRequest = request
        busy = true
        streamedText = ""
        generationJob = scope.launch {
            try {
                val lifecycle = if (previous.lifecycleStatus == ChapterLifecycleStatus.PASSED) ChapterLifecycleResult(true, "章节闭环已通过") else runChapterLifecycle(previous, request)
                if (!lifecycle.passed) {
                    refreshProject()
                    selectChapter(previous.id)
                    message = "第${previous.number}章正在完成闭环；通过后才能新建下一章：${lifecycle.message}"
                    return@launch
                }
                val nextId = db.addChapter(project.id)
                val next = db.chapter(nextId) ?: return@launch
                refreshProject()
                selectChapter(next.id)
                if (action == NextChapterAction.CREATE_BLANK) {
                    message = "已新建第${next.number}章"
                    return@launch
                }
                val context = ContextEngine.build(project, next, db.chapters(project.id), db.storyItems(project.id), db.anchors(project.id), researchNotes = db.notes(project.id), ragChunks = db.ragChunks(project.id)).prompt
                val generated = ai.continueWriting(modelConfig, context, request) { delta ->
                    appendStreamDelta(epoch, request, delta)
                }.getOrElse { throw it }
                db.saveChapter(next.copy(content = generated), "AI 生成下一章")
                refreshProject()
                selectChapter(next.id)
                streamedText = ""
                message = "第${next.number}章已由 AI 生成"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (ownsGeneration(epoch, request)) message = failureMessage(error)
            } finally {
                if (ownsGeneration(epoch, request)) {
                    generationRequest = null
                    generationJob = null
                    streamedText = ""
                    busy = false
                }
            }
        }
    }
    fun deleteChapter(){val c=selectedChapter?:return;db.deleteChapter(c.id);refreshProject();chapters.firstOrNull()?.let{selectChapter(it.id)}?:run{selectedChapter=null;editorText=""}}
    fun editContent(value:String){if(value==editorText)return;if(!applyingEditorHistory){undoStack.addLast(editorText);if(undoStack.size>200)undoStack.removeFirst();redoStack.clear()};editorText=value;saveStatus=SaveStatus.EDITING;saveJob?.cancel();saveJob=scope.launch{delay(700);saveNow()};writeRecovery(value)}
    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    fun undoEditor() { if (undoStack.isEmpty()) return; redoStack.addLast(editorText); applyingEditorHistory = true; editContent(undoStack.removeLast()); applyingEditorHistory = false }
    fun redoEditor() { if (redoStack.isEmpty()) return; undoStack.addLast(editorText); applyingEditorHistory = true; editContent(redoStack.removeLast()); applyingEditorHistory = false }
    fun replaceAllInEditor(query: String, replacement: String) { if (query.isBlank()) return; val count = Regex(Regex.escape(query)).findAll(editorText).count(); if (count == 0) { message = "未找到“$query”"; return }; editContent(editorText.replace(query, replacement)); message = "已替换 $count 处" }
    fun renameChapter(value:String){selectedChapter=selectedChapter?.copy(title=value);saveStatus=SaveStatus.EDITING;saveJob?.cancel();saveJob=scope.launch{delay(500);saveNow()}}
    fun updateOutline(value:String){selectedChapter=selectedChapter?.copy(outline=value);scheduleStructuredSave()}
    fun updateBeatSheet(value:String){selectedChapter=selectedChapter?.copy(beatSheet=value);scheduleStructuredSave()}
    fun updateChapterTargetWordCount(value: Int) { selectedChapter = selectedChapter?.copy(targetWordCount = value.coerceAtLeast(0)); scheduleStructuredSave() }
    fun updateProject(value:NovelProject){selectedProject=value;scope.launch(Dispatchers.IO){db.updateProject(value);withContext(Dispatchers.Main){projects=db.projects();message="作品资料已保存"}}}
    fun addStoryItem(kind: String, name: String, detail: String, status: String = StoryItemStatus.ACTIVE) { selectedProject?.let { db.addStoryItem(it.id, kind.trim(), name.trim(), detail.trim(), status.trim().ifBlank { StoryItemStatus.ACTIVE }); refreshProject() } }
    fun updateStoryItem(item: StoryItem) { db.updateStoryItem(item); refreshProject(); message = "资料卡已保存" }
    fun addStoryEdge(sourceId: Long, targetId: Long, relation: String, description: String, sinceChapter: Int = selectedChapter?.number ?: 1) { val p = selectedProject ?: return; if (sourceId == targetId || relation.isBlank()) return; db.addEdge(p.id, sourceId, targetId, relation.trim(), description.trim(), sinceChapter.coerceAtLeast(1)); refreshProject(); message = "关系已加入知识图谱" }
    fun deleteStoryItem(id: Long) { db.deleteStoryItem(id); refreshProject(); message = "资料卡及其关联关系已删除" }
    fun deleteStoryEdge(id: Long) { db.deleteEdge(id); refreshProject(); message = "关系已删除" }
    fun addAnchor(startChapter: Int, endChapter: Int, title: String, coreConflict: String, allowedPlot: String, forbiddenReveals: String, mandatoryTension: String) {
        val project = selectedProject ?: return
        if (title.isBlank() || coreConflict.isBlank()) return
        db.addAnchor(project.id, startChapter.coerceAtLeast(1), endChapter.coerceAtLeast(startChapter.coerceAtLeast(1)), title.trim(), coreConflict.trim(), allowedPlot.trim(), forbiddenReveals.trim(), mandatoryTension.trim())
        refreshProject()
        message = "故事锚点已保存"
    }
    fun deleteAnchor(id: Long) { db.deleteAnchor(id); refreshProject(); message = "故事锚点已删除" }
    fun updateAnchor(anchor: StoryAnchor) { db.updateAnchor(anchor.copy(startChapter = anchor.startChapter.coerceAtLeast(1), endChapter = anchor.endChapter.coerceAtLeast(anchor.startChapter.coerceAtLeast(1)))); refreshProject(); message = "故事锚点已保存" }
    fun addNote(title: String, sourceUrl: String = "", tags: String = "", content: String, rightsConfirmed: Boolean = false) {
        selectedProject?.let {
            db.addNote(it.id, title.trim(), content.trim(), sourceUrl.trim(), tags.trim(), rightsConfirmed)
            refreshProject()
            message = "研究笔记已保存"
        }
    }
    fun updateNote(note: ResearchNote) { db.updateNote(note); refreshProject(); message = "研究笔记已保存" }
    fun deleteNote(id: Long) { db.deleteNote(id); refreshProject(); message = "研究笔记已删除" }
    fun restoreRevision(id:Long){selectedChapter?.let{db.restoreRevision(it.id,id);selectChapter(it.id);message="已恢复历史版本"}}
    fun deleteRevision(id: Long) { selectedChapter?.let { chapter -> db.deleteRevision(chapter.id, id); revisions = db.revisions(chapter.id); message = "历史版本已删除" } }
    fun saveModelConfig(value:ModelConfig){prefs.save(value);modelConfig=value;message="模型配置已加密保存"}
    fun enableCloudSync(){runTask{val created=withContext(Dispatchers.IO){cloud.createVault()};cloudPrefs.save(created);cloudConfig=created;message="云同步已开启。请保存恢复码：${created.recoveryCode}"}}
    fun restoreCloudSync(code:String){val parts=code.trim().split('.',limit=2);if(parts.size!=2){message="恢复码格式不正确";return};runTask{val restored=CloudSyncConfig(parts[0],parts[1]);withContext(Dispatchers.IO){cloud.list(restored)};cloudPrefs.save(restored);cloudConfig=restored;message="云端保险箱已连接，可恢复缺失作品"}}
    fun restoreCloudProjects(){if(!cloudConfig.enabled){message="请先输入恢复码";return};val localSyncIds=projects.map{it.syncId}.toSet();runTask{val imported=withContext(Dispatchers.IO){var count=0;val revisions=cloudConfig.revisions.toMutableMap();cloud.list(cloudConfig).forEach{remote->if(remote.id !in localSyncIds){val temporary=Files.createTempFile("noveledit-cloud-",".json");try{Files.write(temporary,cloud.download(cloudConfig,remote.id));backup.import(temporary);revisions[remote.id]=remote.revision;count++}finally{Files.deleteIfExists(temporary)}}};count to revisions};cloudConfig=cloudConfig.copy(revisions=imported.second);cloudPrefs.save(cloudConfig);projects=db.projects();message=if(imported.first==0)"云端没有缺失作品" else "已从云端恢复 ${imported.first} 部作品"}}
    fun saveUpdateManifest(url:String){updatePrefs.save(url);updateManifestUrl=url;message="更新地址已保存"}
    fun checkForUpdates(notifyWhenLatest:Boolean=true){runTask{val result=withContext(Dispatchers.IO){updates.check(updateManifestUrl,AppVersion.CURRENT)};availableUpdate=result;if(result!=null||notifyWhenLatest)message=if(result==null)"当前已是最新版本" else "发现 NovelEdit ${result.version}"}}
    fun downloadUpdate(){val update=availableUpdate?:return;runTask{val portable=paths.portable;val extension=if(portable)"zip" else "msi";val target=paths.root.resolve("updates").resolve("NovelEdit-${update.version}.$extension");withContext(Dispatchers.IO){updates.download(update,portable,target);java.awt.Desktop.getDesktop().open(target.toFile())};message="更新包已校验并打开，请按安装程序完成更新"}}
    fun syncSelectedProject(){
        val project=selectedProject?:run{message="请先打开要同步的作品";return}
        if(!cloudConfig.enabled){message="请先在设置中开启云同步";return}
        runTask{
            val result=withContext(Dispatchers.IO){
                val temporary=Files.createTempFile("noveledit-sync-",".json")
                try {
                    backup.export(project.id,temporary)
                    try {
                        SyncResult(cloud.upload(cloudConfig,project.syncId,cloudConfig.revisions[project.syncId]?:0,Files.readAllBytes(temporary)))
                    } catch (conflict: CloudRevisionConflict) {
                        Files.write(temporary,cloud.download(cloudConfig,project.syncId))
                        val importedId=backup.import(temporary)
                        db.project(importedId)?.let { remote ->
                            db.updateProject(remote.copy(title="${remote.title}（云端冲突副本）"))
                        }
                        SyncResult(conflict.remoteRevision, importedId)
                    }
                } finally { Files.deleteIfExists(temporary) }
            }
            cloudConfig=cloudConfig.copy(revisions=cloudConfig.revisions+(project.syncId to result.revision));cloudPrefs.save(cloudConfig);projects=db.projects()
            message=if(result.conflictProjectId==null) "已加密同步《${project.title}》" else "云端版本已作为《${db.project(result.conflictProjectId)?.title}》导入，本地作品未被覆盖"
        }
    }
    fun testModel(){runTask{message=ai.test(modelConfig).getOrElse { throw it }}}
    fun testReviewModel(){runTask{message=ai.test(reviewModelConfig()).getOrElse { throw it }}}
    fun testImageModel(){runTask{message=ai.testImage(modelConfig).getOrElse { throw it }}}
    fun saveContinuitySnapshot(){val p=selectedProject?:return;val c=selectedChapter?.copy(content=editorText)?:return;val predecessor=chapters.filter{it.number<c.number&&it.content.isNotBlank()}.maxByOrNull{it.number};val prompt=ContextEngine.build(p,c,chapters,storyItems,anchors,researchNotes=notes,ragChunks=ragChunks).prompt;db.saveContinuitySnapshot(ChapterContinuitySnapshot(c.id,p.id,predecessor?.id?:0,predecessor?.content?.takeLast(2200).orEmpty(),prompt,if(predecessor==null||predecessor.lifecycleStatus==ChapterLifecycleStatus.PASSED)ContinuitySnapshotStatus.CONFIRMED else ContinuitySnapshotStatus.PENDING));message="连续性快照已保存"}
    fun runSelectedChapterLifecycle() {
        val chapter = selectedChapter?.copy(content = editorText) ?: return
        if (chapter.content.isBlank()) { message = "章节正文为空，无法执行章节闭环"; return }
        runTask {
            val result = runChapterLifecycle(chapter, GenerationRequest())
            withContext(Dispatchers.Main) {
                refreshProject()
                selectChapter(chapter.id)
                message = result.message
            }
        }
    }
    fun extractChapterMemory(){val p=selectedProject?:return;val c=selectedChapter?.copy(content=editorText)?:return;if(c.content.isBlank()){message="章节正文为空，无法提取记忆";return};launchTextGeneration(GenerationTask.MEMORY_EXTRACTION,{request->ai.extractStoryMemory(modelConfig,"第${c.number}章 ${c.title}\n${c.content}",request).getOrElse{throw it}}){raw->val extraction=MemoryExtractionParser.parse(raw);val known=storyItems.associateBy{it.name}.toMutableMap();val mentioned=mutableSetOf<Long>();extraction.items.distinctBy{it.name}.forEach{item->val existing=known[item.name];if(existing==null){val id=db.addStoryItem(p.id,item.kind,item.name,item.detail);known[item.name]=StoryItem(id,p.id,item.kind,item.name,item.detail,item.status)}else if(item.detail.isNotBlank()){db.updateStoryItem(existing.copy(kind=item.kind,detail=item.detail,status=item.status))};known[item.name]?.let{mentioned+=it.id}};val edgeKeys=db.edges(p.id).map{Triple(it.sourceItemId,it.targetItemId,it.relation)}.toMutableSet();extraction.edges.forEach{edge->val source=known[edge.sourceName]?:return@forEach;val target=known[edge.targetName]?:return@forEach;mentioned+=source.id;mentioned+=target.id;if(edgeKeys.add(Triple(source.id,target.id,edge.relation)))db.addEdge(p.id,source.id,target.id,edge.relation,edge.description,c.number)};db.replaceChapterMentions(c,mentioned);refreshProject();val issues=QualityGate.inspect(c,storyItems,anchors,p);val content=if(issues.isEmpty())"PASS: 记忆提取与本地一致性检查通过" else "WARN: "+issues.joinToString("；"){it.title};db.addGateReport(ChapterGateReport(projectId=p.id,chapterId=c.id,stage="记忆与本地一致性",passed=issues.none{it.severity==QualitySeverity.WARNING},content=content,contextSnapshot=ContextEngine.build(p,c,chapters,storyItems,anchors,researchNotes=notes,ragChunks=ragChunks).prompt.take(12000)));message="记忆已更新：${mentioned.size} 条章节引用"}}
    fun autoWriteChapters(count:Int){val p=selectedProject?:return;if(busy)return;startAutoWrite(p,db.createAutoWriteRun(p.id,count.coerceIn(1,5)))}
    fun resumeAutoWrite(){val p=selectedProject?:return;val run=db.resumableAutoWriteRun(p.id)?:run{message="没有可继续的批量写作计划";return};startAutoWrite(p,run)}
    fun importCover(path:Path){val p=selectedProject?:return;runTask{val extension=path.fileName.toString().substringAfterLast('.',"png");val target=paths.covers.resolve("project-${p.id}-${System.currentTimeMillis()}.$extension");Files.copy(path,target,StandardCopyOption.REPLACE_EXISTING);db.updateProject(p.copy(coverPath=target.toString()));withContext(Dispatchers.Main){selectedProject=db.project(p.id);projects=db.projects();message="封面已导入"}}}
    fun generateCover(prompt:String){val p=selectedProject?:return;generationRequest?.cancel();generationJob?.cancel();val request=GenerationRequest();generationRequest=request;busy=true;generationJob=scope.launch{runCatching{ai.generateCover(modelConfig,prompt.ifBlank{"Chinese web novel cover, cinematic composition, no text, no watermark"},request).getOrElse{throw it}}.onSuccess{bytes->withContext(Dispatchers.IO){val target=paths.covers.resolve("project-${p.id}-${System.currentTimeMillis()}.png");atomicWrite(target,bytes);db.updateProject(p.copy(coverPath=target.toString()))};selectedProject=db.project(p.id);projects=db.projects();message="AI 封面已生成"}.onFailure{if(it !is CancellationException)message=it.message}.also{generationRequest=null;busy=false}}}
    fun generateChapterPlan(){val p=selectedProject?:return;val c=selectedChapter?:return;launchTextGeneration(GenerationTask.CHAPTER_PLAN,{request->ai.generateChapterPlan(modelConfig,ContextEngine.build(p,c.copy(content=editorText),chapters,storyItems,anchors,researchNotes=notes).prompt,request).getOrElse{throw it}}){text->updateOutline(text);message="章节计划已生成"}}
    fun generateBeatSheet(){val p=selectedProject?:return;val c=selectedChapter?:return;launchTextGeneration(GenerationTask.BEAT_SHEET,{request->ai.generateBeatSheet(modelConfig,ContextEngine.build(p,c.copy(content=editorText),chapters,storyItems,anchors,researchNotes=notes).prompt,request).getOrElse{throw it}}){text->updateBeatSheet(text);message="场景分镜已生成"}}
    fun generateChapterTitle(){val c=selectedChapter?:return;launchTextGeneration(GenerationTask.CHAPTER_PLAN,{request->ai.generateChapterTitle(modelConfig,editorText,request).getOrElse{throw it}}){text->renameChapter(text.lineSequence().firstOrNull().orEmpty().trim().take(60));message="章节标题已生成"}}
    fun writeFullChapter(){val p=selectedProject?:return;val c=selectedChapter?:return;launchStreamingGeneration(GenerationTask.OPENING_CHAPTER, { request, delta -> ai.writeFullChapter(modelConfig,ContextEngine.build(p,c.copy(content=editorText),chapters,storyItems,anchors,researchNotes=notes).prompt,request,delta).getOrElse { throw it } }) { text -> editContent(if(editorText.isBlank()) text else editorText+"\n"+text);message="完整章节已生成" }}
    fun rewriteChapter(){val p=selectedProject?:return;val c=selectedChapter?:return;launchTextGeneration(GenerationTask.CHAPTER_REWRITE,{request->ai.rewriteChapter(modelConfig,ContextEngine.build(p,c.copy(content=editorText),chapters,storyItems,anchors,researchNotes=notes).prompt,request).getOrElse{throw it}}){text->editContent(text);message="章节已改写"}}
    fun humanizeChapter(){val p=selectedProject?:return;val c=selectedChapter?:return;launchTextGeneration(GenerationTask.HUMANIZE,{request->ai.humanizeChapter(modelConfig,ContextEngine.build(p,c.copy(content=editorText),chapters,storyItems,anchors,researchNotes=notes).prompt,request).getOrElse{throw it}}){text->editContent(text);message="润色完成"}}
    fun runEditorialReview(){val p=selectedProject?:return;val c=selectedChapter?:return;launchTextGeneration(GenerationTask.EDITORIAL_REVIEW,{request->ai.editorialReview(reviewModelConfig(),ContextEngine.build(p,c.copy(content=editorText),chapters,storyItems,anchors,researchNotes=notes).prompt,request).getOrElse{throw it}}){text->db.addEditorialReview(p.id,c.id,text);editorialReviews=db.editorialReviews(p.id,c.id);message="编辑审稿已完成"}}
    fun runEditorialTeamReview() {
        val project = selectedProject ?: return
        val chapter = selectedChapter?.copy(content = editorText) ?: return
        if (chapter.content.isBlank()) { message = "本章没有正文，无法启动编辑团队"; return }
        launchTextGeneration(GenerationTask.EDITORIAL_TEAM, { request ->
            val context = ContextEngine.build(project, chapter, chapters, storyItems, anchors, researchNotes = notes, ragChunks = ragChunks).prompt + "\n完整正文：\n${chapter.content}"
            val reviewer = reviewModelConfig()
            val planning = ai.generateChapterPlan(reviewer, context, request).getOrElse { throw it }
            val character = ai.characterConsistencyReview(reviewer, context, request).getOrElse { throw it }
            val copyedit = ai.copyeditReview(reviewer, context, request).getOrElse { throw it }
            "【总策划】\n$planning\n\n【角色校对】\n$character\n\n【文字编辑】\n$copyedit"
        }) { report ->
            db.addEditorialReview(project.id, chapter.id, report)
            editorialReviews = db.editorialReviews(project.id, chapter.id)
            message = "编辑团队审稿已保存，未自动修改正文"
        }
    }
    fun runBatchEditorialReview(startChapter: Int, endChapter: Int) {
        val project = selectedProject ?: return
        val selected = chapters.filter { it.number in startChapter..endChapter && it.content.isNotBlank() }
        if (selected.isEmpty()) { message = "所选范围没有可审稿的正文"; return }
        launchTextGeneration(GenerationTask.BATCH_REVIEW, { request ->
            val body = selected.joinToString("\n\n") { "【第${it.number}章 ${it.title}】\n${it.content.take(8000)}" }
            ai.editorialReview(reviewModelConfig(), "请进行跨章节审稿。逐行输出：[P0|P1|P2][第N章或全局] 问题摘要。P0 是矛盾、泄露或逻辑断裂；没有问题时输出 PASS。\n\n$body", request).getOrElse { throw it }
        }) { report ->
            val round = (db.batchReviewRuns(project.id).maxOfOrNull { it.round } ?: 0) + 1
            db.addBatchReview(project.id, selected.first().number, selected.last().number, round, report, parseReviewIssues(report))
            refreshProject()
            message = "批量审稿已完成，问题已进入审核台"
        }
    }
    fun setReviewIssueResolved(id: Long, resolved: Boolean) { db.updateReviewIssueStatus(id, if (resolved) "resolved" else "open"); selectedProject?.let { reviewIssues = db.reviewIssues(it.id) } }
    fun generateRepairPlan() {
        val project = selectedProject ?: return
        val chapter = selectedChapter?.copy(content = editorText) ?: return
        val issues = reviewIssues.filter { it.status == "open" }.joinToString("\n") { "[${it.severity}] ${if (it.chapterNumber == 0) "全局" else "第${it.chapterNumber}章"} ${it.summary}" }
        val context = ContextEngine.build(project, chapter, chapters, storyItems, anchors, researchNotes = notes, ragChunks = ragChunks).prompt + "\n正文：\n${chapter.content}\n待解决问题：\n${issues.ifBlank { "请根据本章上下文提出最小修复方案" }}"
        launchTextGeneration(GenerationTask.REPAIR_PLAN, { request -> ai.generateRepairPlan(modelConfig, context, request).getOrElse { throw it } }) { plan -> repairPlan = plan; message = "修复方案已生成，请确认后应用" }
    }
    fun applyRepairPlan() {
        val project = selectedProject ?: return
        val chapter = selectedChapter?.copy(content = editorText) ?: return
        if (repairPlan.isBlank()) { message = "请先生成修复方案"; return }
        val context = ContextEngine.build(project, chapter, chapters, storyItems, anchors, researchNotes = notes, ragChunks = ragChunks).prompt + "\n当前正文：\n${chapter.content}\n修复方案：\n$repairPlan"
        launchTextGeneration(GenerationTask.CHAPTER_REWRITE, { request -> ai.rewriteChapter(modelConfig, context, request).getOrElse { throw it } }) { text ->
            editContent(text)
            message = "修复稿已写入编辑器，请保存后运行章节闭环"
        }
    }
    fun analyzeReference(note: ResearchNote) {
        val project = selectedProject ?: return
        launchTextGeneration(GenerationTask.REFERENCE_ANALYSIS, { request ->
            ai.analyzeReferenceStructure(modelConfig, "作品类型：${project.genre}\n研究笔记：${note.title}\n${note.content}", request).getOrElse { throw it }
        }) { result -> referenceAnalysis = result; message = "参考结构分析已完成" }
    }
    fun searchOnlineResearch(query: String) {
        runTask {
            val results = OnlineResearchClient.search(query)
            withContext(Dispatchers.Main) {
                onlineResearchResults = results
                message = if (results.isEmpty()) "没有找到可引用的公开资料" else "已找到 ${results.size} 条公开资料"
            }
        }
    }
    fun clearOnlineResearchResults() { onlineResearchResults = emptyList() }
    fun saveResearchResult(result: OnlineResearchResult) {
        selectedProject?.let { project ->
            db.addNote(project.id, result.title, result.excerpt, result.sourceUrl, result.sourceLabel, rightsConfirmed = true)
            refreshProject()
            message = "资料已保存为研究笔记"
        }
    }
    fun savePacingEvent(eventType: String, pace: String, note: String) {
        val chapter = selectedChapter ?: return
        db.savePacingEvent(ChapterPacingEvent(projectId = chapter.projectId, chapterId = chapter.id, chapterNumber = chapter.number, eventType = eventType.trim(), pace = pace.trim(), note = note.trim()))
        refreshProject(); message = "章节节奏事件已保存"
    }
    fun addEventMatrixRule(label: String, cooldown: Int, category: String) {
        val project = selectedProject ?: return
        if (label.isBlank()) return
        db.addEventMatrixRule(project.id, "custom-${System.currentTimeMillis()}", label.trim(), cooldown, category.trim().ifBlank { "自定义" })
        refreshProject(); message = "事件矩阵规则已添加"
    }
    fun updateEventMatrixRule(rule: EventMatrixRule) {
        db.updateEventMatrixRule(rule.copy(label = rule.label.trim(), category = rule.category.trim().ifBlank { "自定义" }, cooldown = rule.cooldown.coerceIn(0, 20)))
        refreshProject()
        message = "事件矩阵规则已更新"
    }
    fun deleteEventMatrixRule(id: Long) { db.deleteEventMatrixRule(id); refreshProject(); message = "事件矩阵规则已删除" }
    fun saveCurrentStyleProfile(name: String) {
        val project = selectedProject ?: return
        if (name.isBlank() || project.styleGuide.isBlank()) return
        val sample = selectedChapter?.content.orEmpty()
        val fingerprint = StyleFingerprintAnalyzer.analyze(sample)
        db.saveStyleProfile(StyleProfile(name = name.trim(), genre = project.genre, guide = project.styleGuide, sourceProjectId = project.id, metrics = fingerprint.metrics, keywords = fingerprint.keywords))
        refreshProject(); message = "文风档案已保存"
    }
    fun applyStyleProfile(profile: StyleProfile) { selectedProject?.let { updateProject(it.copy(styleGuide = profile.guide)); message = "已应用文风档案：${profile.name}" } }
    fun deleteStyleProfile(id: Long) { db.deleteStyleProfile(id); refreshProject(); message = "文风档案已删除" }
    fun pacingRecommendation(): PacingRecommendation? = selectedProject?.let { project -> PacingPlanner.recommend(project, pacingEvents, eventMatrixRules, selectedChapter?.number ?: ((chapters.maxOfOrNull { it.number } ?: 0) + 1)) }
    fun analyzeOutlineCascade(description: String) {
        val chapter = selectedChapter ?: return
        val report = OutlineCascadeAnalyzer.analyze(chapter.number, chapters, storyItems, anchors, storyEdges, description)
        db.markOutlineCascade(report)
        outlineCascadeReport = report
        refreshProject()
        message = "已标记 ${report.affectedItemIds.size} 项资料和 ${report.affectedEdgeIds.size} 条关系待确认"
    }
    fun resolveOutlineCascade() {
        val project = selectedProject ?: return
        db.resolveOutlineCascade(project.id)
        outlineCascadeReport = null
        refreshProject()
        message = "改纲影响已确认"
    }
    fun generateProjectProfile(){val p=selectedProject?:return;launchTextGeneration(GenerationTask.PROJECT_PROFILE,{request->ai.generateProjectProfile(modelConfig,"作品名称：${p.title}\n类型：${p.genre}\n已有设定：${p.premise}",request).getOrElse{throw it}}){text->applyGeneratedProfile(p,text)}}
    fun generateLongFormBlueprint(){val p=selectedProject?:return;launchTextGeneration(GenerationTask.LONG_FORM_BLUEPRINT,{request->ai.generateLongFormBlueprint(modelConfig,projectContext(p),request).getOrElse{throw it}}){text->updateProject(p.copy(longFormBlueprint=text));message="长篇路线图已生成"}}
    fun extractStyleGuide(){val p=selectedProject?:return;val c=selectedChapter?:return;launchTextGeneration(GenerationTask.STYLE_GUIDE,{request->ai.extractStyleGuide(modelConfig,editorText,request).getOrElse{throw it}}){text->updateProject(p.copy(styleGuide=text));message="文风档案已生成"}}
    fun startImportAnalysis() {
        val project = selectedProject ?: return
        if (busy) { message = "当前已有任务在运行，请稍后开始导入分析"; return }
        if (modelConfig.baseUrl.isBlank() || modelConfig.apiKey.isBlank() || modelConfig.model.isBlank()) {
            importAnalysisRun = db.saveImportAnalysisRun(ImportAnalysisRun(project.id, ImportAnalysisStatus.WAITING_FOR_CONFIG, "等待模型配置", 0, "请先在设置中完成文本模型配置"))
            message = "请先完成文本模型配置"
            return
        }
        val queued = db.saveImportAnalysisRun(ImportAnalysisRun(project.id, ImportAnalysisStatus.QUEUED, "等待开始", 0, "将提炼导入正文的作品资料和文风"))
        importAnalysisRun = queued
        val request = GenerationRequest()
        importAnalysisRequest = request
        busy = true
        importAnalysisJob = scope.launch {
            try {
                importAnalysisRun = db.saveImportAnalysisRun(queued.copy(status = ImportAnalysisStatus.RUNNING, stage = "整理导入正文", progress = 15, detail = "正在提取可用于设定的章节样本"))
                val imported = db.chapters(project.id)
                val sample = (imported.take(3) + imported.takeLast(2)).distinctBy { it.id }.joinToString("\n\n") { "【第${it.number}章 ${it.title}】\n${it.content.take(2000)}" }.take(9000)
                require(sample.isNotBlank()) { "导入作品没有可分析的正文" }
                importAnalysisRun = db.saveImportAnalysisRun(queued.copy(status = ImportAnalysisStatus.RUNNING, stage = "提炼作品资料", progress = 40, detail = "正在生成题材、简介、主角与长期冲突"))
                val profileRaw = ai.generateProjectProfile(modelConfig, "以下是已导入小说正文，请提炼作品资料，不能虚构正文之外的事实：\n$sample", request).getOrElse { throw it }
                val profile = JSONObject(profileRaw.trim().removePrefix("```json").removeSuffix("```").trim())
                val current = db.project(project.id) ?: return@launch
                fun value(name: String, fallback: String) = profile.optString(name).trim().ifBlank { fallback }
                db.updateProject(current.copy(
                    title = value("title", current.title), genre = value("genre", current.genre), premise = value("premise", current.premise),
                    summary = value("summary", current.summary), tags = value("tags", current.tags), targetAudience = value("targetAudience", current.targetAudience),
                    protagonistName = value("protagonistName", current.protagonistName), forbiddenContent = value("forbiddenContent", current.forbiddenContent),
                ))
                importAnalysisRun = db.saveImportAnalysisRun(queued.copy(status = ImportAnalysisStatus.RUNNING, stage = "提炼文风", progress = 75, detail = "正在归纳叙事、节奏和语言习惯"))
                val style = ai.extractStyleGuide(modelConfig, sample, request).getOrElse { throw it }
                val updated = db.project(project.id) ?: return@launch
                db.updateProject(updated.copy(styleGuide = style))
                importAnalysisRun = db.saveImportAnalysisRun(queued.copy(status = ImportAnalysisStatus.COMPLETED, stage = "分析完成", progress = 100, detail = "作品资料和文风档案已从导入正文中提炼"))
                refreshProject(); message = "导入分析完成，已更新作品资料和文风"
            } catch (cancelled: CancellationException) {
                importAnalysisRun = db.saveImportAnalysisRun(queued.copy(status = ImportAnalysisStatus.CANCELLED, stage = "已取消", progress = importAnalysisRun?.progress ?: 0, detail = "导入分析已取消，可随时重新开始"))
                throw cancelled
            } catch (error: Throwable) {
                importAnalysisRun = db.saveImportAnalysisRun(queued.copy(status = ImportAnalysisStatus.FAILED, stage = "分析失败", progress = importAnalysisRun?.progress ?: 0, detail = failureMessage(error)))
                message = importAnalysisRun?.detail
            } finally {
                importAnalysisRequest = null
                importAnalysisJob = null
                busy = false
            }
        }
    }
    fun cancelImportAnalysis() {
        val project = selectedProject ?: return
        importAnalysisRequest?.cancel(); importAnalysisJob?.cancel()
        importAnalysisRun = db.saveImportAnalysisRun(ImportAnalysisRun(project.id, ImportAnalysisStatus.CANCELLED, "已取消", importAnalysisRun?.progress ?: 0, "导入分析已取消，可随时重新开始"))
        importAnalysisRequest = null; importAnalysisJob = null; busy = false
    }
    fun continueWriting(direction:String){val p=selectedProject?:return;val c=selectedChapter?:return;launchStreamingGeneration(GenerationTask.CONTINUATION,{request,onDelta->val context=ContextEngine.build(p,c.copy(content=editorText),chapters,storyItems,anchors,researchNotes=notes).prompt;ai.continueWriting(modelConfig,context+"\n续写要求："+direction,request,onDelta).getOrElse { throw it }}){generated->editContent(editorText+generated);message="续写完成"}}
    fun cancelGeneration(){val request=generationRequest;val draft=streamedText;generationEpoch++;request?.cancel();generationJob?.cancel();generationRequest=null;generationJob=null;streamedText="";busy=false;if(draft.isNotBlank())editContent(editorText+draft);message=if(draft.isBlank())"生成已取消" else "生成已取消，已保留接收草稿"}
    fun importDocument(path:Path){runTask{val text=DocumentIO.read(path);val parsed=ChapterImporter.parse(text);val id=db.createProject(path.fileName.toString().substringBeforeLast('.'),"待分类","从 ${path.fileName} 导入");val initial=db.chapters(id).first();if(parsed.isEmpty())db.saveChapter(initial.copy(content=text),"导入文档")else{db.saveChapter(initial.copy(title=parsed.first().title,content=parsed.first().content),"导入文档");parsed.drop(1).forEach{part->val chapter=db.chapter(db.addChapter(id))!!;db.saveChapter(chapter.copy(title=part.title,content=part.content),"导入文档")}};db.saveImportAnalysisRun(ImportAnalysisRun(id, ImportAnalysisStatus.WAITING_FOR_CONFIG, "等待模型配置", 0, "已导入正文，可在作品页开始 AI 分析"));withContext(Dispatchers.Main){projects=db.projects();selectProject(id);message="导入完成：${parsed.size.coerceAtLeast(1)} 章，可继续进行 AI 分析"}}}
    fun exportDocument(path:Path,format:String){val p=selectedProject?:return;runTask{flushSave();DocumentIO.export(path,format,p,db.chapters(p.id),db.storyItems(p.id),db.edges(p.id),db.notes(p.id));withContext(Dispatchers.Main){message="已导出到 $path"}}}
    fun exportBackup(path:Path){val p=selectedProject?:return;runTask{flushSave();backup.export(p.id,path);withContext(Dispatchers.Main){message="项目备份已导出"}}}
    fun importBackup(path:Path){runTask{val id=backup.import(path);withContext(Dispatchers.Main){projects=db.projects();selectProject(id);message="项目备份已恢复"}}}
    fun qualityIssues():List<QualityIssue>{val p=selectedProject?:return emptyList();val c=selectedChapter?.copy(content=editorText)?:return emptyList();return QualityGate.inspect(c,storyItems,anchors,p)}
    fun aiTraceReport(): AiTraceReport = AiTraceDetector.inspect(editorText)
    fun researchPlan(): ResearchPlan? = selectedProject?.let { ResearchPlanner.build(it, notes) }
    fun search(query:String):List<ChapterSearchResult> = StorySearch.find(chapters,query)
    fun flushSave(){saveJob?.cancel();if(saveStatus!=SaveStatus.SAVED)runBlocking{saveNow()}}
    private suspend fun saveNow(){val c=selectedChapter?:return;saveStatus=SaveStatus.SAVING;runCatching{withContext(Dispatchers.IO){val saved=c.copy(content=editorText);db.saveChapter(saved);db.rebuildRagChunks(saved)}}.onSuccess{selectedChapter=db.chapter(c.id);revisions=db.revisions(c.id);saveStatus=SaveStatus.SAVED;Files.deleteIfExists(recoveryFile(c.id))}.onFailure{saveStatus=SaveStatus.FAILED;message=it.message}}
    private fun scheduleStructuredSave(){saveStatus=SaveStatus.EDITING;saveJob?.cancel();saveJob=scope.launch{delay(500);saveNow()}}
    private fun parseReviewIssues(report: String): List<ReviewIssue> {
        val format = Regex("\\[(P[012])]\\s*\\[(?:第(\\d+)章|全局)]\\s*(.+)")
        return report.lineSequence().mapNotNull { line ->
            format.find(line.trim())?.let { match ->
                ReviewIssue(severity = match.groupValues[1], chapterNumber = match.groupValues[2].toIntOrNull() ?: 0, summary = match.groupValues[3].take(240))
            }
        }.toList()
    }
    private fun refreshProject(){val id=selectedProject?.id?:return;selectedProject=db.project(id);chapters=db.chapters(id);storyItems=db.storyItems(id);storyEdges=db.edges(id);anchors=db.anchors(id);notes=db.notes(id);ragChunks=db.ragChunks(id);resumableAutoWriteRun=db.resumableAutoWriteRun(id);importAnalysisRun=db.importAnalysisRun(id);batchReviewRuns=db.batchReviewRuns(id);reviewIssues=db.reviewIssues(id);pacingEvents=db.pacingEvents(id);eventMatrixRules=db.eventMatrixRules(id);styleProfiles=db.styleProfiles(id)}
    private fun writeRecovery(text:String){selectedChapter?.let{runCatching{atomicWrite(recoveryFile(it.id),text.toByteArray())}}}
    private fun recoveryFile(id:Long)=paths.recovery.resolve("chapter-$id.txt")
    private fun reviewModelConfig(): ModelConfig {
        val values = listOf(modelConfig.reviewerBaseUrl, modelConfig.reviewerApiKey, modelConfig.reviewerModel)
        if (values.all(String::isBlank)) return modelConfig
        require(values.none(String::isBlank)) { "独立审稿模型需要同时填写 Base URL、API Key 和模型名称" }
        return modelConfig.copy(baseUrl = modelConfig.reviewerBaseUrl, apiKey = modelConfig.reviewerApiKey, model = modelConfig.reviewerModel, protocol = modelConfig.reviewerProtocol.ifBlank { modelConfig.protocol })
    }
    private fun projectContext(project:NovelProject)="作品：${project.title}\n类型：${project.genre}\n设定：${project.premise}\n简介：${project.summary}\n主角：${project.protagonistName}\n标签：${project.tags}\n禁区：${project.forbiddenContent}\n已有蓝图：${project.longFormBlueprint}"
    private fun applyGeneratedProfile(project: NovelProject, text: String) {
        val json = runCatching { JSONObject(text) }.getOrElse {
            throw IllegalArgumentException("作品设定返回格式无效：${it.message}")
        }
        fun value(name: String, current: String) = json.optString(name).trim().ifBlank { current }
        updateProject(project.copy(
            title = value("title", project.title),
            genre = value("genre", project.genre),
            premise = value("premise", project.premise),
            summary = value("summary", project.summary),
            tags = value("tags", project.tags),
            targetAudience = value("targetAudience", project.targetAudience),
            protagonistName = value("protagonistName", project.protagonistName),
            forbiddenContent = value("forbiddenContent", project.forbiddenContent),
            styleGuide = value("writingStyle", project.styleGuide),
        ))
        message = "作品设定已生成"
    }
    private suspend fun runChapterLifecycle(chapter: Chapter, request: GenerationRequest): ChapterLifecycleResult {
        val job = db.claimLifecycle(db.enqueueLifecycle(chapter)) ?: return ChapterLifecycleResult(false, "章节闭环任务无法启动")
        val current = db.chapter(chapter.id) ?: run {
            db.finishLifecycle(job, false, "章节已删除")
            return ChapterLifecycleResult(false, "章节已删除")
        }
        db.saveChapter(current.copy(lifecycleStatus = ChapterLifecycleStatus.PROCESSING, lifecycleDetail = "正在提取本章记忆并执行质量门禁"))
        return try {
            val raw = ai.extractStoryMemory(modelConfig, "第${current.number}章 ${current.title}\n${current.content}", request).getOrElse { throw it }
            if (db.chapter(current.id)?.content != current.content) {
                val changed = db.chapter(current.id)!!
                db.saveChapter(changed.copy(lifecycleStatus = ChapterLifecycleStatus.MEMORY_FAILED, lifecycleDetail = "正文已在处理期间修改，请重新运行章节闭环"))
                db.finishLifecycle(job, false, "正文已修改")
                return ChapterLifecycleResult(false, "正文已修改，请重新运行章节闭环")
            }
            applyLifecycleMemory(current, MemoryExtractionParser.parse(raw))
            db.rebuildRagChunks(current)
            val project = db.project(current.projectId) ?: throw IllegalStateException("作品不存在")
            val currentItems = db.storyItems(current.projectId)
            val currentAnchors = db.anchors(current.projectId)
            val currentChapters = db.chapters(current.projectId)
            val issues = QualityGate.inspect(current, currentItems, currentAnchors, project)
            val blocking = issues.filter { it.title in setOf("发现占位文本", "发现重复段落", "可能提前揭露大纲禁区") }
            val passed = blocking.isEmpty()
            val detail = if (passed) "记忆、RAG 和本地质量门禁已通过" else blocking.joinToString("；") { it.title }
            db.addGateReport(ChapterGateReport(projectId = project.id, chapterId = current.id, stage = "记忆更新", passed = true, content = "PASS: 章节记忆已同步", contextSnapshot = ""))
            db.addGateReport(ChapterGateReport(projectId = project.id, chapterId = current.id, stage = "本地一致性与节奏", passed = passed, content = if (passed) "PASS: $detail" else "FAIL: [P0] $detail", contextSnapshot = ""))
            val saved = db.chapter(current.id) ?: current
            val failures = if (passed) 0 else saved.gateFailureCount + 1
            db.saveChapter(saved.copy(
                lifecycleStatus = if (passed) ChapterLifecycleStatus.PASSED else ChapterLifecycleStatus.WAITING_REVIEW,
                lifecycleDetail = detail,
                qualityStatus = if (passed) ChapterQualityStatus.READY else ChapterQualityStatus.NEEDS_REPAIR,
                qualityIssueSummary = if (passed) "" else detail,
                memoryUpdatedAt = System.currentTimeMillis(),
                gateFailureCount = failures,
                requiresHumanReview = failures >= 2,
            ))
            saveLifecycleContinuitySnapshot(project, current, currentChapters, currentItems, currentAnchors)
            db.finishLifecycle(job, passed, detail)
            ChapterLifecycleResult(passed, if (passed) "章节闭环已通过" else "章节已保存，但质量门禁未通过：$detail")
        } catch (cancelled: CancellationException) {
            db.requeueLifecycle(job, "已暂停，可稍后继续")
            throw cancelled
        } catch (error: Throwable) {
            val latest = db.chapter(current.id)
            latest?.let { db.saveChapter(it.copy(lifecycleStatus = ChapterLifecycleStatus.MEMORY_FAILED, lifecycleDetail = "记忆同步失败：${error.message ?: "模型请求失败"}")) }
            db.finishLifecycle(job, false, "记忆同步失败：${error.message ?: "模型请求失败"}")
            ChapterLifecycleResult(false, "记忆同步失败，可在审核页重试")
        }
    }

    private fun applyLifecycleMemory(chapter: Chapter, extraction: MemoryExtraction) {
        val known = db.storyItems(chapter.projectId).associateBy { it.name }.toMutableMap()
        val mentioned = mutableSetOf<Long>()
        extraction.items.distinctBy { it.name }.forEach { item ->
            val existing = known[item.name]
            val resolved = if (existing == null) {
                val id = db.addStoryItem(chapter.projectId, item.kind, item.name, item.detail)
                StoryItem(id, chapter.projectId, item.kind, item.name, item.detail, item.status)
            } else {
                db.updateStoryItem(existing.copy(kind = item.kind, detail = item.detail.ifBlank { existing.detail }, status = item.status))
                existing
            }
            known[item.name] = resolved
            mentioned += resolved.id
        }
        val edgeKeys = db.edges(chapter.projectId).map { Triple(it.sourceItemId, it.targetItemId, it.relation) }.toMutableSet()
        extraction.edges.forEach { edge ->
            val source = known[edge.sourceName] ?: return@forEach
            val target = known[edge.targetName] ?: return@forEach
            mentioned += source.id; mentioned += target.id
            if (edgeKeys.add(Triple(source.id, target.id, edge.relation))) db.addEdge(chapter.projectId, source.id, target.id, edge.relation, edge.description, chapter.number)
        }
        db.replaceChapterMentions(chapter, mentioned)
    }

    private fun saveLifecycleContinuitySnapshot(project: NovelProject, chapter: Chapter, allChapters: List<Chapter>, items: List<StoryItem>, anchors: List<StoryAnchor>) {
        val predecessor = allChapters.filter { it.number < chapter.number && it.content.isNotBlank() }.maxByOrNull { it.number }
        val prompt = ContextEngine.build(project, chapter, allChapters, items, anchors, researchNotes = db.notes(project.id), ragChunks = db.ragChunks(project.id)).prompt
        db.saveContinuitySnapshot(ChapterContinuitySnapshot(chapter.id, project.id, predecessor?.id ?: 0, predecessor?.content?.takeLast(2200).orEmpty(), prompt, if (predecessor == null || predecessor.lifecycleStatus == ChapterLifecycleStatus.PASSED) ContinuitySnapshotStatus.CONFIRMED else ContinuitySnapshotStatus.PENDING))
    }

    private suspend fun automaticHumanize(project: NovelProject, body: String, request: GenerationRequest): String {
        if (project.automationLevel != "自动推进" || body.isBlank()) return body
        fun keepDraft(result: Result<String>, fallback: String) = result.getOrElse { error -> if (error is CancellationException) throw error else fallback }
        val first = keepDraft(ai.humanizeChapter(modelConfig, "自动推进模式第一遍去 AI 味润色。只输出完整正文，不改变剧情事实、人物关系、视角或篇幅。\n\n正文：\n$body", request), body)
        return keepDraft(ai.humanizeChapter(modelConfig, "自动推进模式第二遍去 AI 味校对。只处理残留机械重复、模板化衔接和同质句式；只输出完整正文。\n\n正文：\n$first", request), first)
    }

    private fun startAutoWrite(project:NovelProject,run:AutoWriteRun){generationRequest?.cancel();generationJob?.cancel();val request=GenerationRequest();generationRequest=request;busy=true;generationJob=scope.launch{var current=run;try{val working=chapters.toMutableList();current=db.updateAutoWriteRun(run,run.completedCount,AutoWriteRunStatus.RUNNING,"正在准备第 ${run.completedCount+1} 章");repeat(current.requestedCount-current.completedCount){val number=(working.maxOfOrNull{it.number}?:0)+1;val target=Chapter(projectId=project.id,number=number,title="第${number}章");val planningContext=ContextEngine.build(project,target,working,storyItems,anchors,researchNotes=notes).prompt;val outline=ai.generateChapterPlan(modelConfig,planningContext,request).getOrElse{throw it};val prepared=target.copy(outline=outline);val beatSheet=ai.generateBeatSheet(modelConfig,ContextEngine.build(project,prepared,working,storyItems,anchors,researchNotes=notes).prompt,request).getOrElse{throw it};val ready=prepared.copy(beatSheet=beatSheet);val draft=ai.writeFullChapter(modelConfig,ContextEngine.build(project,ready,working,storyItems,anchors,researchNotes=notes).prompt,request).getOrElse{throw it};val body=automaticHumanize(project,draft,request);val fallback="第${number}章";val title=ai.generateChapterTitle(modelConfig,body,request).getOrDefault(fallback).lineSequence().firstOrNull().orEmpty().trim().take(60).ifBlank{fallback};val chapter=db.addGeneratedChapter(project.id,number,title,body,outline,beatSheet,current.id);working+=chapter;val lifecycle=runChapterLifecycle(chapter,request);if(!lifecycle.passed){current=db.updateAutoWriteRun(current,current.completedCount,AutoWriteRunStatus.PAUSED,"第${number}章已保存，等待处理质量门禁：${lifecycle.message}");withContext(Dispatchers.Main){refreshProject();selectChapter(chapter.id);message=current.detail};return@launch};current=db.updateAutoWriteRun(current,current.completedCount+1,AutoWriteRunStatus.RUNNING,"第${number}章已生成并通过闭环（${current.completedCount+1}/${current.requestedCount}）");withContext(Dispatchers.Main){refreshProject();selectChapter(chapter.id);message=current.detail}};current=db.updateAutoWriteRun(current,current.requestedCount,AutoWriteRunStatus.COMPLETED,"批量写作完成，共生成 ${current.requestedCount} 章");withContext(Dispatchers.Main){refreshProject();message=current.detail}}catch(cancelled:CancellationException){db.updateAutoWriteRun(current,current.completedCount,AutoWriteRunStatus.PAUSED,"作者已暂停，可继续批量写作");withContext(Dispatchers.Main){refreshProject()};throw cancelled}catch(error:Throwable){current=db.updateAutoWriteRun(current,current.completedCount,AutoWriteRunStatus.PAUSED,"已暂停：${error.message?:"模型请求失败"}");withContext(Dispatchers.Main){refreshProject();message=current.detail}}finally{generationRequest=null;busy=false}}}
    private fun failureMessage(error: Throwable): String {
        val http = generateSequence(error) { it.cause }.filterIsInstance<AiHttpException>().firstOrNull()
        return when (http?.statusCode) {
            401, 403 -> "模型服务拒绝了凭据（HTTP ${http.statusCode}），请检查 API Key 和服务地址"
            404 -> "模型“${modelConfig.model}”不存在、不可用或当前账号无权限。请在设置中填写服务商提供的实际模型 ID"
            408 -> "模型服务响应超时（HTTP 408），请稍后重试"
            429 -> "模型服务正在限流（HTTP 429），请稍后重试或降低并发"
            in 500..599 -> "模型服务暂时不可用（HTTP ${http?.statusCode}），已自动重试后仍失败"
            else -> when {
                generateSequence(error) { it.cause }.any { it is SocketTimeoutException } -> "连接模型服务超时，请检查网络或稍后重试"
                generateSequence(error) { it.cause }.any { it is UnknownHostException } -> "无法找到模型服务地址，请检查 Base URL 和网络连接"
                generateSequence(error) { it.cause }.any { it is SSLException } -> "模型服务的 TLS 证书或协议不受信任，请检查 HTTPS 地址"
                else -> "模型请求失败：${error.message ?: error.javaClass.simpleName}"
            }
        }
    }
    private fun ownsGeneration(epoch:Long,request:GenerationRequest)=generationEpoch==epoch&&generationRequest===request
    private fun beginGeneration():Pair<Long,GenerationRequest>{generationRequest?.cancel();generationJob?.cancel();val request=GenerationRequest();val epoch=++generationEpoch;generationRequest=request;busy=true;streamedText="";return epoch to request}
    private fun appendStreamDelta(epoch:Long,request:GenerationRequest,delta:String){scope.launch{if(ownsGeneration(epoch,request)&&!request.isCancelled)streamedText+=delta}}
    private fun launchTextGeneration(task:GenerationTask,block:suspend(GenerationRequest)->String,onSuccess:(String)->Unit){val(epoch,request)=beginGeneration();generationJob=scope.launch{runCatching{block(request)}.onSuccess{if(ownsGeneration(epoch,request))onSuccess(it)}.onFailure{if(it !is CancellationException&&ownsGeneration(epoch,request))message=failureMessage(it)}.also{if(ownsGeneration(epoch,request)){generationRequest=null;generationJob=null;busy=false}}}}
    private fun launchStreamingGeneration(task:GenerationTask,block:suspend(GenerationRequest,(String)->Unit)->String,onSuccess:(String)->Unit){val(epoch,request)=beginGeneration();generationJob=scope.launch{runCatching{block(request){delta->appendStreamDelta(epoch,request,delta)}}.onSuccess{if(ownsGeneration(epoch,request)){onSuccess(it);streamedText=""}}.onFailure{if(it !is CancellationException&&ownsGeneration(epoch,request)){val draft=streamedText;if(draft.isNotBlank()){editContent(editorText+draft);message="${failureMessage(it)}；已保留已接收草稿"}else message=failureMessage(it);streamedText=""}}.also{if(ownsGeneration(epoch,request)){generationRequest=null;generationJob=null;busy=false}}}}
    private fun runTask(block:suspend()->Unit){if(busy)return;busy=true;scope.launch{runCatching{block()}.onFailure{message=failureMessage(it)};busy=false}}
    override fun close(){flushSave();workspaceLayoutStore.save(workspaceLayout);importAnalysisRequest?.cancel();scope.cancel();db.close()}
}
