package com.mozhou.novelcraft.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException

internal const val AI_REQUEST_MAX_ATTEMPTS = 3

class AiHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

internal class AiRetryExhaustedException(
    attempts: Int,
    cause: Throwable,
) : IOException("请求已自动重试 $attempts 次仍失败：${cause.message ?: cause.javaClass.simpleName}", cause)

internal object AiRetryPolicy {
    fun shouldRetry(error: Throwable): Boolean = when (error) {
        is AiHttpException -> error.statusCode == 408 || error.statusCode == 425 || error.statusCode == 429 || error.statusCode >= 500
        is IOException -> true
        else -> false
    }
}

suspend fun <T> retryAiRequest(
    request: GenerationRequest? = null,
    canRetry: () -> Boolean = { true },
    operation: suspend () -> T,
): T {
    repeat(AI_REQUEST_MAX_ATTEMPTS) { attempt ->
        currentCoroutineContext().ensureActive()
        if (request?.isCancelled == true) throw CancellationException("AI request cancelled")
        try {
            return operation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val lastAttempt = attempt == AI_REQUEST_MAX_ATTEMPTS - 1
            if (!AiRetryPolicy.shouldRetry(failure) || !canRetry() || request?.isCancelled == true) throw failure
            if (lastAttempt) throw AiRetryExhaustedException(AI_REQUEST_MAX_ATTEMPTS, failure)
            delay(500L * (1L shl attempt))
        }
    }
    error("Unreachable")
}

