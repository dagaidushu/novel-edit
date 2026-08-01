package com.mozhou.novelcraft.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Public, no-key research lookup. It stores citation metadata and a short source snippet only. */
data class OnlineResearchResult(
    val title: String,
    val excerpt: String,
    val sourceUrl: String,
    val sourceLabel: String,
    val retrievedAt: String,
)

object OnlineResearchClient {
    suspend fun search(query: String): List<OnlineResearchResult> = withContext(Dispatchers.IO) {
        val normalized = query.trim().take(120)
        require(normalized.isNotBlank()) { "请输入要调研的关键词" }
        coroutineScope {
            listOf(
                async { runCatching { searchWikipedia(normalized, "zh", "中文维基百科公开资料") }.getOrDefault(emptyList()) },
                async { runCatching { searchWikipedia(normalized, "en", "Wikipedia public reference") }.getOrDefault(emptyList()) },
                async { runCatching { searchOpenAlex(normalized) }.getOrDefault(emptyList()) },
            ).awaitAll().flatten()
                .distinctBy { it.sourceUrl }
                .take(12)
        }
    }

    private fun searchWikipedia(query: String, language: String, label: String): List<OnlineResearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = URL("https://$language.wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&srlimit=6&srprop=snippet&srsearch=$encoded")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "NovelEdit/1.0 research-citations")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val entries = JSONObject(json).getJSONObject("query").getJSONArray("search")
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            return buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.getJSONObject(index)
                    val title = item.optString("title").trim()
                    val excerpt = item.optString("snippet")
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (title.isNotBlank() && excerpt.isNotBlank()) {
                        add(
                            OnlineResearchResult(
                                title = title,
                                excerpt = excerpt,
                                sourceUrl = "https://$language.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8.name()).replace("+", "_"),
                                sourceLabel = label,
                                retrievedAt = now,
                            ),
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** OpenAlex is a public scholarly index. We retain only bibliographic metadata, never full text. */
    private fun searchOpenAlex(query: String): List<OnlineResearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = URL("https://api.openalex.org/works?search=$encoded&per-page=4&select=id,display_name,publication_year,doi,primary_location")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9_000
            readTimeout = 9_000
            setRequestProperty("User-Agent", "NovelEdit/1.0 research-citations")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val entries = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return emptyList()
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            return buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val title = item.optString("display_name").trim()
                    val location = item.optJSONObject("primary_location")
                    val venue = location?.optJSONObject("source")?.optString("display_name").orEmpty()
                    val year = item.optInt("publication_year", 0).takeIf { it > 0 }?.toString().orEmpty()
                    val doi = item.optString("doi").trim()
                    val sourceUrl = doi.ifBlank { item.optString("id").trim() }
                    if (title.isNotBlank() && sourceUrl.isNotBlank()) {
                        val detail = listOf(year, venue.ifBlank { "OpenAlex 公开文献索引" }).filter { it.isNotBlank() }.joinToString(" · ")
                        add(OnlineResearchResult(title, detail, sourceUrl, "OpenAlex 公开文献索引", now))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

