package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.ModelConfig
import com.mozhou.novelcraft.core.GenerationRequest
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatibleAiClientTest {
    @Test fun connectionTestReportsUnauthorizedResponse() {
        withServer { server ->
            server.createContext("/models") { exchange ->
                val body = "{\"error\":\"bad key\"}".toByteArray()
                exchange.sendResponseHeaders(401, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            val error = runBlocking { OpenAiCompatibleClient(true).test(config(server)).exceptionOrNull() }
            assertTrue(requireNotNull(error).message.orEmpty().contains("401"))
        }
    }

    @Test fun connectionTestRetriesRateLimitedResponse() {
        withServer { server ->
            val requests = AtomicInteger()
            server.createContext("/models") { exchange ->
                requests.incrementAndGet()
                val body = "{\"error\":\"rate limited\"}".toByteArray()
                exchange.sendResponseHeaders(429, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            val error = runBlocking { OpenAiCompatibleClient(true).test(config(server)).exceptionOrNull() }
            assertTrue(requireNotNull(error).message.orEmpty().contains("429"))
            assertEquals(3, requests.get())
        }
    }

    @Test fun connectionTestRejectsConfiguredModelMissingFromModelList() {
        withServer { server ->
            server.createContext("/models") { exchange ->
                val body = "{\"data\":[{\"id\":\"available-model\"}]}".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            val error = runBlocking {
                OpenAiCompatibleClient(true).test(config(server).copy(model = "missing-model")).exceptionOrNull()
            }
            assertTrue(requireNotNull(error).message.orEmpty().contains("missing-model"))
        }
    }

    @Test fun streamCancellationKeepsAlreadyReceivedTextAvailableToCaller() {
        withServer { server ->
            server.createContext("/chat/completions") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.bufferedWriter().use { writer ->
                    writer.write("data: {\"choices\":[{\"delta\":{\"content\":\"已接收草稿\"}}]}\n\n")
                    writer.flush()
                    Thread.sleep(2_000)
                }
            }
            val request = GenerationRequest()
            val received = StringBuilder()
            val result = runBlocking {
                OpenAiCompatibleClient(true).continueWriting(config(server), "测试上下文", request) { delta ->
                    received.append(delta)
                    request.cancel()
                }
            }
            assertTrue(received.contains("已接收草稿"))
            assertTrue(result.isFailure)
        }
    }

    private fun config(server: HttpServer) = ModelConfig(baseUrl = "http://127.0.0.1:${server.address.port}", apiKey = "test-key", model = "test-model")

    private fun withServer(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }
}
