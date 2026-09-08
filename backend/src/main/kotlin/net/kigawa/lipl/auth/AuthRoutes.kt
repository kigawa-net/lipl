package net.kigawa.lipl.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.host
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

private const val STATE_COOKIE_NAME = "lipl_oauth_state"
private val logger = LoggerFactory.getLogger("net.kigawa.lipl.auth.AuthRoutes")
private val tokenResponseJson = Json { ignoreUnknownKeys = true }

// haproxy ingress配下では frontend（/）とbackend（/api）が同一ホストで提供されるため、
// フロントエンドの公開URLを表す環境変数は不要。X-Forwarded-*ヘッダーからスキーム・ホストを
// 復元してredirect_uriを組み立てる（TLSはingressで終端されるためscheme復元にはヘッダーが必要）。
private fun ApplicationCall.externalOrigin(): String {
    val scheme = request.header("X-Forwarded-Proto") ?: "https"
    val host = request.header("X-Forwarded-Host") ?: request.host()
    return "$scheme://$host"
}

private fun encodeParam(value: String): String = URLEncoder.encode(value, "UTF-8")

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long,
)

fun Route.authRoutes(config: KeycloakConfig, sessionAuth: SessionAuth, client: HttpClient) {
    get("/api/auth/login") {
        val state = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

        call.response.cookies.append(
            name = STATE_COOKIE_NAME,
            value = state,
            maxAge = 300L,
            path = "/api/auth",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        )

        val redirectUri = "${call.externalOrigin()}/api/auth/callback"
        val authUrl = buildString {
            append(config.authorizationEndpoint)
            append("?client_id=").append(encodeParam(config.backendClientId))
            append("&redirect_uri=").append(encodeParam(redirectUri))
            append("&response_type=code")
            append("&scope=openid")
            append("&state=").append(encodeParam(state))
        }
        call.respondRedirect(authUrl)
    }

    get("/api/auth/callback") {
        val code = call.request.queryParameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "codeパラメータがありません")
        val state = call.request.queryParameters["state"]
        val cookieState = call.request.cookies[STATE_COOKIE_NAME]
        if (state == null || cookieState == null || state != cookieState) {
            return@get call.respond(HttpStatusCode.BadRequest, "stateが一致しません")
        }

        val redirectUri = "${call.externalOrigin()}/api/auth/callback"
        val response = client.submitForm(
            url = config.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("client_id", config.backendClientId)
                append("client_secret", config.backendClientSecret)
                append("code", code)
                append("redirect_uri", redirectUri)
            },
        )
        if (!response.status.isSuccess()) {
            logger.error("Keycloakトークン交換に失敗しました（status={}）: {}", response.status, response.bodyAsText())
            return@get call.respond(HttpStatusCode.BadGateway, "ログインに失敗しました")
        }

        val parsed = try {
            tokenResponseJson.decodeFromString(TokenResponse.serializer(), response.bodyAsText())
        } catch (e: Exception) {
            logger.error("Keycloakトークン交換応答のパースに失敗しました", e)
            return@get call.respond(HttpStatusCode.BadGateway, "ログインに失敗しました")
        }

        val now = Instant.now().epochSecond
        sessionAuth.writeCookie(
            call,
            SessionPayload(
                accessToken = parsed.accessToken,
                refreshToken = parsed.refreshToken,
                accessExpiresAt = now + parsed.expiresIn,
                refreshExpiresAt = now + parsed.refreshExpiresIn,
            ),
        )
        call.response.cookies.append(
            name = STATE_COOKIE_NAME,
            value = "",
            maxAge = 0L,
            path = "/api/auth",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        )

        call.respondRedirect("/")
    }

    get("/api/auth/logout") {
        sessionAuth.clearCookie(call)
        // "/"はダッシュボード（要ログイン）のため、ログアウト直後にそこへ戻すと
        // 401→/loginへ即座に押し戻されてしまう。ログアウト後はマーケティングページ（/lp）へ。
        val postLogoutRedirectUri = "${call.externalOrigin()}/lp"
        val logoutUrl = buildString {
            append(config.endSessionEndpoint)
            append("?client_id=").append(encodeParam(config.backendClientId))
            append("&post_logout_redirect_uri=").append(encodeParam(postLogoutRedirectUri))
        }
        call.respondRedirect(logoutUrl)
    }

    authenticate("keycloak") {
        get("/api/auth/session") {
            call.respond(HttpStatusCode.OK)
        }
    }
}
