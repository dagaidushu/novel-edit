package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.GenerationRequest
import com.mozhou.novelcraft.core.AiHttpException
import com.mozhou.novelcraft.core.ModelConfig
import com.mozhou.novelcraft.core.retryAiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class OpenAiCompatibleClient(private val allowInsecureLoopback: Boolean = false) {
    suspend fun test(config: ModelConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(validBaseUrl(config)) { "请填写有效的 HTTPS Base URL" }
            require(config.apiKey.isNotBlank()) { "请先填写 API Key" }
            require(config.model.isNotBlank()) { "请先填写模型名称" }
            retryAiRequest {
                val azure = config.protocol == "azure"
                val suffix = when (config.protocol) { "anthropic" -> "/v1/models"; "gemini" -> "/models?key=${config.apiKey}"; else -> "/models" }
                val connection = URL(if (azure) endpoint(config) else config.baseUrl.trimEnd('/') + suffix).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = if (azure) "POST" else "GET"
                    if (azure) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    setAuth(connection, config)
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    if (azure) connection.outputStream.use {
                        it.write(JSONObject()
                            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
                            .put("max_tokens", 1)
                            .toString()
                            .toByteArray(Charsets.UTF_8))
                    }
                    val status = connection.responseCode
                    if (status !in 200..299) throw AiHttpException(status, "接口返回 HTTP $status")
                    if (!azure) {
                        val availableModels = availableModelIds(config.protocol, connection.inputStream.bufferedReader().use { it.readText() })
                        if (availableModels.isNotEmpty() && config.model !in availableModels) {
                            val choices = availableModels.take(8).joinToString("、")
                            throw IllegalArgumentException(
                                "模型“${config.model}”不可用或当前账号无权限。请从服务商控制台复制实际模型 ID。可用模型：$choices",
                            )
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            "连接成功，模型“${config.model}”可用"
        }
    }

    suspend fun continueWriting(
        config: ModelConfig,
        context: String,
        request: GenerationRequest? = null,
        onDelta: ((String) -> Unit)? = null,
    ): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.8,
        systemInstruction = "你是中文网文写作助手。必须使用第三人称叙事，严禁使用“我”作为叙述主语。只输出可直接接在正文后的小说正文，不输出标题、说明、Markdown 或分析。不得提前揭露尚未解决的核心谜底。",
        request = request,
        onDelta = onDelta,
    )

    suspend fun generateChapterPlan(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.5,
        systemInstruction = "你是中文网文策划编辑。根据作者提供的已写内容和本地设定，只输出本章可执行大纲：目标、冲突升级、关键转折、结尾钩子。使用简短中文分点，不要写正文，不要暴露保密设定。",
        request = request,
    )

    suspend fun generateBeatSheet(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.4,
        systemInstruction = "你是中文网文分镜策划。基于本章计划、锚点和历史信息，只输出 4-7 条按顺序执行的 Beat Sheet。每条必须写明场景/人物动作/信息变化或冲突升级；最后一条必须是具体钩子。不要写正文、分析或 Markdown 标题；不得提前揭露禁区。",
        request = request,
    )

    suspend fun writeFullChapter(
        config: ModelConfig,
        context: String,
        request: GenerationRequest? = null,
        onDelta: ((String) -> Unit)? = null,
    ): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.8,
        systemInstruction = "你是中文网文作者。必须使用第三人称叙事，严禁使用“我”作为叙述主语。根据上下文、文风、锚点和分镜，输出一章完整纯小说正文，必须不少于 4000 个中文字符。只输出正文，不输出标题、说明、Markdown、分析或元信息。不得提前揭露禁区，结尾必须留下具体可继续写的钩子。",
        request = request,
        onDelta = onDelta,
    )

    suspend fun generateChapterTitle(config: ModelConfig, chapterText: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = chapterText,
        temperature = 0.35,
        systemInstruction = "你是中文网文责编。只根据给出的章节正文生成一个具体、有悬念的章节标题，限 5-18 个汉字；不要章节编号、引号、Markdown、解释或换行。",
        request = request,
    )

    suspend fun extractStoryMemory(config: ModelConfig, chapterText: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = chapterText,
        temperature = 0.1,
        systemInstruction = """你是小说知识图谱抽取器。仅根据给出的章节文本提取明确出现或明确变化的信息，不得猜测、补全或写小说正文。只输出一个合法 JSON 对象，不要 Markdown：
{
  "items":[{"kind":"人物|地点|势力|物品|事件|伏笔|世界规则","name":"名称","detail":"本章可验证的状态或事实","status":"活跃|已回收|保密"}],
  "edges":[{"source":"已在items中出现的名称","target":"已在items中出现的名称","relation":"同盟|敌对|位于|持有|隶属|触发|铺垫|师徒|情感","description":"本章证据"}]
}
没有可靠信息时返回空数组。每类最多15条，不要把普通路人、泛称或推测当实体。""",
        request = request,
    )

    suspend fun generateRepairPlan(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.3,
        systemInstruction = "你是中文小说责编。根据给出的章节和已发现的门禁问题，输出最短修复计划：按优先级列出具体要改的段落、修改目标和一个可直接采用的写法方向。不要重写全文，不要输出正文以外的空泛评价，不要建议提前揭露禁区。",
        request = request,
    )

    suspend fun rewriteChapter(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.55,
        systemInstruction = "你是中文网文责任编辑。只重写用户给出的当前章节正文，逐项解决已列出的门禁问题，保留已经成立的剧情事实、角色关系、叙事视角和未解悬念。只输出完整正文，不要标题、说明、Markdown 或分析。",
        request = request,
    )

    suspend fun humanizeChapter(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.65,
        systemInstruction = "你是中文网文语言编辑。仅润色用户给出的当前章节：删减机械重复、概念复述和模板化转折，使动作、感官细节和人物语气更自然；不得改变剧情事件、人物关系、伏笔状态、叙事视角或字数规模。只输出完整正文，不要标题、说明、Markdown 或分析。",
        request = request,
    )

    suspend fun editorialReview(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.25,
        systemInstruction = "你是中文网文责任编辑。审阅给出的单章正文与设定，只报告可验证的问题：剧情连续性、人物动机、时间线、伏笔、节奏、语言重复。按 P0/P1/P2 分级；每条指出依据和最小修改建议。不要重写正文，不要虚构正文外事实。",
        request = request,
    )

    suspend fun characterConsistencyReview(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.15,
        systemInstruction = "你是中文网文的角色一致性编辑。只核查给出的章节与设定：角色动机、说话方式、能力边界、已建立关系、时间线和已发生事件。按 P0/P1/P2 列出可验证问题；每条给出文本依据与最小修复建议。没有问题时输出 PASS。绝不改写正文、绝不虚构章节外事实。",
        request = request,
    )

    suspend fun calibrateStyle(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.15,
        systemInstruction = "You are a Chinese web-novel style gate. Return only concise Chinese findings, beginning with PASS when the chapter follows the supplied style guide, or FAIL when it has material narration, dialogue, pacing, or prohibited-expression drift. Cite observable evidence and a minimal repair action. Do not rewrite the chapter.",
        request = request,
    )

    suspend fun copyeditReview(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.1,
        systemInstruction = "You are a Chinese novel copyediting gate. Return only concise Chinese findings, beginning with PASS when there are no material repetition, placeholder, meta-text, punctuation, or readability defects; otherwise begin FAIL and list minimal actionable fixes. Do not rewrite the chapter.",
        request = request,
    )

    suspend fun analyzeReferenceStructure(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.2,
        systemInstruction = "你是网文结构编辑。仅根据作者提供的非受版权保护摘要、标签和观察，提炼可迁移的抽象结构：开局承诺、冲突升级、信息揭示节奏、章末钩子、爽点类型和风险提示。不得复述、续写或模仿任何受版权保护作品，不得生成原作人物、情节、句子或可识别片段。",
        request = request,
    )

    suspend fun extractStyleGuide(config: ModelConfig, sample: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = sample,
        temperature = 0.2,
        systemInstruction = "你是中文网文风格编辑。仅根据给出的样章，提取一个可执行的项目文风档案：叙事视角、时态、句长和节奏、对话比例、描写偏好、禁用表达、章末钩子习惯。用紧凑中文分点，不评价原文，不仿照在世作者，不输出小说正文。",
        request = request,
    )

    suspend fun generateProjectProfile(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.7,
        systemInstruction = """你是中文网文策划编辑。依据作者给出的书名和已有信息，补全可直接用于创作的作品设定。只输出一个合法 JSON 对象，不要 Markdown 或解释：
{
  "title":"吸引人的书名，最多16字",
  "genre":"精确题材，最多12字",
  "premise":"一句话核心设定，最多60字",
  "summary":"120-180字的网文简介，突出爽点、冲突与悬念",
  "tags":"3-6个中文标签，用中文逗号分隔",
  "targetAudience":"目标读者描述，最多30字",
  "protagonistName":"主角姓名",
  "conflict":"贯穿全书且不能轻易解决的核心冲突，最多60字",
  "promise":"读者持续获得的爽点或悬念，最多60字",
  "writingStyle":"可执行的叙事视角、节奏和对白风格，最多80字",
  "forbiddenContent":"不应提前揭露或不应触碰的内容，最多60字"
}
保留并强化作者已给出的信息，不要引用现实在世作者，不要编造具体平台或版权信息。""",
        request = request,
    )

    suspend fun generateLongFormBlueprint(config: ModelConfig, context: String, request: GenerationRequest? = null): Result<String> = chat(
        config = config,
        context = context,
        temperature = 0.55,
        systemInstruction = "你是中文长篇网文总策划。基于作者提供的作品资料，输出可编辑的长篇路线图：总目标与终局条件、4-6个阶段（建议章节区间、阶段目标、核心冲突、爽点升级、必须保留的伏笔）、主角能力或关系变化，以及每阶段不得提前解决的问题。不要写正文，不要编造现实作品或平台信息；用紧凑中文分点输出。",
        request = request,
    )

    suspend fun generateCover(
        config: ModelConfig,
        prompt: String,
        request: GenerationRequest? = null,
        size: String = "1024x1536",
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.imageBaseUrl.startsWith("https://")) { "请先填写封面 AI 的 Base URL" }
            require(config.imageApiKey.isNotBlank()) { "请先填写封面 AI 的 API Key" }
            require(config.imageModel.isNotBlank()) { "请先填写封面 AI 的模型名称" }
            retryAiRequest(request) {
                val endpoint = if (config.imageProtocol == "gemini") "${config.imageBaseUrl.trimEnd('/')}/models/${config.imageModel}:generateContent?key=${config.imageApiKey}" else config.imageBaseUrl.trimEnd('/') + "/images/generations"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                request?.attach(connection)
                try {
                    val body = if (config.imageProtocol == "gemini") JSONObject().put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt))))).toString() else JSONObject().apply { put("model", config.imageModel); put("prompt", prompt); put("size", size); put("response_format", "b64_json") }.toString()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (config.imageProtocol != "gemini") connection.setRequestProperty("Authorization", "Bearer " + config.imageApiKey)
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 120_000
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) throw AiHttpException(status, "封面接口返回 HTTP $status: ${response.take(180)}")
                    if (config.imageProtocol == "gemini") {
                        val parts = JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        val inline = (0 until parts.length()).map { parts.getJSONObject(it) }.firstOrNull { it.has("inlineData") }?.getJSONObject("inlineData") ?: error("Gemini 未返回图片数据；请选择支持图片输出的模型")
                        return@retryAiRequest Base64.getDecoder().decode(inline.getString("data"))
                    }
                    val data = JSONObject(response).getJSONArray("data").getJSONObject(0)
                    when {
                        data.has("b64_json") -> Base64.getDecoder().decode(data.getString("b64_json"))
                        data.has("url") -> URL(data.getString("url")).openStream().use { it.readBytes() }
                        else -> error("封面接口没有返回图片数据")
                    }
                } finally {
                    request?.detach(connection)
                    connection.disconnect()
                }
            }
        }
    }

    suspend fun testImage(config: ModelConfig): Result<String> =
        generateCover(
            config = config,
            prompt = "A simple abstract Chinese web novel cover composition, no text, no logo, no watermark.",
            size = "1024x1024",
        ).map { "封面 AI 连接成功，图像端点可用" }

    private suspend fun legacyChat(
        config: ModelConfig,
        context: String,
        temperature: Double,
        systemInstruction: String,
        request: GenerationRequest? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(validBaseUrl(config)) { "请填写有效的 HTTPS Base URL" }
            require(config.apiKey.isNotBlank()) { "请先填写 API Key" }
            require(config.model.isNotBlank()) { "请先填写模型名称" }
            val endpoint = endpoint(config)
            val body = requestBody(config, context, temperature, systemInstruction)
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            request?.attach(connection)
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setAuth(connection, config)
                connection.connectTimeout = 20_000
                connection.readTimeout = 90_000
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) error("接口返回 HTTP " + status + ": " + response.take(180))
                parseResponse(config, response)
            } finally {
                request?.detach(connection)
                connection.disconnect()
            }
        }
    }

    private suspend fun chat(
        config: ModelConfig,
        context: String,
        temperature: Double,
        systemInstruction: String,
        request: GenerationRequest? = null,
        onDelta: ((String) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(validBaseUrl(config)) { "A valid Base URL is required" }
            require(config.apiKey.isNotBlank()) { "API Key is required" }
            require(config.model.isNotBlank()) { "Model name is required" }
            val stream = onDelta != null
            var receivedStreamDelta = false
            retryAiRequest(request, canRetry = { !receivedStreamDelta }) {
                val connection = URL(endpoint(config, stream)).openConnection() as HttpURLConnection
                request?.attach(connection)
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (stream) connection.setRequestProperty("Accept", "text/event-stream")
                    setAuth(connection, config)
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 120_000
                    connection.outputStream.use { it.write(requestBody(config, context, temperature, systemInstruction, stream).toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        throw requestFailure(status, errorBody, config.model)
                    }
                    if (!stream) return@retryAiRequest parseResponse(config, connection.inputStream.bufferedReader().use { it.readText() })
                    val output = StringBuilder()
                    connection.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { rawLine ->
                            val line = rawLine.trim()
                            val delta = if (line.startsWith("data:")) streamDelta(config.protocol, line.removePrefix("data:").trim()) else null
                            if (!delta.isNullOrEmpty()) {
                                receivedStreamDelta = true
                                output.append(delta)
                                onDelta?.invoke(delta)
                            }
                        }
                    }
                    output.toString().trim().ifBlank { error("The model returned an empty streamed response") }
                } finally {
                    request?.detach(connection)
                    connection.disconnect()
                }
            }
        }
    }

    private fun validBaseUrl(config: ModelConfig): Boolean {
        if (config.baseUrl.startsWith("https://")) return true
        if (!allowInsecureLoopback) return false
        return runCatching {
            val url = URL(config.baseUrl)
            url.protocol == "http" && url.host in setOf("127.0.0.1", "localhost", "::1")
        }.getOrDefault(false)
    }

    private fun availableModelIds(protocol: String, body: String): List<String> = runCatching {
        val response = JSONObject(body)
        val values = if (protocol == "gemini") response.optJSONArray("models") else response.optJSONArray("data")
        buildList {
            for (index in 0 until (values?.length() ?: 0)) {
                val value = values?.opt(index)
                val id = when (value) {
                    is JSONObject -> if (protocol == "gemini") value.optString("name") else value.optString("id")
                    is String -> value
                    else -> ""
                }.removePrefix("models/")
                if (id.isNotBlank()) add(id)
            }
        }
    }.getOrDefault(emptyList())

    private fun requestFailure(status: Int, body: String, model: String): AiHttpException {
        val normalized = body.lowercase()
        val message = when {
            status == 404 && ("model_not_found" in normalized || "model" in normalized) ->
                "模型“$model”不存在、不可用或当前账号无权限。请在设置中填写服务商提供的实际模型 ID"
            status == 401 || status == 403 -> "模型服务拒绝了凭据，请检查 API Key 和服务地址"
            status == 429 -> "模型服务正在限流或额度不足，请稍后重试"
            status in 500..599 -> "模型服务暂时不可用（HTTP $status），请稍后重试"
            else -> "模型服务返回 HTTP $status"
        }
        return AiHttpException(status, message)
    }
    private fun endpoint(config: ModelConfig, stream: Boolean = false): String = when (config.protocol) {
        "anthropic" -> config.baseUrl.trimEnd('/') + "/v1/messages"
        "gemini" -> config.baseUrl.trimEnd('/') + "/models/${config.model}:${if (stream) "streamGenerateContent?alt=sse&" else "generateContent?"}key=${config.apiKey}"
        "azure" -> config.baseUrl.trimEnd('/') + "/openai/deployments/${config.model}/chat/completions?api-version=2024-10-21"
        else -> config.baseUrl.trimEnd('/') + "/chat/completions"
    }
    private fun requestBody(config: ModelConfig, context: String, temperature: Double, system: String, stream: Boolean = false): String = when (config.protocol) {
        "anthropic" -> JSONObject().put("model", config.model).put("max_tokens", 8192).put("temperature", temperature).put("stream", stream).put("system", system).put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", context))).toString()
        "gemini" -> JSONObject().put("systemInstruction", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", system)))).put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", context))))).put("generationConfig", JSONObject().put("temperature", temperature).put("maxOutputTokens", 8192)).toString()
        "azure" -> JSONObject().put("temperature", temperature).put("max_tokens", 8192).put("stream", stream).put("messages", org.json.JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", context))).toString()
        else -> JSONObject().put("model", config.model).put("temperature", temperature).put("max_tokens", 8192).put("stream", stream).put("messages", org.json.JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", context))).toString()
    }
    private fun streamDelta(protocol: String, data: String): String? = if (data == "[DONE]") null else runCatching {
        val payload = JSONObject(data)
        when (protocol) {
            "anthropic" -> payload.optJSONObject("delta")?.opt("text") as? String
            "gemini" -> payload.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.opt("text") as? String
            else -> payload.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.opt("content") as? String
        }
    }.getOrNull()
    private fun parseResponse(config: ModelConfig, raw: String): String = when (config.protocol) {
        "anthropic" -> JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text").trim()
        "gemini" -> JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
        else -> JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
    private fun setAuth(connection: HttpURLConnection, config: ModelConfig) { when (config.protocol) { "anthropic" -> { connection.setRequestProperty("x-api-key", config.apiKey); connection.setRequestProperty("anthropic-version", "2023-06-01") }; "azure" -> connection.setRequestProperty("api-key", config.apiKey); "gemini" -> Unit; else -> connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}") } }
}
