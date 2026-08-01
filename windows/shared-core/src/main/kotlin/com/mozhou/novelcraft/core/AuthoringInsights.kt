package com.mozhou.novelcraft.core

data class StyleFingerprint(val metrics: String, val keywords: String)
data class AiTraceReport(val score: Int, val findings: List<String>, val suggestions: List<String>)
data class ResearchPlan(val keywords: List<String>, val gaps: List<String>, val quick: String, val standard: String, val deep: String)

object StyleFingerprintAnalyzer {
    fun analyze(text: String): StyleFingerprint {
        val sentences = text.split(Regex("[。！？!?]+")).map(String::trim).filter(String::isNotEmpty)
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map(String::trim).filter(String::isNotEmpty)
        val dialogue = text.count { it == '“' || it == '”' }
        val average = if (sentences.isEmpty()) 0 else sentences.sumOf { it.length } / sentences.size
        val dialogueRatio = if (text.isBlank()) 0 else dialogue * 100 / text.length
        val candidates = Regex("[\\p{IsHan}]{2,4}").findAll(text).map { it.value }.toList()
        val keywords = candidates.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.filter { it.value >= 2 }.take(8).joinToString("、") { it.key }
        return StyleFingerprint("句均${average}字｜段均${if (paragraphs.isEmpty()) 0 else text.length / paragraphs.size}字｜对白${dialogueRatio}%｜${if (average <= 20) "短句偏快" else "长句舒展"}", keywords)
    }
}

object AiTraceDetector {
    private val stockPhrases = listOf("不禁", "嘴角微微上扬", "心中一凛", "瞳孔骤缩", "意味深长", "空气仿佛凝固", "毋庸置疑", "令人不由得")
    fun inspect(text: String): AiTraceReport {
        if (text.isBlank()) return AiTraceReport(0, listOf("本章尚无正文"), emptyList())
        val findings = mutableListOf<String>()
        stockPhrases.filter { text.contains(it) }.forEach { findings += "高频模板表达：$it" }
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.replace(Regex("\\s+"), "") }.filter { it.length > 30 }
        if (paragraphs.groupBy { it }.any { it.value.size > 1 }) findings += "存在重复段落"
        val sentenceLengths = text.split(Regex("[。！？!?]+")).map { it.trim().length }.filter { it > 0 }
        if (sentenceLengths.size >= 6 && sentenceLengths.maxOrNull()!! - sentenceLengths.minOrNull()!! < 8) findings += "句长变化过少，节奏可能过于均匀"
        if (Regex("(然后|接着|随后).{0,16}(然后|接着|随后)").containsMatchIn(text)) findings += "连接词密度偏高"
        val score = (findings.size * 22).coerceAtMost(100)
        val suggestions = listOfNotNull(
            if (findings.any { it.contains("模板") }) "将模板表情替换为人物独有的动作、感官或具体物件。" else null,
            if (findings.any { it.contains("句长") }) "混入短促动作句和有停顿的对话，打破匀速叙述。" else null,
            if (findings.any { it.contains("连接词") }) "删除显式连接词，让因果通过动作和场景衔接。" else null,
        )
        return AiTraceReport(score, findings.ifEmpty { listOf("未发现明显的模板化痕迹") }, suggestions)
    }
}

object ResearchPlanner {
    fun build(project: NovelProject, notes: List<ResearchNote>): ResearchPlan {
        val basis = "${project.genre} ${project.premise} ${project.tags}".lowercase()
        val keywords = buildList {
            addAll(listOf(project.genre, project.protagonistName).filter { it.isNotBlank() })
            if (basis.contains("修仙") || basis.contains("仙侠")) addAll(listOf("境界体系", "宗门规则", "灵药法器", "秘境历练"))
            else if (basis.contains("悬疑")) addAll(listOf("案件流程", "取证逻辑", "时间线核对", "动机设计"))
            else if (basis.contains("科幻")) addAll(listOf("技术边界", "社会后果", "科学术语", "世界规则"))
            else addAll(listOf("世界规则", "职业细节", "冲突场景", "读者预期"))
        }.distinct().take(6)
        val noteText = notes.joinToString(" ") { "${it.title} ${it.tags} ${it.content}" }
        val gaps = listOf("世界规则", "角色动机", "场景细节", "专业常识").filter { !noteText.contains(it) }.take(3)
        return ResearchPlan(keywords, gaps, "快速：围绕${keywords.take(2).joinToString("、")}写 3 条可直接进入设定的事实摘要。", "标准：补齐${gaps.joinToString("、")}，每条记录来源、适用章节和不确定性。", "深入：为${project.genre.ifBlank { "当前题材" }}建立时间线、术语表、反例和需要作者确认的边界。")
    }
}

