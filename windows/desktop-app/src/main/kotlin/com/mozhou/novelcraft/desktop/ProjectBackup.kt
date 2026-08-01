package com.mozhou.novelcraft.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

class ProjectBackup(private val db:DesktopDatabase,private val paths:AppPaths){
    fun export(projectId:Long,path:Path){
        val project=db.rawProject(projectId).toMutableMap()
        val cover=project["coverPath"]?.toString().orEmpty().takeIf(String::isNotBlank)?.let{runCatching{Files.readAllBytes(Path.of(it))}.getOrNull()}
        project["coverPath"]=""
        val data=JSONObject().put("project",mapJson(project));DesktopDatabase.PROJECT_TABLES.sorted().forEach{table->data.put(table,rowsJson(db.rawRows(table,projectId)))};data.put("style_profiles",rowsJson(db.rawStyleProfiles(projectId)));data.put("coverBase64",cover?.let{Base64.getEncoder().encodeToString(it)}?:"")
        val payload=canonical(data)
        val root=JSONObject().put("format",FORMAT).put("version",2).put("createdBy","NovelEdit Windows 1.0.1").put("checksum",sha256(payload)).put("data",data)
        atomicWrite(path,root.toString(2).toByteArray(StandardCharsets.UTF_8))
    }
    fun import(path:Path):Long{
        val root=JSONObject(Files.readString(path));require(root.optString("format") in SUPPORTED_FORMATS){"不是 NovelEdit 项目备份"}
        val version=root.optInt("version");require(version in 1..2){"不支持的备份版本：$version"}
        val data=if(version==2)root.getJSONObject("data").also{require(root.optString("checksum")==sha256(canonical(it))){"备份校验失败，文件可能已损坏"}}else normalizeV1(root)
        val project=jsonMap(data.getJSONObject("project")).toMutableMap().apply{this["coverPath"]=""}
        val tables=mutableMapOf<String,List<Map<String,Any?>>>()
        DesktopDatabase.PROJECT_TABLES.forEach{table->tables[table]=jsonRows(data.optJSONArray(table))};tables["style_profiles"]=jsonRows(data.optJSONArray("style_profiles"))
        val projectId=db.importRows(project,tables)
        data.optString("coverBase64").takeIf(String::isNotBlank)?.let{encoded->val file=paths.covers.resolve("project-$projectId.jpg");atomicWrite(file,Base64.getDecoder().decode(encoded));db.project(projectId)?.let{db.updateProject(it.copy(coverPath=file.toString()))}}
        return projectId
    }
    private fun normalizeV1(root:JSONObject)=JSONObject().also{out->out.put("project",root.getJSONObject("project"));out.put("coverBase64",root.optString("coverBase64"));val aliases=mapOf("chapters" to "chapters","chapter_revisions" to "revisions","auto_write_runs" to "autoWriteRuns","story_items" to "items","chapter_story_mentions" to "mentions","research_notes" to "notes","editorial_reviews" to "editorialReviews","story_anchors" to "anchors","story_edges" to "edges","chapter_pacing_events" to "pacingEvents","batch_review_runs" to "batchReviews","review_issues" to "reviewIssues","style_profiles" to "styleProfiles","event_matrix_rules" to "eventMatrixRules","chapter_gate_reports" to "gateReports");aliases.forEach{(table,key)->out.put(table,root.optJSONArray(key)?:JSONArray())}}
    private fun rowsJson(rows:List<Map<String,Any?>>)=JSONArray().also{array->rows.forEach{array.put(mapJson(it))}}
    private fun mapJson(row:Map<String,Any?>)=JSONObject().also{json->row.forEach{(k,v)->json.put(k,v?:JSONObject.NULL)}}
    private fun jsonRows(array:JSONArray?)=buildList{for(i in 0 until(array?.length()?:0))add(jsonMap(array!!.getJSONObject(i)))}
    private fun jsonMap(json:JSONObject)=json.keySet().associateWith{k->json.opt(k).takeUnless{it==JSONObject.NULL}}
    private fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun canonical(value:Any?):String=when(value){is JSONObject->value.keySet().sorted().joinToString(prefix="{",postfix="}"){JSONObject.quote(it)+":"+canonical(value.opt(it))};is JSONArray->(0 until value.length()).joinToString(prefix="[",postfix="]"){canonical(value.opt(it))};is String->JSONObject.quote(value);JSONObject.NULL,null->"null";else->value.toString()}
    companion object{
        private const val FORMAT="noveledit-project-backup"
        private val SUPPORTED_FORMATS=setOf(FORMAT,"novelcraft-project-backup")
    }
}
