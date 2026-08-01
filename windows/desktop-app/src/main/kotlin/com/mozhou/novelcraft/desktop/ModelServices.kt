package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.ModelConfig
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.Base64

class SecureModelPreferences(private val paths: AppPaths) {
    private val file = paths.root.resolve("model-config.json")
    fun load(): ModelConfig = runCatching {
        val json=JSONObject(Files.readString(file))
        ModelConfig(json.optString("provider","OpenAI 兼容"),json.optString("protocol","openai"),json.optString("baseUrl"),decrypt(json.optString("apiKey")),json.optString("model"),json.optString("imageBaseUrl"),decrypt(json.optString("imageApiKey")),json.optString("imageModel"),json.optString("imageProtocol","openai"),json.optString("reviewerBaseUrl"),decrypt(json.optString("reviewerApiKey")),json.optString("reviewerModel"),json.optString("reviewerProtocol"))
    }.getOrDefault(ModelConfig())
    fun save(value:ModelConfig){
        val json=JSONObject().put("provider",value.provider).put("protocol",value.protocol).put("baseUrl",value.baseUrl.trimEnd('/')).put("apiKey",encrypt(value.apiKey)).put("model",value.model).put("imageBaseUrl",value.imageBaseUrl.trimEnd('/')).put("imageApiKey",encrypt(value.imageApiKey)).put("imageModel",value.imageModel).put("imageProtocol",value.imageProtocol).put("reviewerBaseUrl",value.reviewerBaseUrl.trimEnd('/')).put("reviewerApiKey",encrypt(value.reviewerApiKey)).put("reviewerModel",value.reviewerModel).put("reviewerProtocol",value.reviewerProtocol)
        atomicWrite(file,json.toString(2).toByteArray(StandardCharsets.UTF_8))
    }
    private fun encrypt(value:String)=if(value.isBlank())"" else Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(value.toByteArray(StandardCharsets.UTF_8)))
    private fun decrypt(value:String)=if(value.isBlank())"" else String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(value)),StandardCharsets.UTF_8)
}

class DesktopAiClient {
    private val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    suspend fun test(config:ModelConfig):String=withContext(Dispatchers.IO){
        validate(config)
        val request=HttpRequest.newBuilder(URI.create(config.baseUrl.trimEnd('/')+"/models")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer ${config.apiKey}").GET().build()
        val response=http.send(request,HttpResponse.BodyHandlers.ofString())
        if(response.statusCode() !in 200..299) throw apiError(response.statusCode(),response.body())
        "连接成功（HTTP ${response.statusCode()}）"
    }
    suspend fun generate(config:ModelConfig,system:String,prompt:String,onDelta:(String)->Unit):String=withContext(Dispatchers.IO){
        validate(config)
        val body=JSONObject().put("model",config.model).put("stream",true).put("messages",JSONArray().put(JSONObject().put("role","system").put("content",system)).put(JSONObject().put("role","user").put("content",prompt))).toString()
        val request=HttpRequest.newBuilder(URI.create(config.baseUrl.trimEnd('/')+"/chat/completions")).timeout(Duration.ofMinutes(10)).header("Authorization","Bearer ${config.apiKey}").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response=http.send(request,HttpResponse.BodyHandlers.ofLines())
        if(response.statusCode() !in 200..299) throw apiError(response.statusCode(),response.body().limit(1).findFirst().orElse(""))
        buildString { response.body().forEach { line -> if(line.startsWith("data:")){ val raw=line.removePrefix("data:").trim();if(raw!="[DONE]")runCatching{JSONObject(raw).getJSONArray("choices").getJSONObject(0).optJSONObject("delta")?.optString("content").orEmpty()}.getOrDefault("").takeIf(String::isNotEmpty)?.let{append(it);onDelta(it)} } } }
    }
    private fun validate(c:ModelConfig){require(c.baseUrl.startsWith("https://")||c.baseUrl.startsWith("http://localhost")){"Base URL 必须使用 HTTPS"};require(c.apiKey.isNotBlank()){"请填写 API Key"};require(c.model.isNotBlank()){"请填写模型名称"}}
    private fun apiError(status:Int,body:String)=IllegalStateException(when(status){401,403->"鉴权失败，请检查 API Key";429->"请求过于频繁或额度不足";in 500..599->"模型服务暂时不可用（$status）";else->"模型接口错误（$status）：${body.take(200)}"})
}

internal fun atomicWrite(path:java.nio.file.Path,bytes:ByteArray){ Files.createDirectories(path.parent);val temp=path.resolveSibling(path.fileName.toString()+".tmp");Files.write(temp,bytes);runCatching{Files.move(temp,path,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)}.getOrElse{Files.move(temp,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING)} }
