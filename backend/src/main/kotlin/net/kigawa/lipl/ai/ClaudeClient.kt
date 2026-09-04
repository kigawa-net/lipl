package net.kigawa.lipl.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClaudeMessage(val role: String, val content: String)

interface ClaudeClient {
    suspend fun complete(systemPrompt: String, messages: List<ClaudeMessage>): String
}

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
)

@Serializable
private data class ClaudeResponseBlock(val type: String, val text: String? = null)

@Serializable
private data class ClaudeResponse(val content: List<ClaudeResponseBlock>)

class AnthropicClaudeClient(private val config: ClaudeConfig) : ClaudeClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    override suspend fun complete(systemPrompt: String, messages: List<ClaudeMessage>): String {
        val response = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", config.apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(
                ClaudeRequest(
                    model = config.model,
                    maxTokens = 1024,
                    system = systemPrompt,
                    messages = messages,
                ),
            )
        }.body<ClaudeResponse>()

        return response.content.firstOrNull { it.type == "text" }?.text
            ?: error("Claude APIから予期しない応答形式を受け取りました")
    }
}
