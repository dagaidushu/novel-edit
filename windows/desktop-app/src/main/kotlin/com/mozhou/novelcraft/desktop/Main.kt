package com.mozhou.novelcraft.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mozhou.novelcraft.core.*
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

private val Ink=Color(0xFF202124);private val Paper=Color(0xFFF7F8FA);private val Brand=Color(0xFF246B5A);private val Rule=Color(0xFFD9DEE3);private val Warm=Color(0xFFB45F32)

@Composable
private fun ResizeDivider(onResize: (Float) -> Unit, onResizeFinished: () -> Unit) {
    val resize by rememberUpdatedState(onResize)
    val finishResize by rememberUpdatedState(onResizeFinished)
    Box(
        Modifier.width(8.dp).fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { finishResize() }) { change, dragAmount ->
                    change.consume()
                    resize(dragAmount.x)
                }
            }
            .background(Rule.copy(alpha = 0.5f)),
    )
}

fun main()=application {
    val state=remember{AppState()}
    val windowStore = remember { WindowStateStore(state.paths) }
    val restoredWindow = remember { windowStore.load() }
    val windowState = androidx.compose.ui.window.rememberWindowState(
        width = restoredWindow.width.dp,
        height = restoredWindow.height.dp,
        position = if (restoredWindow.x != null && restoredWindow.y != null) androidx.compose.ui.window.WindowPosition(restoredWindow.x.dp, restoredWindow.y.dp) else androidx.compose.ui.window.WindowPosition.PlatformDefault,
    )
    Window(onCloseRequest={windowStore.save(windowState);state.close();exitApplication()},title="NovelEdit",icon=painterResource("branding/noveledit-icon.png"),state=windowState){
        MaterialTheme(colorScheme=lightColorScheme(primary=Brand,secondary=Warm,background=Paper,surface=Color.White,onSurface=Ink),typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=15.sp))){NovelEditApp(state)}
    }
}

@Composable private fun NovelEditApp(state:AppState){
    Row(Modifier.fillMaxSize().background(Paper)){
        Navigation(state)
        VerticalDivider(color=Rule)
        when(state.section){MainSection.SHELF->ShelfV2(state);MainSection.WORKSPACE->Workspace(state);MainSection.SETTINGS->Settings(state)}
    }
    state.message?.let{msg->SnackbarHost(SnackbarHostState().also{host->LaunchedEffect(msg){host.showSnackbar(msg);state.message=null}} ,Modifier.fillMaxSize().padding(20.dp).wrapContentSize(Alignment.BottomCenter))}
}

@Composable private fun Navigation(state:AppState){
    Column(Modifier.width(76.dp).fillMaxHeight().background(Color.White).padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Icon(Icons.Outlined.AutoStories,null,tint=Brand,modifier=Modifier.size(32.dp));Spacer(Modifier.height(24.dp))
        NavButton(Icons.Outlined.Home,"书架",state.section==MainSection.SHELF){state.section=MainSection.SHELF}
        NavButton(Icons.Outlined.EditNote,"创作",state.section==MainSection.WORKSPACE,state.selectedProject!=null){state.section=MainSection.WORKSPACE}
        Spacer(Modifier.weight(1f));NavButton(Icons.Outlined.Settings,"设置",state.section==MainSection.SETTINGS){state.section=MainSection.SETTINGS}
        Text(if(state.paths.portable)"便携" else "本地",fontSize=11.sp,color=Color.Gray)
    }
}
@Composable private fun NavButton(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,selected:Boolean,enabled:Boolean=true,onClick:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth().clickable(enabled=enabled,onClick=onClick).padding(vertical=12.dp)){Icon(icon,label,tint=if(selected)Brand else Color(0xFF67717B));Text(label,fontSize=11.sp,color=if(selected)Brand else Color.Gray)}}

@Composable private fun Shelf(state:AppState){var create by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(32.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column{Text("我的书架",fontSize=28.sp,fontWeight=FontWeight.SemiBold);Text("${state.projects.size} 部作品 · 本地保存",color=Color.Gray)};Spacer(Modifier.weight(1f));OutlinedButton({pickOpen(listOf("json"))?.let(state::importBackup)},enabled=!state.busy){Icon(Icons.Outlined.Restore,null);Spacer(Modifier.width(8.dp));Text("恢复备份")};Spacer(Modifier.width(10.dp));OutlinedButton({pickOpen(listOf("txt","md","docx","epub","pdf"))?.let(state::importDocument)},enabled=!state.busy){Icon(Icons.Outlined.FileOpen,null);Spacer(Modifier.width(8.dp));Text("导入文稿")};Spacer(Modifier.width(10.dp));Button({create=true}){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("新建作品")}}
        Spacer(Modifier.height(24.dp));if(state.projects.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.LibraryBooks,null,Modifier.size(52.dp),tint=Color.Gray);Text("书架还是空的",fontSize=20.sp);Text("新建作品或导入现有文稿",color=Color.Gray)}}else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(state.projects,key={it.id}){p->Surface(onClick={state.selectProject(p.id)},shape=MaterialTheme.shapes.small,border=BorderStroke(1.dp,Rule),color=Color.White){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(46.dp).background(Brand),contentAlignment=Alignment.Center){Text(p.title.take(1),color=Color.White,fontSize=20.sp)};Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(p.title,fontSize=18.sp,fontWeight=FontWeight.Medium);Text("${p.genre}  ·  ${p.premise.take(80)}",color=Color.Gray,maxLines=1)};Icon(Icons.Outlined.ChevronRight,null,tint=Color.Gray)}}}}
    };if(create)CreateProjectDialog({create=false},{t,g,p->create=false;state.createProject(t,g,p)})
}

@Composable
private fun ShelfV2(state: AppState) {
    var create by remember { mutableStateOf(false) }
    var ideate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的书架", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("${state.projects.size} 部作品 · 本地保存", color = Color.Gray)
            }
            OutlinedButton({ pickOpen(listOf("json"))?.let(state::importBackup) }, enabled = !state.busy) { Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(6.dp)); Text("恢复备份") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ pickOpen(listOf("txt", "md", "docx", "epub", "pdf"))?.let(state::importDocument) }, enabled = !state.busy) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("导入文稿") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ ideate = true }, enabled = !state.busy) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("AI 灵感开书") }
            Spacer(Modifier.width(8.dp))
            Button({ create = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("新建作品") }
        }
        Spacer(Modifier.height(24.dp))
        if (state.projects.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("书架还是空的", color = Color.Gray) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.projects, key = { it.id }) { project ->
                Surface(onClick = { state.selectProject(project.id) }, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, Rule), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).background(Brand), contentAlignment = Alignment.Center) { Text(project.title.take(1), color = Color.White, fontSize = 20.sp) }
                        Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(project.title, fontSize = 18.sp, fontWeight = FontWeight.Medium); Text("${project.genre} · ${project.premise.take(100)}", color = Color.Gray, maxLines = 1) }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Color.Gray)
                    }
                }
            }
        }
    }
    if (create) CreateProjectDialog({ create = false }, { title, genre, premise -> create = false; state.createProject(title, genre, premise) })
    if (ideate) GuidedIdeationDialog(state, { ideate = false })
}

@Composable
private fun GuidedIdeationDialog(state: AppState, onDismiss: () -> Unit) {
    var seed by remember(state.ideationDraft?.id) { mutableStateOf(state.ideationDraft?.premise.orEmpty()) }
    var genre by remember(state.ideationDraft?.id) { mutableStateOf(state.ideationDraft?.genre.orEmpty()) }
    val draft = state.ideationDraft
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 灵感开书") },
        text = {
            Column {
                if (draft == null) {
                    OutlinedTextField(seed, { seed = it }, Modifier.fillMaxWidth(), label = { Text("灵感") }, minLines = 3)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(genre, { genre = it }, Modifier.fillMaxWidth(), label = { Text("偏好题材") }, singleLine = true)
                } else {
                    Text(draft.title, fontWeight = FontWeight.SemiBold)
                    Text("${draft.genre} · ${draft.premise}")
                    Spacer(Modifier.height(6.dp)); Text("主角：${draft.protagonist}\n长期冲突：${draft.conflict}\n读者承诺：${draft.promise}", color = Color.Gray)
                }
            }
        },
        confirmButton = {
            if (draft == null) Button(onClick = {
                if (state.modelConfig.baseUrl.isBlank() || state.modelConfig.apiKey.isBlank() || state.modelConfig.model.isBlank()) {
                    state.saveIdeationDraft(IdeationDraft(title = seed.lineSequence().firstOrNull().orEmpty().take(30), genre = genre, premise = seed, conflict = "待展开"))
                    state.createFromIdeationDraft()
                    onDismiss()
                } else state.generateGuidedIdeation(seed, genre)
            }, enabled = !state.busy && seed.isNotBlank()) { Text(if (state.modelConfig.baseUrl.isBlank() || state.modelConfig.apiKey.isBlank() || state.modelConfig.model.isBlank()) "直接开始" else "生成开书资料") }
            else Button(onClick = { state.createFromIdeationDraft(); onDismiss() }) { Text("创建作品") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (draft == null && seed.isNotBlank()) state.saveIdeationDraft(IdeationDraft(title = seed.lineSequence().firstOrNull().orEmpty().take(30), genre = genre, premise = seed, conflict = "待展开"))
                onDismiss()
            }) { Text(if (draft == null) "稍后再说" else "取消") }
        },
    )
}

@Composable private fun Workspace(state:AppState){val p=state.selectedProject?:return;Column(Modifier.fillMaxSize()){Row(Modifier.height(64.dp).fillMaxWidth().background(Color.White).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Column{Text(p.title,fontSize=20.sp,fontWeight=FontWeight.SemiBold);Text("${state.chapters.size} 章",fontSize=12.sp,color=Color.Gray)};Spacer(Modifier.weight(1f));WorkspaceTab.entries.forEach{tab->TextButton({state.tab=tab},colors=ButtonDefaults.textButtonColors(contentColor=if(state.tab==tab)Brand else Color.Gray)){Text(tab.label)}};Spacer(Modifier.width(16.dp));IconButton({state.flushSave();state.section=MainSection.SHELF}){Icon(Icons.Outlined.Close,"关闭作品")}}
        HorizontalDivider(color=Rule);when(state.tab){WorkspaceTab.WRITE->WriteWorkspaceV2(state);WorkspaceTab.PROJECT->ProjectPanel(state);WorkspaceTab.OUTLINE->OutlinePanelV2(state);WorkspaceTab.RESOURCES->ResourcesPanelV2(state);WorkspaceTab.REVIEW->ReviewPanelV2(state)}}
}

@Composable private fun WriteWorkspace(state:AppState){var search by remember{mutableStateOf("")};var direction by remember{mutableStateOf("")};Row(Modifier.fillMaxSize()){
    Column(Modifier.width(250.dp).fillMaxHeight().background(Color.White)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("章节",fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));IconButton(state::addChapter){Icon(Icons.Outlined.Add,"新增章节")}};OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=10.dp),singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)},placeholder={Text("搜索全书")});Spacer(Modifier.height(8.dp));LazyColumn(Modifier.weight(1f)){val list=if(search.length>=2)state.search(search).map{it.chapter}else state.chapters;items(list,key={it.id}){c->Row(Modifier.fillMaxWidth().clickable{state.selectChapter(c.id)}.background(if(state.selectedChapter?.id==c.id)Color(0xFFE5F2EE) else Color.Transparent).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(c.number.toString().padStart(2,'0'),color=Color.Gray,fontSize=12.sp);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(c.title,maxLines=1);Text("${c.content.length} 字",fontSize=11.sp,color=Color.Gray)}}}};TextButton(state::deleteChapter,enabled=state.selectedChapter!=null,modifier=Modifier.align(Alignment.CenterHorizontally)){Icon(Icons.Outlined.Delete,null);Text("删除本章")}}
    VerticalDivider(color=Rule);Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=24.dp,vertical=16.dp)){state.selectedChapter?.let{c->Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(c.title,state::renameChapter,Modifier.weight(1f),singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=20.sp,fontWeight=FontWeight.SemiBold));Spacer(Modifier.width(12.dp));Text("${state.editorText.length} 字 · ${state.saveStatus.label}",color=if(state.saveStatus==SaveStatus.FAILED)MaterialTheme.colorScheme.error else Color.Gray)};Spacer(Modifier.height(12.dp));OutlinedTextField(state.editorText,state::editContent,Modifier.fillMaxSize(),placeholder={Text("从这里开始写作……")})}?:Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("请选择章节",color=Color.Gray)}}
    VerticalDivider(color=Rule);Column(Modifier.width(310.dp).fillMaxHeight().background(Color.White).padding(16.dp)){Text("AI 创作",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(12.dp));OutlinedTextField(direction,{direction=it},Modifier.fillMaxWidth(),label={Text("续写方向")},minLines=3);Spacer(Modifier.height(10.dp));Button({state.continueWriting(direction)},Modifier.fillMaxWidth(),enabled=!state.busy&&state.selectedChapter!=null){Icon(Icons.Outlined.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text(if(state.busy)"生成中" else "续写正文")};Spacer(Modifier.height(6.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton(state::writeFullChapter,Modifier.weight(1f),enabled=!state.busy&&state.selectedChapter!=null){Text("完整章节",fontSize=12.sp)};OutlinedButton(state::generateChapterTitle,Modifier.weight(1f),enabled=!state.busy&&state.selectedChapter!=null){Text("生成标题",fontSize=12.sp)}};Spacer(Modifier.height(6.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton(state::rewriteChapter,Modifier.weight(1f),enabled=!state.busy&&state.selectedChapter!=null){Text("AI 改写",fontSize=12.sp)};OutlinedButton(state::humanizeChapter,Modifier.weight(1f),enabled=!state.busy&&state.selectedChapter!=null){Text("去 AI 味",fontSize=12.sp)}};if(state.busy){TextButton(state::cancelGeneration,Modifier.fillMaxWidth()){Icon(Icons.Outlined.Stop,null);Text("取消并保留内容")}};if(state.busy&&state.streamedText.isNotBlank()){LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.streamedText.takeLast(500),fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=8.dp))};HorizontalDivider(Modifier.padding(vertical=16.dp));Text("本地检查",fontWeight=FontWeight.SemiBold);val issues=state.qualityIssues();if(issues.isEmpty())Text("当前未发现明显问题",color=Brand,modifier=Modifier.padding(top=8.dp))else LazyColumn{items(issues){issue->Text("• ${issue.title}\n${issue.detail}",fontSize=12.sp,color=if(issue.severity==QualitySeverity.WARNING)Warm else Color.Gray,modifier=Modifier.padding(vertical=5.dp))}}}
}}

@Composable
private fun WriteWorkspaceV2(state: AppState) {
    var search by remember { mutableStateOf("") }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("") }
    var confirmDeleteChapter by remember { mutableStateOf(false) }
    var nextChapterDialog by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf(state.workspaceLayout) }
    fun updateLayout(value: WorkspaceLayout, persist: Boolean = false) {
        layout = value
        state.updateWorkspaceLayout(value, persist)
    }
    val chapterTreeWidth = if (layout.chapterTreeCollapsed) 42f else layout.chapterTreeWidth
    val aiPanelWidth = if (layout.aiPanelCollapsed) 42f else layout.aiPanelWidth
    Row(Modifier.fillMaxSize()) {
        if (layout.chapterTreeCollapsed) {
            Column(Modifier.width(chapterTreeWidth.dp).fillMaxHeight().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton({ updateLayout(layout.copy(chapterTreeCollapsed = false), persist = true) }) { Icon(Icons.Outlined.ChevronRight, "展开章节栏") }
            }
        } else Column(Modifier.width(chapterTreeWidth.dp).fillMaxHeight().background(Color.White)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text("章节", fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); IconButton({ nextChapterDialog = true }, enabled = !state.busy) { Icon(Icons.Outlined.Add, "新建下一章") }; IconButton({ updateLayout(layout.copy(chapterTreeCollapsed = true), persist = true) }) { Icon(Icons.Outlined.ChevronLeft, "收起章节栏") } }
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 10.dp), placeholder = { Text("搜索全文") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                val list = if (search.length >= 2) state.search(search).map { it.chapter } else state.chapters
                items(list, key = { it.id }) { chapter ->
                    Row(Modifier.fillMaxWidth().clickable { state.selectChapter(chapter.id) }.background(if (state.selectedChapter?.id == chapter.id) Color(0xFFE5F2EE) else Color.Transparent).padding(12.dp)) {
                        Text(chapter.number.toString().padStart(2, '0'), color = Color.Gray, fontSize = 12.sp); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(chapter.title, maxLines = 1); Text("${chapter.content.length} 字", color = Color.Gray, fontSize = 11.sp) }
                    }
                }
            }
            TextButton({ confirmDeleteChapter = true }, enabled = state.selectedChapter != null, modifier = Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Outlined.Delete, null); Text("删除本章") }
        }
        ResizeDivider(
            onResize = { delta -> updateLayout(layout.copy(chapterTreeWidth = (layout.chapterTreeWidth + delta).coerceIn(180f, 420f))) },
            onResizeFinished = { state.updateWorkspaceLayout(layout, persist = true) },
        )
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 12.dp)) {
            val chapter = state.selectedChapter
            if (chapter == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("请选择章节", color = Color.Gray) }
            else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(chapter.title, state::renameChapter, Modifier.weight(1f), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.width(8.dp)); Text("${state.editorText.length} 字 · ${state.saveStatus.label}", color = Color.Gray, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    IconButton(state::undoEditor, enabled = state.canUndo()) { Icon(Icons.Outlined.Undo, "撤销") }
                    IconButton(state::redoEditor, enabled = state.canRedo()) { Icon(Icons.Outlined.Redo, "重做") }
                    OutlinedTextField(find, { find = it }, Modifier.weight(1f), label = { Text("查找") }, singleLine = true)
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(replace, { replace = it }, Modifier.weight(1f), label = { Text("替换为") }, singleLine = true)
                    Spacer(Modifier.width(6.dp))
                    TextButton({ state.replaceAllInEditor(find, replace) }, enabled = find.isNotBlank()) { Text("全部替换") }
                }
                OutlinedTextField(state.editorText, state::editContent, Modifier.fillMaxSize().padding(top = 8.dp), placeholder = { Text("从这里开始写作…") })
            }
        }
        ResizeDivider(
            onResize = { delta -> updateLayout(layout.copy(aiPanelWidth = (layout.aiPanelWidth - delta).coerceIn(260f, 460f))) },
            onResizeFinished = { state.updateWorkspaceLayout(layout, persist = true) },
        )
        if (layout.aiPanelCollapsed) {
            Column(Modifier.width(aiPanelWidth.dp).fillMaxHeight().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton({ updateLayout(layout.copy(aiPanelCollapsed = false), persist = true) }) { Icon(Icons.Outlined.ChevronLeft, "展开 AI 面板") }
            }
        } else Column(Modifier.width(aiPanelWidth.dp).fillMaxHeight().background(Color.White).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI 创作", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton({ updateLayout(layout.copy(aiPanelCollapsed = true), persist = true) }) { Icon(Icons.Outlined.ChevronRight, "收起 AI 面板") }
            }
            OutlinedTextField(direction, { direction = it }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("续写方向") }, minLines = 3)
            Button({ state.continueWriting(direction) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !state.busy && state.selectedChapter != null) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("续写正文") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(state::writeFullChapter, Modifier.weight(1f), enabled = !state.busy && state.selectedChapter != null) { Text("完整章节", fontSize = 12.sp) }; OutlinedButton(state::generateChapterTitle, Modifier.weight(1f), enabled = !state.busy && state.selectedChapter != null) { Text("生成标题", fontSize = 12.sp) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(state::rewriteChapter, Modifier.weight(1f), enabled = !state.busy && state.selectedChapter != null) { Text("AI 改写", fontSize = 12.sp) }; OutlinedButton(state::humanizeChapter, Modifier.weight(1f), enabled = !state.busy && state.selectedChapter != null) { Text("去 AI 味", fontSize = 12.sp) } }
            if (state.busy) TextButton(state::cancelGeneration, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Stop, null); Text("取消并保留草稿") }
            if (state.busy && state.streamedText.isNotBlank()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.streamedText.takeLast(500), fontSize = 12.sp, color = Color.Gray) }
        }
    }
    if (confirmDeleteChapter) AlertDialog(
        onDismissRequest = { confirmDeleteChapter = false },
        title = { Text("删除当前章节？") },
        text = { Text("正文、历史版本、审核记录和关联索引都会被删除。") },
        confirmButton = { Button({ confirmDeleteChapter = false; state.deleteChapter() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") } },
        dismissButton = { TextButton({ confirmDeleteChapter = false }) { Text("取消") } },
    )
    if (nextChapterDialog) AlertDialog(
        onDismissRequest = { nextChapterDialog = false },
        title = { Text("新建下一章") },
        text = { Text("当前章节会先完成记忆、RAG 和质量闭环；通过后再创建下一章。") },
        confirmButton = { Button({ nextChapterDialog = false; state.addChapter(NextChapterAction.GENERATE_WITH_AI) }) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("创建并由 AI 生成") } },
        dismissButton = { Row { OutlinedButton({ nextChapterDialog = false; state.addChapter(NextChapterAction.CREATE_BLANK) }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("创建空白章节") }; TextButton({ nextChapterDialog = false }) { Text("取消") } } },
    )
}

@Composable private fun ProjectPanel(state: AppState) {
    val project = state.selectedProject ?: return
    var edit by remember(project) { mutableStateOf(project) }
    var coverPrompt by remember(project.id) { mutableStateOf("") }
    var exportMenu by remember { mutableStateOf(false) }
    var confirmDeleteProject by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Text("作品资料", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        val analysis = state.importAnalysisRun
        val analysisRunning = analysis?.status in setOf(ImportAnalysisStatus.QUEUED, ImportAnalysisStatus.RUNNING)
        Surface(shape = MaterialTheme.shapes.small, color = Color.White, border = BorderStroke(1.dp, Rule)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, null, tint = Brand)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(when (analysis?.status) {
                        ImportAnalysisStatus.QUEUED -> "导入分析排队中"
                        ImportAnalysisStatus.RUNNING -> analysis.stage
                        ImportAnalysisStatus.WAITING_FOR_CONFIG -> "导入分析需要模型配置"
                        ImportAnalysisStatus.COMPLETED -> "导入分析已完成"
                        ImportAnalysisStatus.CANCELLED -> "导入分析已取消"
                        ImportAnalysisStatus.FAILED -> "导入分析失败"
                        else -> "从现有正文提炼作品资料与文风"
                    })
                    Text(analysis?.let { "${it.progress}% · ${it.detail}" } ?: "不会覆盖正文，可随时重新分析", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
                if (analysisRunning) IconButton(state::cancelImportAnalysis) { Icon(Icons.Outlined.Close, "取消导入分析") }
                else TextButton(state::startImportAnalysis, enabled = !state.busy) { Text(if (analysis?.status == ImportAnalysisStatus.COMPLETED) "重新分析" else "开始") }
            }
        }
        Spacer(Modifier.height(18.dp))
        Field("作品名称", edit.title) { edit = edit.copy(title = it) }
        Field("类型", edit.genre) { edit = edit.copy(genre = it) }
        Field("核心设定", edit.premise, 5) { edit = edit.copy(premise = it) }
        Field("目标读者", edit.targetAudience) { edit = edit.copy(targetAudience = it) }
        Field("主角", edit.protagonistName) { edit = edit.copy(protagonistName = it) }
        Field("标签", edit.tags) { edit = edit.copy(tags = it) }
        Field("长篇蓝图", edit.longFormBlueprint, 8) { edit = edit.copy(longFormBlueprint = it) }
        Field("禁写内容", edit.forbiddenContent, 3) { edit = edit.copy(forbiddenContent = it) }
        Text("创作策略", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(edit.targetChapterCount.takeIf { it > 0 }?.toString().orEmpty(), { edit = edit.copy(targetChapterCount = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, Modifier.weight(1f), label = { Text("目标章节") }, singleLine = true)
            OutlinedTextField(edit.targetWordCount.takeIf { it > 0 }?.toString().orEmpty(), { edit = edit.copy(targetWordCount = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, Modifier.weight(1f), label = { Text("全书目标字数") }, singleLine = true)
            OutlinedTextField(edit.pacingProfile, { edit = edit.copy(pacingProfile = it) }, Modifier.weight(1f), label = { Text("全书节奏") }, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(edit.automationLevel, { edit = edit.copy(automationLevel = it) }, Modifier.weight(1f), label = { Text("自动化等级") }, singleLine = true)
            OutlinedTextField(edit.targetChapterWordCount.toString(), { edit = edit.copy(targetChapterWordCount = (it.filter(Char::isDigit).toIntOrNull() ?: 3000).coerceIn(400, 20_000)) }, Modifier.weight(1f), label = { Text("单章最少字数") }, singleLine = true)
            OutlinedTextField(edit.targetChapterWordCountMax.toString(), { edit = edit.copy(targetChapterWordCountMax = (it.filter(Char::isDigit).toIntOrNull() ?: 5000).coerceIn(edit.targetChapterWordCount, 30_000)) }, Modifier.weight(1f), label = { Text("单章最多字数") }, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(state::generateProjectProfile, enabled = !state.busy) { Icon(Icons.Outlined.AutoAwesome, null); Text("生成作品设定") }
            OutlinedButton(state::generateLongFormBlueprint, enabled = !state.busy) { Icon(Icons.Outlined.AutoAwesome, null); Text("生成长篇蓝图") }
            OutlinedButton(state::extractStyleGuide, enabled = !state.busy && state.selectedChapter != null) { Icon(Icons.Outlined.AutoAwesome, null); Text("提取当前文风") }
        }
        Spacer(Modifier.height(12.dp))
        Text("批量写作", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        state.resumableAutoWriteRun?.let { run ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${run.completedCount}/${run.requestedCount} 章，${run.detail}", color = Color.Gray, modifier = Modifier.weight(1f), maxLines = 1)
                OutlinedButton(state::resumeAutoWrite, enabled = !state.busy) { Icon(Icons.Outlined.PlayArrow, null); Text("继续") }
            }
        } ?: Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ state.autoWriteChapters(1) }, enabled = !state.busy) { Text("生成 1 章") }
            OutlinedButton({ state.autoWriteChapters(3) }, enabled = !state.busy) { Text("生成 3 章") }
            OutlinedButton({ state.autoWriteChapters(5) }, enabled = !state.busy) { Text("生成 5 章") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(state::saveContinuitySnapshot, enabled = state.selectedChapter != null && !state.busy) { Icon(Icons.Outlined.BookmarkAdd, null); Text("保存当前章节连续性快照") }
            TextButton(state::runSelectedChapterLifecycle, enabled = state.selectedChapter != null && !state.busy) { Icon(Icons.Outlined.Psychology, null); Text("运行章节闭环") }
        }
        Spacer(Modifier.height(20.dp))
        Text("作品封面", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(if (project.coverPath.isBlank()) "尚未设置封面" else project.coverPath, color = Color.Gray, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(coverPrompt, { coverPrompt = it }, Modifier.fillMaxWidth(), label = { Text("封面提示词") }, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ pickOpen(listOf("png", "jpg", "jpeg", "webp"))?.let(state::importCover) }, enabled = !state.busy) { Icon(Icons.Outlined.FileOpen, null); Text("导入封面") }
            OutlinedButton({ state.generateCover(coverPrompt) }, enabled = !state.busy) { Icon(Icons.Outlined.AutoAwesome, null); Text("生成封面") }
            TextButton(state::testImageModel, enabled = !state.busy) { Text("测试图片模型") }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Button({ state.updateProject(edit) }) { Icon(Icons.Outlined.Save, null); Text("保存作品资料") }
            Spacer(Modifier.width(12.dp))
            Box {
                OutlinedButton({ exportMenu = true }) { Icon(Icons.Outlined.FileDownload, null); Text("导出正文"); Icon(Icons.Outlined.ArrowDropDown, null) }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    listOf(
                        Triple("Markdown (.md)", "markdown", "md"),
                        Triple("Word (.docx)", "docx", "docx"),
                        Triple("EPUB (.epub)", "epub", "epub"),
                        Triple("PDF (.pdf)", "pdf", "pdf"),
                    ).forEach { (label, format, extension) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            exportMenu = false
                            pickSave("${project.title}.$extension")?.let { state.exportDocument(it, format) }
                        })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ state.exportBackup(pickSave("${project.title}.noveledit.json") ?: return@OutlinedButton) }) { Icon(Icons.Outlined.Backup, null); Text("项目备份") }
            Spacer(Modifier.weight(1f))
            TextButton({ confirmDeleteProject = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Text("删除作品") }
        }
    }
    if (confirmDeleteProject) AlertDialog(
        onDismissRequest = { confirmDeleteProject = false },
        title = { Text("删除作品？") },
        text = { Text("将删除作品、全部章节、资料、审核记录和本地索引。此操作无法撤销。") },
        confirmButton = { Button({ confirmDeleteProject = false; state.deleteProject() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") } },
        dismissButton = { TextButton({ confirmDeleteProject = false }) { Text("取消") } },
    )
}

@Composable private fun OutlinePanel(state:AppState){val c=state.selectedChapter;Column(Modifier.fillMaxSize().padding(28.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("大纲与分镜",fontSize=24.sp,fontWeight=FontWeight.SemiBold);Text(c?.title?:"未选择章节",color=Color.Gray)};OutlinedButton(state::generateChapterPlan,enabled=!state.busy&&c!=null){Icon(Icons.Outlined.AutoAwesome,null);Text("生成计划")};Spacer(Modifier.width(8.dp));OutlinedButton(state::generateBeatSheet,enabled=!state.busy&&c!=null){Icon(Icons.Outlined.AutoAwesome,null);Text("生成分镜")}};Spacer(Modifier.height(16.dp));Row(Modifier.fillMaxSize()){OutlinedTextField(c?.outline.orEmpty(),state::updateOutline,Modifier.weight(1f).fillMaxHeight(),label={Text("章节计划")});Spacer(Modifier.width(16.dp));OutlinedTextField(c?.beatSheet.orEmpty(),state::updateBeatSheet,Modifier.weight(1f).fillMaxHeight(),label={Text("分镜与节拍")})}}}

@Composable
private fun OutlinePanelV2(state: AppState) {
    val project = state.selectedProject ?: return
    val chapter = state.selectedChapter
    var eventType by remember { mutableStateOf("冲突升级") }
    var eventPace by remember { mutableStateOf("中") }
    var eventNote by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var ruleLabel by remember { mutableStateOf("") }
    var ruleCooldown by remember { mutableStateOf("3") }
    var outlineChange by remember { mutableStateOf("") }
    var editingRule by remember { mutableStateOf<EventMatrixRule?>(null) }
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("大纲与节奏", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(chapter?.let { "第 ${it.number} 章 · ${it.title}" } ?: "未选择章节", color = Color.Gray)
            }
            OutlinedButton(state::generateChapterPlan, enabled = !state.busy && chapter != null) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("生成章节计划") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(state::generateBeatSheet, enabled = !state.busy && chapter != null) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("生成分镜") }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(outlineChange, { outlineChange = it }, Modifier.weight(1f), label = { Text("改纲说明") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ state.analyzeOutlineCascade(outlineChange) }, enabled = chapter != null) { Icon(Icons.Outlined.AccountTree, null); Spacer(Modifier.width(6.dp)); Text("分析影响") }
        }
        state.outlineCascadeReport?.let { report ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(report.summary, color = Warm, modifier = Modifier.weight(1f), maxLines = 2)
                TextButton(state::resolveOutlineCascade) { Text("确认已处理") }
            }
        }
        OutlinedTextField(
            chapter?.targetWordCount?.takeIf { it > 0 }?.toString().orEmpty(),
            { state.updateChapterTargetWordCount(it.filter(Char::isDigit).toIntOrNull() ?: 0) },
            Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("本章目标字数，可留空") },
            singleLine = true,
            enabled = chapter != null,
        )
        Row(Modifier.heightIn(min = 280.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(chapter?.outline.orEmpty(), state::updateOutline, Modifier.weight(1f).fillMaxHeight(), label = { Text("章节计划") })
            OutlinedTextField(chapter?.beatSheet.orEmpty(), state::updateBeatSheet, Modifier.weight(1f).fillMaxHeight(), label = { Text("场景分镜") })
        }
        HorizontalDivider(Modifier.padding(vertical = 18.dp))
        Text("章节节奏", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        state.pacingRecommendation()?.let { recommendation -> Text("建议：${recommendation.pace}档 · ${recommendation.eventType}。${recommendation.reason}", color = Brand, modifier = Modifier.padding(top = 4.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(eventType, { eventType = it }, Modifier.weight(1f), label = { Text("事件类型") }, singleLine = true)
            OutlinedTextField(eventPace, { eventPace = it }, Modifier.width(100.dp), label = { Text("节奏") }, singleLine = true)
            OutlinedTextField(eventNote, { eventNote = it }, Modifier.weight(1f), label = { Text("说明") }, singleLine = true)
            Button(onClick = { state.savePacingEvent(eventType, eventPace, eventNote); eventNote = "" }, enabled = chapter != null) { Text("登记") }
        }
        state.pacingEvents.takeLast(5).forEach { event -> ListItem(headlineContent = { Text("第 ${event.chapterNumber} 章 · ${event.eventType} · ${event.pace}档") }, supportingContent = { Text(event.note) }) }
        HorizontalDivider(Modifier.padding(vertical = 18.dp))
        Text("文风档案", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(profileName, { profileName = it }, Modifier.weight(1f), label = { Text("档案名称") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ state.extractStyleGuide() }, enabled = !state.busy && chapter != null) { Text("从本章提取") }
            Spacer(Modifier.width(8.dp))
            Button({ state.saveCurrentStyleProfile(profileName); profileName = "" }, enabled = profileName.isNotBlank() && project.styleGuide.isNotBlank()) { Text("保存档案") }
        }
        state.styleProfiles.forEach { profile ->
            ListItem(headlineContent = { Text(profile.name) }, supportingContent = { Text(profile.guide, maxLines = 2) }, trailingContent = { Row { TextButton({ state.applyStyleProfile(profile) }) { Text("应用") }; IconButton({ state.deleteStyleProfile(profile.id) }) { Icon(Icons.Outlined.Delete, "删除档案") } } })
        }
        HorizontalDivider(Modifier.padding(vertical = 18.dp))
        Text("事件矩阵", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(ruleLabel, { ruleLabel = it }, Modifier.weight(1f), label = { Text("事件规则") }, singleLine = true)
            OutlinedTextField(ruleCooldown, { ruleCooldown = it }, Modifier.width(110.dp), label = { Text("冷却章数") }, singleLine = true)
            Button({ state.addEventMatrixRule(ruleLabel, ruleCooldown.toIntOrNull() ?: 0, "自定义"); ruleLabel = "" }, enabled = ruleLabel.isNotBlank()) { Text("添加") }
        }
        state.eventMatrixRules.forEach { rule ->
            ListItem(
                headlineContent = { Text(rule.label) },
                supportingContent = { Text("${rule.category} · 间隔 ${rule.cooldown} 章 · ${if (rule.enabled) "启用" else "停用"}") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = rule.enabled, onCheckedChange = { state.updateEventMatrixRule(rule.copy(enabled = it)) })
                        IconButton({ editingRule = rule }) { Icon(Icons.Outlined.Edit, "编辑规则") }
                        IconButton({ state.deleteEventMatrixRule(rule.id) }) { Icon(Icons.Outlined.Delete, "删除规则") }
                    }
                },
            )
        }
    }
    editingRule?.let { rule ->
        EditEventMatrixRuleDialog(
            rule = rule,
            onDismiss = { editingRule = null },
            onSave = { state.updateEventMatrixRule(it); editingRule = null },
        )
    }
}

@Composable
private fun EditEventMatrixRuleDialog(rule: EventMatrixRule, onDismiss: () -> Unit, onSave: (EventMatrixRule) -> Unit) {
    var label by remember(rule.id) { mutableStateOf(rule.label) }
    var category by remember(rule.id) { mutableStateOf(rule.category) }
    var cooldown by remember(rule.id) { mutableStateOf(rule.cooldown.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑事件规则") },
        text = {
            Column {
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("事件规则") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("分类") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(cooldown, { cooldown = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("冷却章节数") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(rule.copy(label = label, category = category, cooldown = cooldown.toIntOrNull() ?: rule.cooldown)) }, enabled = label.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ResourcesPanelV2(state: AppState) {
    var kind by remember { mutableStateOf("人物") }
    var name by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var itemStatus by remember { mutableStateOf(StoryItemStatus.ACTIVE) }
    var sourceId by remember(state.selectedProject?.id) { mutableStateOf<Long?>(null) }
    var targetId by remember(state.selectedProject?.id) { mutableStateOf<Long?>(null) }
    var relation by remember { mutableStateOf("关联") }
    var relationDescription by remember { mutableStateOf("") }
    var relationSinceChapter by remember { mutableStateOf("1") }
    var anchorTitle by remember { mutableStateOf("") }
    var anchorConflict by remember { mutableStateOf("") }
    var anchorAllowedPlot by remember { mutableStateOf("") }
    var anchorForbiddenReveals by remember { mutableStateOf("") }
    var anchorMandatoryTension by remember { mutableStateOf("") }
    var anchorStart by remember { mutableStateOf("1") }
    var anchorEnd by remember { mutableStateOf("10") }
    var noteTitle by remember { mutableStateOf("") }
    var noteSource by remember { mutableStateOf("") }
    var noteTags by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var noteRightsConfirmed by remember { mutableStateOf(false) }
    var researchQuery by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var editingItem by remember { mutableStateOf<StoryItem?>(null) }
    var editingAnchor by remember { mutableStateOf<StoryAnchor?>(null) }
    var editingNote by remember { mutableStateOf<ResearchNote?>(null) }

    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("故事资料", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(kind, { kind = it }, Modifier.width(100.dp), label = { Text("类型") }, singleLine = true)
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("名称") }, singleLine = true)
            }
            OutlinedTextField(detail, { detail = it }, Modifier.fillMaxWidth(), label = { Text("详情") }, minLines = 2)
            OutlinedTextField(itemStatus, { itemStatus = it }, Modifier.fillMaxWidth(), label = { Text("资料状态") }, singleLine = true)
            Button(
                onClick = { state.addStoryItem(kind, name, detail, itemStatus); name = ""; detail = ""; itemStatus = StoryItemStatus.ACTIVE },
                enabled = name.isNotBlank(),
            ) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("添加资料卡") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.storyItems, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text("${item.kind} · ${item.name} · ${item.status}") },
                        supportingContent = { Text(item.detail.ifBlank { "未填写详情" }, maxLines = 2) },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { sourceId = item.id }) { Text(if (sourceId == item.id) "已选起点" else "设为起点") }
                                TextButton(onClick = { targetId = item.id }) { Text(if (targetId == item.id) "已选终点" else "设为终点") }
                                IconButton(onClick = { editingItem = item }) { Icon(Icons.Outlined.Edit, "编辑资料卡") }
                                IconButton(onClick = { confirmDelete = "删除资料卡“${item.name}”及其关系？" to { state.deleteStoryItem(item.id) } }) { Icon(Icons.Outlined.Delete, "删除资料卡") }
                            }
                        },
                    )
                    HorizontalDivider(color = Rule)
                }
            }
        }

        VerticalDivider(color = Rule)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("关系图谱", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            val sourceName = state.storyItems.firstOrNull { it.id == sourceId }?.name ?: "未选"
            val targetName = state.storyItems.firstOrNull { it.id == targetId }?.name ?: "未选"
            Text("起点：$sourceName", color = Color.Gray)
            Text("终点：$targetName", color = Color.Gray)
            OutlinedTextField(relation, { relation = it }, Modifier.fillMaxWidth(), label = { Text("关系") }, singleLine = true)
            OutlinedTextField(relationDescription, { relationDescription = it }, Modifier.fillMaxWidth(), label = { Text("关系说明") }, singleLine = true)
            OutlinedTextField(relationSinceChapter, { relationSinceChapter = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("从第几章起") }, singleLine = true)
            Button(
                onClick = {
                    val source = sourceId ?: return@Button
                    val target = targetId ?: return@Button
                    state.addStoryEdge(source, target, relation, relationDescription, relationSinceChapter.toIntOrNull() ?: 1)
                    targetId = null
                },
                enabled = sourceId != null && targetId != null && sourceId != targetId && relation.isNotBlank(),
            ) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("添加关系") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.storyEdges, key = { it.id }) { edge ->
                    val source = state.storyItems.firstOrNull { it.id == edge.sourceItemId }?.name ?: "已删除"
                    val target = state.storyItems.firstOrNull { it.id == edge.targetItemId }?.name ?: "已删除"
                    ListItem(
                        headlineContent = { Text("$source - ${edge.relation} -> $target") },
                        supportingContent = { Text("自第 ${edge.sinceChapter} 章起${if (edge.description.isBlank()) "" else " · ${edge.description}"}") },
                        trailingContent = { IconButton(onClick = { confirmDelete = "删除这条关系？" to { state.deleteStoryEdge(edge.id) } }) { Icon(Icons.Outlined.Delete, "删除关系") } },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("故事锚点", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(anchorStart, { anchorStart = it }, Modifier.weight(1f), label = { Text("起始章") }, singleLine = true)
                OutlinedTextField(anchorEnd, { anchorEnd = it }, Modifier.weight(1f), label = { Text("结束章") }, singleLine = true)
            }
            OutlinedTextField(anchorTitle, { anchorTitle = it }, Modifier.fillMaxWidth(), label = { Text("锚点标题") }, singleLine = true)
            OutlinedTextField(anchorConflict, { anchorConflict = it }, Modifier.fillMaxWidth(), label = { Text("核心冲突") }, singleLine = true)
            OutlinedTextField(anchorAllowedPlot, { anchorAllowedPlot = it }, Modifier.fillMaxWidth(), label = { Text("允许推进") }, minLines = 2)
            OutlinedTextField(anchorForbiddenReveals, { anchorForbiddenReveals = it }, Modifier.fillMaxWidth(), label = { Text("禁止揭露") }, minLines = 2)
            OutlinedTextField(anchorMandatoryTension, { anchorMandatoryTension = it }, Modifier.fillMaxWidth(), label = { Text("必要张力") }, minLines = 2)
            TextButton(
                onClick = {
                    state.addAnchor(anchorStart.toIntOrNull() ?: 1, anchorEnd.toIntOrNull() ?: 1, anchorTitle, anchorConflict, anchorAllowedPlot, anchorForbiddenReveals, anchorMandatoryTension)
                    anchorTitle = ""; anchorConflict = ""; anchorAllowedPlot = ""; anchorForbiddenReveals = ""; anchorMandatoryTension = ""
                },
                enabled = anchorTitle.isNotBlank() && anchorConflict.isNotBlank(),
            ) { Icon(Icons.Outlined.Add, null); Text("添加锚点") }
            state.anchors.take(3).forEach { anchor ->
                ListItem(
                    headlineContent = { Text("第 ${anchor.startChapter}-${anchor.endChapter} 章：${anchor.title}") },
                    supportingContent = { Text(listOf(anchor.coreConflict, anchor.allowedPlot.takeIf { it.isNotBlank() }?.let { "推进：$it" }, anchor.forbiddenReveals.takeIf { it.isNotBlank() }?.let { "禁区：$it" }, anchor.mandatoryTension.takeIf { it.isNotBlank() }?.let { "张力：$it" }).filterNotNull().joinToString(" · "), maxLines = 2) },
                    trailingContent = { Column { IconButton(onClick = { editingAnchor = anchor }) { Icon(Icons.Outlined.Edit, "编辑锚点") }; IconButton(onClick = { confirmDelete = "删除故事锚点“${anchor.title}”？" to { state.deleteAnchor(anchor.id) } }) { Icon(Icons.Outlined.Delete, "删除锚点") } } },
                )
            }
        }

        VerticalDivider(color = Rule)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("研究笔记", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            state.researchPlan()?.let { plan ->
                Text("调研计划：${plan.quick}", color = Brand, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                if (plan.gaps.isNotEmpty()) Text("待补：${plan.gaps.joinToString("、")}", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(researchQuery, { researchQuery = it }, Modifier.weight(1f), label = { Text("联网调研关键词") }, singleLine = true)
                IconButton(onClick = { state.searchOnlineResearch(researchQuery) }, enabled = researchQuery.isNotBlank() && !state.busy) { Icon(Icons.Outlined.Search, "联网调研") }
            }
            if (state.onlineResearchResults.isNotEmpty()) {
                Text("公开资料检索结果", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                state.onlineResearchResults.take(3).forEach { result ->
                    ListItem(
                        headlineContent = { Text(result.title, maxLines = 1) },
                        supportingContent = { Text(result.excerpt, maxLines = 2) },
                        trailingContent = { Row { IconButton(onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(result.sourceUrl)) }.onFailure { state.message = "无法打开来源链接：${it.message}" } }) { Icon(Icons.Outlined.OpenInNew, "打开来源") }; IconButton(onClick = { state.saveResearchResult(result) }) { Icon(Icons.Outlined.Save, "保存为笔记") } } },
                    )
                }
                TextButton(onClick = state::clearOnlineResearchResults) { Text("清空检索结果") }
            }
            OutlinedTextField(noteTitle, { noteTitle = it }, Modifier.fillMaxWidth(), label = { Text("笔记标题") }, singleLine = true)
            OutlinedTextField(noteSource, { noteSource = it }, Modifier.fillMaxWidth(), label = { Text("来源链接") }, singleLine = true)
            OutlinedTextField(noteTags, { noteTags = it }, Modifier.fillMaxWidth(), label = { Text("标签") }, singleLine = true)
            OutlinedTextField(noteContent, { noteContent = it }, Modifier.fillMaxWidth(), label = { Text("内容") }, minLines = 3)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(noteRightsConfirmed, { noteRightsConfirmed = it }); Text("我确认这是自写摘要或已获授权，不含受保护正文", fontSize = 12.sp) }
            Button(onClick = { state.addNote(noteTitle, noteSource, noteTags, noteContent, noteRightsConfirmed); noteTitle = ""; noteSource = ""; noteTags = ""; noteContent = ""; noteRightsConfirmed = false }, enabled = noteTitle.isNotBlank() && noteContent.isNotBlank()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("保存笔记") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.notes, key = { it.id }) { note ->
                    ListItem(
                        headlineContent = { Text(note.title) },
                        supportingContent = { Text(note.content, maxLines = 4) },
                        trailingContent = {
                            Column {
                                IconButton(onClick = { state.analyzeReference(note) }) { Icon(Icons.Outlined.AutoAwesome, "分析参考结构") }
                                IconButton(onClick = { editingNote = note }) { Icon(Icons.Outlined.Edit, "编辑笔记") }
                                IconButton(onClick = { confirmDelete = "删除研究笔记“${note.title}”？" to { state.deleteNote(note.id) } }) { Icon(Icons.Outlined.Delete, "删除笔记") }
                            }
                        },
                    )
                    HorizontalDivider(color = Rule)
                }
            }
            if (state.referenceAnalysis.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("参考结构分析", fontWeight = FontWeight.SemiBold)
                Text(state.referenceAnalysis, maxLines = 8, color = Color.Gray)
            }
        }
    }

    confirmDelete?.let { (question, action) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("确认删除") },
            text = { Text(question) },
            confirmButton = { Button(onClick = { action(); confirmDelete = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
    editingItem?.let { item -> EditStoryItemDialog(item, { editingItem = null }, state::updateStoryItem) }
    editingAnchor?.let { anchor -> EditAnchorDialog(anchor, { editingAnchor = null }, state::updateAnchor) }
    editingNote?.let { note -> EditResearchNoteDialog(note, { editingNote = null }, state::updateNote) }
}

@Composable private fun ReviewPanel(state:AppState){Column(Modifier.fillMaxSize().padding(28.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("审核与版本",fontSize=24.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));OutlinedButton(state::runEditorialReview,enabled=!state.busy&&state.selectedChapter!=null){Icon(Icons.Outlined.RateReview,null);Text("AI 编辑审稿")}};Spacer(Modifier.height(16.dp));Row(Modifier.fillMaxSize()){Column(Modifier.weight(1f)){Text("质量检查",fontWeight=FontWeight.SemiBold);state.qualityIssues().forEach{issue->ListItem(headlineContent={Text(issue.title)},supportingContent={Text(issue.detail)},leadingContent={Icon(if(issue.severity==QualitySeverity.WARNING)Icons.Outlined.WarningAmber else Icons.Outlined.Info,null,tint=if(issue.severity==QualitySeverity.WARNING)Warm else Brand)})};if(state.editorialReviews.isNotEmpty()){HorizontalDivider(Modifier.padding(vertical=12.dp));Text("AI 审稿记录",fontWeight=FontWeight.SemiBold);LazyColumn{items(state.editorialReviews){review->ListItem(headlineContent={Text(review.content.take(80))},supportingContent={Text(review.content,maxLines=6)})}}}};VerticalDivider(Modifier.padding(horizontal=20.dp));Column(Modifier.weight(1f)){Text("正文版本",fontWeight=FontWeight.SemiBold);LazyColumn{items(state.revisions){r->ListItem(headlineContent={Text(r.reason)},supportingContent={Text(r.previousContent.take(100),maxLines=2)},trailingContent={TextButton({state.restoreRevision(r.id)}){Text("恢复")}})}}}}}}

@Composable
private fun EditStoryItemDialog(item: StoryItem, onDismiss: () -> Unit, onSave: (StoryItem) -> Unit) {
    var kind by remember(item.id) { mutableStateOf(item.kind) }
    var name by remember(item.id) { mutableStateOf(item.name) }
    var detail by remember(item.id) { mutableStateOf(item.detail) }
    var status by remember(item.id) { mutableStateOf(item.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑资料卡") },
        text = { Column { OutlinedTextField(kind, { kind = it }, Modifier.fillMaxWidth(), label = { Text("类型") }); OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称") }); OutlinedTextField(detail, { detail = it }, Modifier.fillMaxWidth(), label = { Text("详情") }, minLines = 3); OutlinedTextField(status, { status = it }, Modifier.fillMaxWidth(), label = { Text("状态") }) } },
        confirmButton = { Button(onClick = { onSave(item.copy(kind = kind.trim(), name = name.trim(), detail = detail.trim(), status = status.trim())); onDismiss() }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EditAnchorDialog(anchor: StoryAnchor, onDismiss: () -> Unit, onSave: (StoryAnchor) -> Unit) {
    var start by remember(anchor.id) { mutableStateOf(anchor.startChapter.toString()) }
    var end by remember(anchor.id) { mutableStateOf(anchor.endChapter.toString()) }
    var title by remember(anchor.id) { mutableStateOf(anchor.title) }
    var conflict by remember(anchor.id) { mutableStateOf(anchor.coreConflict) }
    var allowed by remember(anchor.id) { mutableStateOf(anchor.allowedPlot) }
    var forbidden by remember(anchor.id) { mutableStateOf(anchor.forbiddenReveals) }
    var tension by remember(anchor.id) { mutableStateOf(anchor.mandatoryTension) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑故事锚点") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Row { OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("起始章") }); Spacer(Modifier.width(8.dp)); OutlinedTextField(end, { end = it }, Modifier.weight(1f), label = { Text("结束章") }) }; OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") }); OutlinedTextField(conflict, { conflict = it }, Modifier.fillMaxWidth(), label = { Text("核心冲突") }, minLines = 2); OutlinedTextField(allowed, { allowed = it }, Modifier.fillMaxWidth(), label = { Text("允许剧情") }); OutlinedTextField(forbidden, { forbidden = it }, Modifier.fillMaxWidth(), label = { Text("禁止揭露") }); OutlinedTextField(tension, { tension = it }, Modifier.fillMaxWidth(), label = { Text("必要张力") }) } },
        confirmButton = { Button(onClick = { onSave(anchor.copy(startChapter = start.toIntOrNull() ?: anchor.startChapter, endChapter = end.toIntOrNull() ?: anchor.endChapter, title = title.trim(), coreConflict = conflict.trim(), allowedPlot = allowed.trim(), forbiddenReveals = forbidden.trim(), mandatoryTension = tension.trim())); onDismiss() }, enabled = title.isNotBlank() && conflict.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EditResearchNoteDialog(note: ResearchNote, onDismiss: () -> Unit, onSave: (ResearchNote) -> Unit) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var source by remember(note.id) { mutableStateOf(note.sourceUrl) }
    var tags by remember(note.id) { mutableStateOf(note.tags) }
    var content by remember(note.id) { mutableStateOf(note.content) }
    var rightsConfirmed by remember(note.id) { mutableStateOf(note.rightsConfirmed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑研究笔记") },
        text = { Column { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") }); OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("来源链接") }); OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("标签") }); OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), label = { Text("内容") }, minLines = 5); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rightsConfirmed, { rightsConfirmed = it }); Text("我确认这是自写摘要或已获授权", fontSize = 12.sp) } } },
        confirmButton = { Button(onClick = { onSave(note.copy(title = title.trim(), sourceUrl = source.trim(), tags = tags.trim(), content = content.trim(), rightsConfirmed = rightsConfirmed)); onDismiss() }, enabled = title.isNotBlank() && content.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ReviewPanelV2(state: AppState) {
    val chapter = state.selectedChapter
    var revisionToRestore by remember { mutableStateOf<Long?>(null) }
    var revisionToDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("审核与版本", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(chapter?.let { "第 ${it.number} 章 · ${ChapterLifecycleStatus.label(it.lifecycleStatus)}" } ?: "请选择章节", color = Color.Gray)
            }
            OutlinedButton(state::runSelectedChapterLifecycle, enabled = !state.busy && chapter?.content?.isNotBlank() == true) {
                Icon(Icons.Outlined.Psychology, null); Spacer(Modifier.width(6.dp)); Text("重试章节闭环")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(state::runEditorialReview, enabled = !state.busy && chapter != null) {
                Icon(Icons.Outlined.RateReview, null); Spacer(Modifier.width(6.dp)); Text("AI 编辑审稿")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(state::runEditorialTeamReview, enabled = !state.busy && chapter != null) {
                Icon(Icons.Outlined.Groups, null); Spacer(Modifier.width(6.dp)); Text("编辑团队")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { state.chapters.takeIf { it.isNotEmpty() }?.let { state.runBatchEditorialReview(it.first().number, it.last().number) } },
                enabled = !state.busy && state.chapters.any { it.content.isNotBlank() },
            ) { Icon(Icons.Outlined.PlaylistAddCheck, null); Spacer(Modifier.width(6.dp)); Text("全书批量审稿") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(state::generateRepairPlan, enabled = !state.busy && chapter != null) {
                Icon(Icons.Outlined.Build, null); Spacer(Modifier.width(6.dp)); Text("生成修复方案")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text("质量门禁", fontWeight = FontWeight.SemiBold)
                if (state.gateReports.isEmpty()) Text("尚未运行章节闭环", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                else LazyColumn(Modifier.weight(1f)) {
                    items(state.gateReports, key = { it.id }) { report ->
                        ListItem(
                            headlineContent = { Text(report.stage) },
                            supportingContent = { Text(report.content, maxLines = 4) },
                            leadingContent = { Icon(if (report.passed) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null, tint = if (report.passed) Brand else Warm) },
                        )
                        HorizontalDivider(color = Rule)
                    }
                }
                if (state.reviewIssues.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("批量审稿问题", fontWeight = FontWeight.SemiBold)
                    state.reviewIssues.take(6).forEach { issue ->
                        ListItem(
                            headlineContent = { Text("${issue.severity} · ${if (issue.chapterNumber == 0) "全局" else "第 ${issue.chapterNumber} 章"}") },
                            supportingContent = { Text(issue.summary, maxLines = 2) },
                            trailingContent = { TextButton(onClick = { state.setReviewIssueResolved(issue.id, issue.status != "resolved") }) { Text(if (issue.status == "resolved") "重开" else "解决") } },
                        )
                    }
                }
            }
            VerticalDivider(color = Rule)
            Column(Modifier.weight(1f)) {
                Text("本地检查", fontWeight = FontWeight.SemiBold)
                state.qualityIssues().forEach { issue ->
                    ListItem(
                        headlineContent = { Text(issue.title) },
                        supportingContent = { Text(issue.detail) },
                        leadingContent = { Icon(if (issue.severity == QualitySeverity.WARNING) Icons.Outlined.WarningAmber else Icons.Outlined.Info, null, tint = if (issue.severity == QualitySeverity.WARNING) Warm else Brand) },
                    )
                }
                val aiTrace = state.aiTraceReport()
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("AI 痕迹分析 · ${aiTrace.score} 分", fontWeight = FontWeight.SemiBold)
                Text(aiTrace.findings.joinToString("；"), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (aiTrace.suggestions.isNotEmpty()) Text("建议：${aiTrace.suggestions.joinToString("；")}", color = Brand, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("AI 审稿记录", fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.weight(1f)) {
                    items(state.editorialReviews, key = { it.id }) { review ->
                        ListItem(headlineContent = { Text(review.content.take(80)) }, supportingContent = { Text(review.content, maxLines = 5) })
                    }
                }
            }
            VerticalDivider(color = Rule)
            Column(Modifier.weight(1f)) {
                Text("正文版本", fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.weight(1f)) {
                    items(state.revisions, key = { it.id }) { revision ->
                        ListItem(
                            headlineContent = { Text(revision.reason) },
                            supportingContent = { Text(revision.previousContent.take(120), maxLines = 3) },
                            trailingContent = {
                                Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
                                    TextButton(onClick = { revisionToRestore = revision.id }) { Text("恢复") }
                                    TextButton(onClick = { revisionToDelete = revision.id }) { Text("删除", color = Warm) }
                                }
                            },
                        )
                        HorizontalDivider(color = Rule)
                    }
                }
                if (state.repairPlan.isNotBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("修复方案", fontWeight = FontWeight.SemiBold)
                    Text(state.repairPlan, maxLines = 8, color = Color.Gray)
                    Button(onClick = state::applyRepairPlan, enabled = !state.busy) { Icon(Icons.Outlined.AutoFixHigh, null); Spacer(Modifier.width(6.dp)); Text("应用并生成修复稿") }
                }
            }
        }
    }
    revisionToRestore?.let { revisionId ->
        AlertDialog(
            onDismissRequest = { revisionToRestore = null },
            title = { Text("恢复历史版本？") },
            text = { Text("当前正文会先保存为一个新版本，再恢复所选历史正文。") },
            confirmButton = { Button({ revisionToRestore = null; state.restoreRevision(revisionId) }) { Text("恢复") } },
            dismissButton = { TextButton({ revisionToRestore = null }) { Text("取消") } },
        )
    }
    revisionToDelete?.let { revisionId ->
        AlertDialog(
            onDismissRequest = { revisionToDelete = null },
            title = { Text("删除历史版本？") },
            text = { Text("删除后不能恢复该版本的正文。当前正文不会受到影响。") },
            confirmButton = { Button(onClick = { revisionToDelete = null; state.deleteRevision(revisionId) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { revisionToDelete = null }) { Text("取消") } },
        )
    }
}

@Composable private fun Settings(state: AppState) {
    var config by remember(state.modelConfig) { mutableStateOf(state.modelConfig) }
    var recoveryCode by remember { mutableStateOf("") }
    var updateUrl by remember(state.updateManifestUrl) { mutableStateOf(state.updateManifestUrl) }
    var updateProxy by remember(state.updateProxyUrl) { mutableStateOf(state.updateProxyUrl) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
        Text("模型设置", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("密钥使用 Windows DPAPI 加密保存在本机", color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        Field("协议（openai / azure / anthropic / gemini）", config.protocol) {
            config = config.copy(protocol = it.trim().lowercase())
        }
        Field("Base URL", config.baseUrl) { config = config.copy(baseUrl = it) }
        OutlinedTextField(config.apiKey, { config = config.copy(apiKey = it) }, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Field("模型名称", config.model) { config = config.copy(model = it) }
        Text("独立审稿模型（留空则沿用文本模型）", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 20.dp))
        Field("审稿协议（openai / azure / anthropic / gemini）", config.reviewerProtocol) { config = config.copy(reviewerProtocol = it.trim().lowercase()) }
        Field("审稿 Base URL", config.reviewerBaseUrl) { config = config.copy(reviewerBaseUrl = it) }
        OutlinedTextField(config.reviewerApiKey, { config = config.copy(reviewerApiKey = it) }, Modifier.fillMaxWidth(), label = { Text("审稿 API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Field("审稿模型名称", config.reviewerModel) { config = config.copy(reviewerModel = it) }
        Text("封面模型", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 20.dp))
        Field("图片协议（openai / gemini）", config.imageProtocol) {
            config = config.copy(imageProtocol = it.trim().lowercase())
        }
        Field("图片 Base URL", config.imageBaseUrl) { config = config.copy(imageBaseUrl = it) }
        OutlinedTextField(config.imageApiKey, { config = config.copy(imageApiKey = it) }, Modifier.fillMaxWidth(), label = { Text("图片 API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Field("图片模型", config.imageModel) { config = config.copy(imageModel = it) }
        Row {
            Button({ state.saveModelConfig(config) }) { Icon(Icons.Outlined.Save, null); Text("保存") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton({ state.saveModelConfig(config); state.testModel() }, enabled = !state.busy) { Icon(Icons.Outlined.WifiTethering, null); Text("测试连接") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton({ state.saveModelConfig(config); state.testReviewModel() }, enabled = !state.busy) { Icon(Icons.Outlined.RateReview, null); Text("测试审稿连接") }
        }
        Spacer(Modifier.height(28.dp))
        Text("云同步", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(if (state.cloudConfig.enabled) "已启用加密同步，可在打开作品后使用“同步到云端”。" else "云端仅保存加密内容；首次开启会生成恢复码。", color = Color.Gray)
        Spacer(Modifier.height(10.dp))
        if (state.cloudConfig.enabled) {
            OutlinedTextField(state.cloudConfig.recoveryCode, {}, Modifier.fillMaxWidth(), label = { Text("恢复码") }, readOnly = true, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(state::syncSelectedProject, enabled = !state.busy) { Icon(Icons.Outlined.CloudUpload, null); Text("同步当前作品") }
                OutlinedButton(state::restoreCloudProjects, enabled = !state.busy) { Icon(Icons.Outlined.CloudDownload, null); Text("恢复缺失作品") }
            }
        } else {
            OutlinedTextField(recoveryCode, { recoveryCode = it }, Modifier.fillMaxWidth(), label = { Text("已有保险箱恢复码") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(state::enableCloudSync, enabled = !state.busy) { Icon(Icons.Outlined.Cloud, null); Text("新建云同步") }
                OutlinedButton({ state.restoreCloudSync(recoveryCode) }, enabled = !state.busy && recoveryCode.isNotBlank()) { Icon(Icons.Outlined.CloudDownload, null); Text("连接保险箱") }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("自动更新", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(updateUrl, { updateUrl = it }, Modifier.fillMaxWidth(), label = { Text("GitHub update.json 地址") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(updateProxy, { updateProxy = it }, Modifier.fillMaxWidth(), label = { Text("更新代理地址（可选，例如 http://127.0.0.1:10808）") }, singleLine = true)
        Text("仅更新检查和下载会使用此代理；端口由每位用户自己的节点软件决定。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ state.saveUpdateSettings(updateUrl, updateProxy) }) { Icon(Icons.Outlined.Save, null); Text("保存更新设置") }
            OutlinedButton({ state.saveUpdateSettings(updateUrl, updateProxy); state.checkForUpdates() }, enabled = !state.busy && updateUrl.isNotBlank()) { Icon(Icons.Outlined.SystemUpdate, null); Text("检查更新") }
            state.availableUpdate?.let { Button(state::downloadUpdate, enabled = !state.busy) { Icon(Icons.Outlined.Download, null); Text("下载 ${it.version}") } }
        }
        Spacer(Modifier.height(28.dp))
        Text("数据目录", fontWeight = FontWeight.SemiBold)
        Text(state.paths.root.toString(), color = Color.Gray)
        if (state.paths.portable) {
            Text("便携版的数据随启动器保存在解压目录的 data 文件夹。请从 ZIP 外层运行 NovelEdit-Portable.cmd。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        } else {
            OutlinedButton({ pickDirectory()?.let(state::changeDataDirectory) }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.FolderOpen, null); Text("迁移数据目录")
            }
            Text("请选择空文件夹；保存后退出并重新打开应用，现有数据会自动迁移。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable private fun Field(label:String,value:String,lines:Int=1,onChange:(String)->Unit){OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},singleLine=lines==1,minLines=lines);Spacer(Modifier.height(12.dp))}
@Composable private fun CreateProjectDialog(onDismiss:()->Unit,onCreate:(String,String,String)->Unit){var title by remember{mutableStateOf("")};var genre by remember{mutableStateOf("")};var premise by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("新建作品")},text={Column{Field("作品名称",title){title=it};Field("类型",genre){genre=it};Field("核心设定",premise,4){premise=it}}},confirmButton={Button({onCreate(title,genre,premise)},enabled=title.isNotBlank()){Text("创建")}},dismissButton={TextButton(onDismiss){Text("取消")}})}
private fun pickOpen(ext:List<String>):Path?{val d=FileDialog(null as Frame?,"导入文档",FileDialog.LOAD);d.isVisible=true;return d.file?.let{Path.of(d.directory,it)}}
private fun pickSave(default:String):Path?{val d=FileDialog(null as Frame?,"保存文件",FileDialog.SAVE);d.file=default;d.isVisible=true;return d.file?.let{Path.of(d.directory,it)}}
private fun pickDirectory():Path?{val chooser=javax.swing.JFileChooser();chooser.fileSelectionMode=javax.swing.JFileChooser.DIRECTORIES_ONLY;chooser.dialogTitle="选择新的数据目录（必须为空文件夹）";return if(chooser.showOpenDialog(null)==javax.swing.JFileChooser.APPROVE_OPTION)chooser.selectedFile.toPath()else null}
