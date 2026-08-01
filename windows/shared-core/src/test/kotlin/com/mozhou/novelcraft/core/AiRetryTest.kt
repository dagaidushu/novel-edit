package com.mozhou.novelcraft.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CancellationException
import java.io.IOException

class AiRetryTest {
    @Test
    fun retriesTransientFailureUntilRequestSucceeds() = runTest {
        var attempts = 0

        val result = retryAiRequest {
            attempts += 1
            if (attempts < 3) throw IOException("temporary network failure")
            "done"
        }

        assertEquals("done", result)
        assertEquals(3, attempts)
    }

    @Test
    fun doesNotRetryPermanentHttpFailure() = runTest {
        var attempts = 0

        val error = runCatching {
            retryAiRequest {
                attempts += 1
                throw AiHttpException(401, "invalid API key")
            }
        }.exceptionOrNull()

        assertTrue(error is AiHttpException)
        assertEquals(1, attempts)
    }

    @Test
    fun identifiesOnlyTransientHttpStatusesAsRetryable() {
        assertTrue(AiRetryPolicy.shouldRetry(AiHttpException(429, "busy")))
        assertTrue(AiRetryPolicy.shouldRetry(AiHttpException(503, "unavailable")))
        assertFalse(AiRetryPolicy.shouldRetry(AiHttpException(400, "bad request")))
        assertFalse(AiRetryPolicy.shouldRetry(AiHttpException(401, "unauthorized")))
    }

    @Test
    fun cancelledRequestDoesNotStartAnotherAttempt() = runTest {
        val request = GenerationRequest().apply { cancel() }
        var attempts = 0

        val error = runCatching {
            retryAiRequest(request) {
                attempts += 1
                "unexpected"
            }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(0, attempts)
    }
}

