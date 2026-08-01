package com.mozhou.novelcraft.core

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class ImportAnalysisFailure(
    val status: String,
    val stage: String,
    val detail: String,
    val shouldRetry: Boolean,
)

object ImportAnalysisFailureClassifier {
    fun classify(error: Exception, hasValidatedNetwork: Boolean): ImportAnalysisFailure {
        if (!hasValidatedNetwork) {
            return ImportAnalysisFailure(
                status = ImportAnalysisStatus.WAITING_FOR_NETWORK,
                stage = "等待网络",
                detail = "系统尚未确认网络可用，恢复后将自动重试",
                shouldRetry = true,
            )
        }
        val reason = when (error) {
            is UnknownHostException -> "模型域名无法解析"
            is SocketTimeoutException -> "连接模型服务超时"
            is ConnectException -> "模型服务拒绝连接"
            is SSLException -> "模型服务的 HTTPS 证书校验失败"
            else -> "无法连接模型服务"
        }
        val original = error.message?.take(120)?.takeIf { it.isNotBlank() }
        return ImportAnalysisFailure(
            status = ImportAnalysisStatus.FAILED,
            stage = "模型连接失败",
            detail = buildString {
                append(reason)
                original?.let { append("：").append(it) }
                append("。请检查“我的”中的 Base URL、协议和模型连通性后重试")
            },
            shouldRetry = false,
        )
    }
}

