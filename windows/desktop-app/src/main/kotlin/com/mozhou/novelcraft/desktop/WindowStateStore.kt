package com.mozhou.novelcraft.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.json.JSONObject
import java.nio.file.Files

data class SavedWindowState(val width: Float = 1440f, val height: Float = 900f, val x: Float? = null, val y: Float? = null)

class WindowStateStore(private val paths: AppPaths) {
    private val file = paths.root.resolve("window-state.json")

    fun load(): SavedWindowState = runCatching {
        val json = JSONObject(Files.readString(file))
        SavedWindowState(
            width = json.optDouble("width", 1440.0).toFloat().coerceIn(960f, 3840f),
            height = json.optDouble("height", 900.0).toFloat().coerceIn(640f, 2160f),
            x = json.takeIf { it.has("x") }?.optDouble("x")?.toFloat(),
            y = json.takeIf { it.has("y") }?.optDouble("y")?.toFloat(),
        )
    }.getOrDefault(SavedWindowState())

    fun save(state: WindowState) {
        val position = state.position
        val json = JSONObject().put("width", state.size.width.value).put("height", state.size.height.value)
        if (position.isSpecified) {
            json.put("x", position.x.value)
            json.put("y", position.y.value)
        }
        atomicWrite(file, json.toString(2).toByteArray())
    }
}
