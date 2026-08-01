package com.mozhou.novelcraft.desktop

import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

data class CloudSyncConfig(val vaultId:String="",val secret:String="",val revisions:Map<String,Int> = emptyMap()) {
    val enabled get()=vaultId.isNotBlank()&&secret.isNotBlank()
    val recoveryCode get()="$vaultId.$secret"
}
data class RemoteCloudProject(val id:String,val revision:Int)
class CloudRevisionConflict(val remoteRevision: Int) : IllegalStateException("云端版本已变更")

class SecureCloudSyncPreferences(private val paths:AppPaths) {
    private val file=paths.root.resolve("cloud-sync.json")
    fun load()=runCatching{val j=JSONObject(Files.readString(file));CloudSyncConfig(j.optString("vaultId"),decrypt(j.optString("secret")),j.optJSONObject("revisions")?.keySet()?.associateWith{j.getJSONObject("revisions").optInt(it)}?:emptyMap())}.getOrDefault(CloudSyncConfig())
    fun save(config:CloudSyncConfig){val revisions=JSONObject();config.revisions.forEach{(id,value)->revisions.put(id,value)};atomicWrite(file,JSONObject().put("vaultId",config.vaultId).put("secret",encrypt(config.secret)).put("revisions",revisions).toString(2).toByteArray(StandardCharsets.UTF_8))}
    private fun encrypt(value:String)=Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(value.toByteArray(StandardCharsets.UTF_8)))
    private fun decrypt(value:String)=if(value.isBlank())"" else String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(value)),StandardCharsets.UTF_8)
}

class CloudSyncClient(private val endpoint:String="https://noveledit-sync.zhaoghao528.workers.dev") {
    private val http=HttpClient.newHttpClient()
    fun createVault():CloudSyncConfig { val id=randomUrl(16);val secret=randomUrl(32);val request=HttpRequest.newBuilder(URI.create("$endpoint/v1/vaults")).header("Authorization","Bearer $secret").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSONObject().put("vaultId",id).toString())).build(); val response=http.send(request,HttpResponse.BodyHandlers.ofString());require(response.statusCode()==201){"云端保险箱创建失败（HTTP ${response.statusCode()}）"};return CloudSyncConfig(id,secret) }
    fun upload(config:CloudSyncConfig,projectId:String,expected:Int,plain:ByteArray):Int { val encrypted=encrypt(config.secret,plain);val request=HttpRequest.newBuilder(URI.create("$endpoint/v1/vaults/${config.vaultId}/projects/$projectId")).header("Authorization","Bearer ${config.secret}").header("If-Match",expected.toString()).PUT(HttpRequest.BodyPublishers.ofByteArray(encrypted)).build();val response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()==409)throw CloudRevisionConflict(JSONObject(response.body()).optInt("revision"));require(response.statusCode() in 200..299){"云同步失败（HTTP ${response.statusCode()}）"};return JSONObject(response.body()).getInt("revision") }
    fun list(config:CloudSyncConfig):List<RemoteCloudProject>{val response=http.send(HttpRequest.newBuilder(URI.create("$endpoint/v1/vaults/${config.vaultId}/projects")).header("Authorization","Bearer ${config.secret}").GET().build(),HttpResponse.BodyHandlers.ofString());require(response.statusCode()==200){"无法读取云端项目（HTTP ${response.statusCode()}）"};val projects=JSONObject(response.body()).getJSONArray("projects");return List(projects.length()){RemoteCloudProject(projects.getJSONObject(it).getString("project_id"),projects.getJSONObject(it).getInt("revision"))}}
    fun download(config:CloudSyncConfig,projectId:String):ByteArray{val response=http.send(HttpRequest.newBuilder(URI.create("$endpoint/v1/vaults/${config.vaultId}/projects/$projectId")).header("Authorization","Bearer ${config.secret}").GET().build(),HttpResponse.BodyHandlers.ofByteArray());require(response.statusCode()==200){"无法下载云端项目（HTTP ${response.statusCode()}）"};return decrypt(config.secret,response.body())}
    private fun encrypt(secret:String,plain:ByteArray):ByteArray { val iv=ByteArray(12).also(SecureRandom()::nextBytes);val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,javax.crypto.spec.SecretKeySpec(java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()),"AES"),GCMParameterSpec(128,iv))};return iv+cipher.doFinal(plain) }
    private fun decrypt(secret:String,encrypted:ByteArray):ByteArray {require(encrypted.size>28){"云端数据已损坏"};val iv=encrypted.copyOfRange(0,12);return Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,javax.crypto.spec.SecretKeySpec(java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()),"AES"),GCMParameterSpec(128,iv))}.doFinal(encrypted.copyOfRange(12,encrypted.size))}
    private fun randomUrl(bytes:Int)=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(SecureRandom()::nextBytes))
}
