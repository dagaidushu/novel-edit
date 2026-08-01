package com.mozhou.novelcraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ImportAnalysisFailureClassifierTest {
    @Test
    fun validatedNetworkDoesNotMislabelModelTimeoutAsOffline() {
        val result = ImportAnalysisFailureClassifier.classify(SocketTimeoutException("timed out"), hasValidatedNetwork = true)

        assertEquals(ImportAnalysisStatus.FAILED, result.status)
        assertEquals("模型连接失败", result.stage)
        assertFalse(result.shouldRetry)
        assertTrue(result.detail.contains("超时"))
    }

    @Test
    fun unvalidatedNetworkWaitsForRecovery() {
        val result = ImportAnalysisFailureClassifier.classify(UnknownHostException("host"), hasValidatedNetwork = false)

        assertEquals(ImportAnalysisStatus.WAITING_FOR_NETWORK, result.status)
        assertTrue(result.shouldRetry)
    }
}

