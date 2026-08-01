package com.mozhou.novelcraft.desktop

typealias OnlineResearchResult = com.mozhou.novelcraft.core.OnlineResearchResult

object OnlineResearchClient {
    suspend fun search(query: String): List<OnlineResearchResult> =
        com.mozhou.novelcraft.core.OnlineResearchClient.search(query)
}
