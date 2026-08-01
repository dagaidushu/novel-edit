package com.mozhou.novelcraft.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class GenerationRequestTest {
    @Test
    fun cancellationDisconnectsEveryConcurrentRequest() {
        val first = TestConnection()
        val second = TestConnection()
        val request = GenerationRequest()

        request.attach(first)
        request.attach(second)
        request.cancel()

        assertTrue(first.disconnected)
        assertTrue(second.disconnected)
    }

    private class TestConnection : HttpURLConnection(URL("https://example.test")) {
        var disconnected = false

        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy() = false
    }
}

