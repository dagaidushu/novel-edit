package com.mozhou.novelcraft.desktop

import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.ProxySelector
import java.nio.file.Files
import java.security.MessageDigest

data class UpdateInfo(
    val version: String,
    val msiUrl: String,
    val portableUrl: String,
    val msiSha256: String,
    val portableSha256: String,
    val notes: String = "",
) {
    fun checksum(portable: Boolean) = if (portable) portableSha256 else msiSha256
}

object AppVersion { const val CURRENT = "1.0.2" }

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val left = candidate.split('.').map { it.toIntOrNull() ?: 0 }
    val right = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0 }
        val b = right.getOrElse(index) { 0 }
        if (a != b) return a > b
    }
    return false
}

internal fun updateProxyAddress(proxyUrl: String): InetSocketAddress? {
    if (proxyUrl.isBlank()) return null
    val uri = URI.create(proxyUrl.trim())
    require(uri.scheme.equals("http", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.port in 1..65535) { "更新代理地址应为 http://主机:端口" }
    return InetSocketAddress(uri.host, uri.port)
}

data class UpdateSettings(val manifestUrl: String = "", val proxyUrl: String = "")

class UpdatePreferences(private val paths: AppPaths) {
    private val file = paths.root.resolve("update-config.json")
    fun load() = runCatching {
        JSONObject(Files.readString(file)).let { UpdateSettings(it.optString("manifestUrl"), it.optString("proxyUrl")) }
    }.getOrDefault(UpdateSettings())
    fun save(value: UpdateSettings) = atomicWrite(file, JSONObject().put("manifestUrl", value.manifestUrl.trim()).put("proxyUrl", value.proxyUrl.trim()).toString(2).toByteArray())
}

class UpdateService {
    fun check(manifestUrl: String, current: String, proxyUrl: String = ""): UpdateInfo? {
        if (manifestUrl.isBlank()) return null
        val response = http(proxyUrl).send(HttpRequest.newBuilder(URI.create(manifestUrl)).GET().build(), HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) { "更新清单读取失败（HTTP ${response.statusCode()}）" }
        val json = JSONObject(response.body())
        val info = UpdateInfo(json.getString("version"), json.getString("msiUrl"), json.getString("portableUrl"), json.getString("msiSha256"), json.getString("portableSha256"), json.optString("notes"))
        require(info.msiUrl.startsWith("https://") && info.portableUrl.startsWith("https://")) { "更新清单必须提供 HTTPS 下载地址" }
        require(info.msiSha256.matches(Regex("[A-Fa-f0-9]{64}")) && info.portableSha256.matches(Regex("[A-Fa-f0-9]{64}"))) { "更新清单中的 SHA-256 格式无效" }
        return info.takeIf { isNewerVersion(it.version, current) }
    }

    fun download(info: UpdateInfo, portable: Boolean, target: java.nio.file.Path, proxyUrl: String = "") {
        val url = if (portable) info.portableUrl else info.msiUrl
        require(url.startsWith("https://")) { "更新地址必须使用 HTTPS" }
        val response = http(proxyUrl).send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        require(response.statusCode() == 200) { "更新包下载失败（HTTP ${response.statusCode()}）" }
        val hash = MessageDigest.getInstance("SHA-256").digest(response.body()).joinToString("") { "%02x".format(it) }
        require(hash.equals(info.checksum(portable), ignoreCase = true)) { "更新包校验失败，文件已丢弃" }
        atomicWrite(target, response.body())
    }

    private fun http(proxyUrl: String): HttpClient = HttpClient.newBuilder().apply {
        updateProxyAddress(proxyUrl)?.let { proxy(ProxySelector.of(it)) }
    }.build()
}
