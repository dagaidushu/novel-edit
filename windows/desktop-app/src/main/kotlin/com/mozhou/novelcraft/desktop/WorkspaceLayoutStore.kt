package com.mozhou.novelcraft.desktop

import org.json.JSONObject
import java.nio.file.Files

data class WorkspaceLayout(
    val chapterTreeWidth: Float = 250f,
    val aiPanelWidth: Float = 310f,
    val chapterTreeCollapsed: Boolean = false,
    val aiPanelCollapsed: Boolean = false,
)

class WorkspaceLayoutStore(private val paths: AppPaths) {
    private val file = paths.root.resolve("workspace-layout.json")

    fun load(): WorkspaceLayout = runCatching {
        val json = JSONObject(Files.readString(file))
        WorkspaceLayout(
            chapterTreeWidth = json.optDouble("chapterTreeWidth", 250.0).toFloat().coerceIn(180f, 420f),
            aiPanelWidth = json.optDouble("aiPanelWidth", 310.0).toFloat().coerceIn(260f, 460f),
            chapterTreeCollapsed = json.optBoolean("chapterTreeCollapsed", false),
            aiPanelCollapsed = json.optBoolean("aiPanelCollapsed", false),
        )
    }.getOrDefault(WorkspaceLayout())

    fun save(value: WorkspaceLayout) {
        val json = JSONObject()
            .put("chapterTreeWidth", value.chapterTreeWidth)
            .put("aiPanelWidth", value.aiPanelWidth)
            .put("chapterTreeCollapsed", value.chapterTreeCollapsed)
            .put("aiPanelCollapsed", value.aiPanelCollapsed)
        atomicWrite(file, json.toString(2).toByteArray())
    }
}
