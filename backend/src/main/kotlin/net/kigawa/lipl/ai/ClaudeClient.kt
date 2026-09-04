package net.kigawa.lipl.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

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

@Serializable
private data class KeycloakTokenResponse(@SerialName("access_token") val accessToken: String)

@Serializable
private data class AnthropicTokenRequest(
    @SerialName("grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:jwt-bearer",
    val assertion: String,
    @SerialName("federation_rule_id") val federationRuleId: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("service_account_id") val serviceAccountId: String,
    @SerialName("workspace_id") val workspaceId: String? = null,
)

@Serializable
private data class AnthropicTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

private data class CachedToken(val accessToken: String, val expiresAt: Instant)

// AnthropicのAPIキーは保持しない。KeycloakのclientCredentialsグラントでJWTを取得し、
// それをAnthropicの短命アクセストークン（sk-ant-oat01-...）と交換して使う
// （Workload Identity Federation）。トークンは期限切れ120秒前まで再利用する。
class AnthropicClaudeClient(private val config: ClaudeConfig) : ClaudeClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    private val federation = config.federation
    private val mutex = Mutex()
    @Volatile private var cachedToken: CachedToken? = null

    override suspend fun complete(systemPrompt: String, messages: List<ClaudeMessage>): String {
        val response = client.post("https://api.anthropic.com/v1/messages") {
            header("authorization", "Bearer ${anthropicAccessToken()}")
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

    private suspend fun anthropicAccessToken(): String {
        cachedToken?.let { if (Instant.now().isBefore(it.expiresAt)) return it.accessToken }

        return mutex.withLock {
            cachedToken?.let { if (Instant.now().isBefore(it.expiresAt)) return@withLock it.accessToken }

            val jwt = fetchKeycloakToken()
            val response = client.post("https://api.anthropic.com/v1/oauth/token") {
                contentType(ContentType.Application.Json)
                setBody(
                    AnthropicTokenRequest(
                        assertion = jwt,
                        federationRuleId = federation.federationRuleId,
                        organizationId = federation.organizationId,
                        serviceAccountId = federation.serviceAccountId,
                        workspaceId = federation.workspaceId,
                    ),
                )
            }.body<AnthropicTokenResponse>()

            val refreshBufferSeconds = 120L
            val safeLifetime = (response.expiresIn - refreshBufferSeconds).coerceAtLeast(30L)
            val token = CachedToken(response.accessToken, Instant.now().plusSeconds(safeLifetime))
            cachedToken = token
            token.accessToken
        }
    }

    private suspend fun fetchKeycloakToken(): String =
        client.submitForm(
            url = federation.keycloakTokenUrl,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", federation.keycloakClientId)
                append("client_secret", federation.keycloakClientSecret)
            },
        ).body<KeycloakTokenResponse>().accessToken
}
