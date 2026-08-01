package com.mozhou.novelcraft.desktop

import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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

object AppVersion { const val CURRENT = "1.0.1" }

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

class UpdatePreferences(private val paths:AppPaths){private val file=paths.root.resolve("update-config.json");fun load()=runCatching{JSONObject(Files.readString(file)).optString("manifestUrl")}.getOrDefault("");fun save(url:String)=atomicWrite(file,JSONObject().put("manifestUrl",url.trim()).toString(2).toByteArray())}
class UpdateService {
    private val http=HttpClient.newHttpClient()
    fun check(manifestUrl:String,current:String):UpdateInfo? { if(manifestUrl.isBlank()) return null; val response=http.send(HttpRequest.newBuilder(URI.create(manifestUrl)).GET().build(),HttpResponse.BodyHandlers.ofString()); require(response.statusCode()==200){"更新清单读取失败（HTTP ${response.statusCode()}）"}; val json=JSONObject(response.body()); val info=UpdateInfo(json.getString("version"),json.getString("msiUrl"),json.getString("portableUrl"),json.getString("msiSha256"),json.getString("portableSha256"),json.optString("notes")); require(info.msiUrl.startsWith("https://") && info.portableUrl.startsWith("https://")){"更新清单必须提供 HTTPS 下载地址"}; require(info.msiSha256.matches(Regex("[A-Fa-f0-9]{64}")) && info.portableSha256.matches(Regex("[A-Fa-f0-9]{64}"))){"更新清单中的 SHA-256 格式无效"}; return info.takeIf{isNewerVersion(it.version,current)} }
    fun download(info:UpdateInfo,portable:Boolean,target:java.nio.file.Path){val url=if(portable)info.portableUrl else info.msiUrl;require(url.startsWith("https://")){"更新地址必须使用 HTTPS"};val response=http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());require(response.statusCode()==200){"更新包下载失败（HTTP ${response.statusCode()}）"};val hash=MessageDigest.getInstance("SHA-256").digest(response.body()).joinToString(""){"%02x".format(it)};require(hash.equals(info.checksum(portable),ignoreCase=true)){"更新包校验失败，文件已丢弃"};atomicWrite(target,response.body())}
}
