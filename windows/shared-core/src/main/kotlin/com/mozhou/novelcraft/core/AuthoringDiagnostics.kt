package com.mozhou.novelcraft.core

data class GraphHealthIssue(val severity: String, val title: String, val detail: String)
data class PacingRecommendation(val pace: String, val eventType: String, val reason: String)
data class EventMatrixCell(val ruleKey: String, val eventType: String, val lastChapter: Int?, val cooldownRemaining: Int, val category: String)

object GenreStylePresets {
    private val fast = "短段落、强动词、每 300-500 字推进一次信息或收益；对话直接，章末保留明确目标与悬念。避免空泛总结。"
    fun forGenre(genre: String): List<Pair<String, String>> = buildList {
        add("爽文推进" to fast)
        when {
            genre.contains("仙") || genre.contains("玄") -> add("仙侠玄幻" to "意象克制而具体，修行规则前后一致；战斗先交代代价与目标，再写变化；人物说话保留各自身份和利益。")
            genre.contains("都") || genre.contains("情") -> add("都市情感" to "以人物行为和对话呈现情绪，不替读者下结论；关系推进要有事件触发，冲突之后保留余波。")
            genre.contains("悬") || genre.contains("推") -> add("悬疑推理" to "线索可回溯、信息分层投放；不使用作者视角强行隐瞒；每个反转应能在前文找到依据。")
        }
    }
}

object ChapterEntryAngles {
    private val angles = listOf(
        "动作结果：从一个正在发生且有代价的动作切入。",
        "人物选择：从主角必须立刻决定的取舍切入。",
        "环境异样：用可感知的异常环境带出冲突。",
        "关系张力：从两名角色之间未说破的对话或动作切入。",
        "任务压力：从明确的时限、目标或阻碍切入。",
        "信息落差：从角色不知道、读者刚能察觉的信息切入。",
        "余波现场：承接上一章事件的后果，不重复回顾。",
        "日常裂缝：从看似平常却即将失控的细节切入。",
    )
    fun forChapter(number: Int) = angles[((number - 1) % angles.size + angles.size) % angles.size]
}

object StoryGraphHealth {
    fun inspect(items: List<StoryItem>, edges: List<StoryEdge>, anchors: List<StoryAnchor>): List<GraphHealthIssue> {
        val ids = items.map { it.id }.toSet()
        val issues = mutableListOf<GraphHealthIssue>()
        if (items.isEmpty()) issues += GraphHealthIssue("提示", "图谱尚未开始", "先添加角色、地点、伏笔或从章节提取记忆。")
        items.filter { it.name.isBlank() || it.detail.isBlank() }.forEach { issues += GraphHealthIssue("警告", "资料信息不完整", "${it.kind.ifBlank { "资料" }}需要名称和说明。") }
        edges.forEach { edge ->
            if (edge.sourceItemId !in ids || edge.targetItemId !in ids) issues += GraphHealthIssue("错误", "悬空关系", "关系 #${edge.id} 指向已删除或不存在的资料卡。")
            if (edge.sourceItemId == edge.targetItemId) issues += GraphHealthIssue("错误", "自循环关系", "关系 #${edge.id} 的两端是同一资料卡。")
            if (edge.relation.isBlank()) issues += GraphHealthIssue("警告", "关系缺少类型", "请说明人物或设定之间的关系。")
        }
        anchors.filter { it.endChapter < it.startChapter || it.coreConflict.isBlank() }.forEach { issues += GraphHealthIssue("警告", "大纲锚点不完整", "${it.title.ifBlank { "未命名锚点" }}需要有效范围和核心冲突。") }
        if (issues.isEmpty()) issues += GraphHealthIssue("通过", "图谱健康", "未发现悬空关系、无效锚点或缺失的关键字段。")
        return issues
    }
}

object PacingPlanner {
    private val legacyRules = listOf(
        EventMatrixRule(projectId = 0, ruleKey = "conflict_thrill", label = "冲突爽点", cooldown = 2, category = "冲突"),
        EventMatrixRule(projectId = 0, ruleKey = "bond_deepening", label = "人物羁绊", cooldown = 1, category = "关系"),
        EventMatrixRule(projectId = 0, ruleKey = "faction_building", label = "势力经营", cooldown = 2, category = "势力"),
        EventMatrixRule(projectId = 0, ruleKey = "world_painting", label = "风土人情", cooldown = 3, category = "世界"),
        EventMatrixRule(projectId = 0, ruleKey = "tension_escalation", label = "危机升级", cooldown = 2, category = "悬念"),
    )

    private fun activeRules(rules: List<EventMatrixRule>) = rules.filter { it.enabled }.ifEmpty { legacyRules }

    fun recommend(project: NovelProject, events: List<ChapterPacingEvent>, rules: List<EventMatrixRule>, nextChapter: Int): PacingRecommendation {
        val enabledRules = activeRules(rules)
        val recent = events.filter { it.chapterNumber < nextChapter }.takeLast(4)
        val last = recent.lastOrNull()
        val fastCount = recent.count { it.pace == "快" }
        if (last?.pace == "快") return PacingRecommendation("慢", "铺垫", "上一章为快档，下一章需要缓冲并留下新的悬念。")
        if (fastCount >= 2) return PacingRecommendation("中", "关系推进", "近四章快档偏多，建议转入中档推进。")
        if (recent.count { it.pace == "慢" } == 0 && recent.size >= 3) return PacingRecommendation("慢", "铺垫", "近三章没有慢档，建议补充情感、线索或日常张力。")
        val softWindowMissed = nextChapter > 5 && events.filter { it.chapterNumber in (nextChapter - 5) until nextChapter }
            .none { it.eventType == "人物羁绊" || it.eventType == "风土人情" }
        val candidates = enabledRules.sortedWith(compareBy<EventMatrixRule> {
            val lastChapter = events.lastOrNull { event -> event.eventType == it.label }?.chapterNumber ?: Int.MIN_VALUE
            if (nextChapter - lastChapter > it.cooldown) 0 else 1
        }.thenBy { it.cooldown })
        val selected = candidates.firstOrNull { rule ->
            val lastChapter = events.lastOrNull { it.eventType == rule.label }?.chapterNumber ?: Int.MIN_VALUE
            nextChapter - lastChapter > rule.cooldown
        }
        val type = selected?.label ?: "情绪铺垫"
        val remaining = enabledRules.mapNotNull { rule -> events.lastOrNull { it.eventType == rule.label }?.let { rule.label to ((rule.cooldown - (nextChapter - it.chapterNumber) + 1).coerceAtLeast(0)) } }.filter { it.second > 0 }
        val reason = when {
            softWindowMissed -> "近五章缺少人物羁绊或风土人情，先补一章柔和事件窗口。"
            selected == null -> "所有重点事件仍在冷却，建议安排线索、情绪或日常张力窗口。"
            else -> "按各事件独立冷却推荐；同类冲突最多连续两章。" + remaining.joinToString(" ") { "${it.first}还需${it.second}章" }
        }
        return PacingRecommendation(if (project.pacingProfile == "快节奏") "中" else "慢", type, reason)
    }

    fun matrix(events: List<ChapterPacingEvent>, rules: List<EventMatrixRule>, chapterNumber: Int): List<EventMatrixCell> = activeRules(rules).map { rule ->
        val last = events.filter { it.eventType == rule.label && it.chapterNumber < chapterNumber }.maxByOrNull { it.chapterNumber }?.chapterNumber
        EventMatrixCell(rule.ruleKey, rule.label, last, last?.let { (rule.cooldown - (chapterNumber - it) + 1).coerceAtLeast(0) } ?: 0, rule.category)
    }

    fun warnings(project: NovelProject, chapter: Chapter, events: List<ChapterPacingEvent>, rules: List<EventMatrixRule>): List<QualityIssue> {
        val enabledRules = activeRules(rules)
        val current = events.filter { it.chapterId == chapter.id }
        val issues = mutableListOf<QualityIssue>()
        if (current.size > 1) issues += QualityIssue(QualitySeverity.WARNING, "本章事件配额超限", "一章最多安排一个主事件；其余内容请写入节奏备注。")
        val recent = events.filter { it.chapterNumber < chapter.number }.takeLast(3)
        if (current.any { it.pace == "快" } && recent.lastOrNull()?.pace == "快") issues += QualityIssue(QualitySeverity.WARNING, "快档缺少冷却", "快档后应安排慢档或中档作为情绪与信息缓冲。")
        current.forEach { event ->
            val previous = events.filter { it.eventType == event.eventType && it.chapterNumber < chapter.number }.maxByOrNull { it.chapterNumber }
            val cooldown = enabledRules.firstOrNull { it.label == event.eventType }?.cooldown ?: 0
            if (previous != null && chapter.number - previous.chapterNumber <= cooldown) issues += QualityIssue(QualitySeverity.WARNING, "${event.eventType}冷却不足", "上次在第${previous.chapterNumber}章，建议间隔${cooldown}章后再触发。")
        }
        if (current.any { it.eventType == "冲突爽点" }) {
            val previousTwo = events.filter { it.chapterNumber < chapter.number }.takeLast(2)
            if (previousTwo.size == 2 && previousTwo.all { it.eventType == "冲突爽点" }) issues += QualityIssue(QualitySeverity.WARNING, "连续冲突超限", "冲突爽点最多连续两章，下一章请安排关系、势力、世界或悬念转换。")
        }
        if (chapter.number >= 6) {
            val softWindow = events.filter { it.chapterNumber in (chapter.number - 5) until chapter.number }
            if (softWindow.none { it.eventType == "人物羁绊" || it.eventType == "风土人情" } && current.none { it.eventType == "人物羁绊" || it.eventType == "风土人情" }) {
                issues += QualityIssue(QualitySeverity.WARNING, "柔和事件窗口缺失", "每五章至少安排一次人物羁绊或风土人情，避免连续高压疲劳。")
            }
        }
        return issues
    }
}

