package net.kigawa.lipl.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.Payload
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.response.respond
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.concurrent.TimeUnit

const val SESSION_COOKIE_NAME = "lipl_session"

data class KeycloakConfig(
    val issuer: String,
    val audience: String,
    // BFFのOIDCコード交換用confidentialクライアント。既存テストの多くはKeycloakConfigを
    // issuer/audienceのみで直接構築するため、ここにはデフォルト値を与えて互換性を保つ
    // （ログインフロー自体をテストしないケースでは値が空でも問題ない）。
    val backendClientId: String = "",
    val backendClientSecret: String = "",
) {
    val jwksUri: String = "$issuer/protocol/openid-connect/certs"
    val authorizationEndpoint: String = "$issuer/protocol/openid-connect/auth"
    val tokenEndpoint: String = "$issuer/protocol/openid-connect/token"
    val endSessionEndpoint: String = "$issuer/protocol/openid-connect/logout"
}

fun keycloakConfigFromEnv(): KeycloakConfig {
    val issuer = System.getenv("KEYCLOAK_ISSUER")
        ?: error("環境変数 KEYCLOAK_ISSUER が設定されていません（例: https://user.kigawa.net/realms/lipl）")
    val audience = System.getenv("KEYCLOAK_AUDIENCE") ?: "account"
    return KeycloakConfig(
        issuer = issuer,
        audience = audience,
        backendClientId = System.getenv("KEYCLOAK_BACKEND_CLIENT_ID")
            ?: error("環境変数 KEYCLOAK_BACKEND_CLIENT_ID が設定されていません"),
        backendClientSecret = System.getenv("KEYCLOAK_BACKEND_CLIENT_SECRET")
            ?: error("環境変数 KEYCLOAK_BACKEND_CLIENT_SECRET が設定されていません"),
    )
}

val JWTPrincipal.ownerSub: String
    get() = payload.subject ?: error("JWTにsubjectクレームがありません")

private val tokenResponseJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class RefreshTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long,
)

// ブラウザにはアクセストークン・リフレッシュトークンを一切渡さず、暗号化したhttpOnly Cookie
// （lipl_session）にのみ保持する（BFF方式）。アクセストークンの検証は既存のKeycloak JWKS
// 検証をそのまま使い、期限切れ間近ならKeycloakのrefresh_tokenグラントで裏側で更新し、
// Cookieを書き換えることでログインを永続化する（Cookieの寿命はKeycloakのrefresh_expires_in
// に追従するスライディングウィンドウ）。
class SessionAuth(
    private val config: KeycloakConfig,
    private val jwkProvider: JwkProvider,
    private val client: HttpClient,
    private val codec: SessionCookieCodec,
) {
    private val expiryLeewaySeconds = 30L

    suspend fun authenticate(call: ApplicationCall): JWTPrincipal? {
        val cookieValue = call.request.cookies[SESSION_COOKIE_NAME] ?: return null
        val session = codec.decode(cookieValue) ?: run {
            clearCookie(call)
            return null
        }

        val now = Instant.now().epochSecond
        if (now < session.accessExpiresAt - expiryLeewaySeconds) {
            val payload = verifyAccessToken(session.accessToken)
            if (payload != null) return JWTPrincipal(payload)
        }

        val refreshed = try {
            refresh(session.refreshToken)
        } catch (e: Exception) {
            // Keycloakへの疎通自体に失敗した場合（タイムアウト等）はリフレッシュトークンが
            // 無効と決まったわけではないため、Cookieはクリアせず今回のリクエストのみ
            // 未認証（401）として扱う。次回リクエストで再度リフレッシュを試みられるようにする。
            logger.warn("Keycloakへのトークンリフレッシュ通信に失敗しました", e)
            return null
        } ?: run {
            clearCookie(call)
            return null
        }
        val payload = verifyAccessToken(refreshed.accessToken) ?: run {
            clearCookie(call)
            return null
        }
        writeCookie(call, refreshed)
        return JWTPrincipal(payload)
    }

    private fun verifyAccessToken(token: String): Payload? = try {
        val kid = JWT.decode(token).keyId
        val jwk = jwkProvider.get(kid)
        val algorithm = Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null)
        JWT.require(algorithm).withIssuer(config.issuer).acceptLeeway(5).build().verify(token)
    } catch (e: JWTVerificationException) {
        null
    } catch (e: Exception) {
        logger.warn("アクセストークンの検証に失敗しました", e)
        null
    }

    private suspend fun refresh(refreshToken: String): SessionPayload? {
        val response = client.submitForm(
            url = config.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("client_id", config.backendClientId)
                append("client_secret", config.backendClientSecret)
                append("refresh_token", refreshToken)
            },
        )
        if (!response.status.isSuccess()) {
            logger.warn("Keycloakトークンのリフレッシュに失敗しました（status={}）: {}", response.status, response.bodyAsText())
            return null
        }
        val parsed = try {
            tokenResponseJson.decodeFromString(RefreshTokenResponse.serializer(), response.bodyAsText())
        } catch (e: Exception) {
            logger.warn("Keycloakトークンのリフレッシュ応答のパースに失敗しました", e)
            return null
        }
        val now = Instant.now().epochSecond
        return SessionPayload(
            accessToken = parsed.accessToken,
            refreshToken = parsed.refreshToken,
            accessExpiresAt = now + parsed.expiresIn,
            refreshExpiresAt = now + parsed.refreshExpiresIn,
        )
    }

    fun writeCookie(call: ApplicationCall, session: SessionPayload) {
        val now = Instant.now().epochSecond
        val maxAge = (session.refreshExpiresAt - now).coerceAtLeast(60L)
        call.response.cookies.append(
            name = SESSION_COOKIE_NAME,
            value = codec.encode(session),
            maxAge = maxAge,
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        )
    }

    fun clearCookie(call: ApplicationCall) {
        call.response.cookies.append(
            name = SESSION_COOKIE_NAME,
            value = "",
            maxAge = 0L,
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SessionAuth::class.java)
    }
}

fun buildJwkProvider(config: KeycloakConfig): JwkProvider =
    JwkProviderBuilder(URI(config.jwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

// authenticate("keycloak") { ... call.principal<JWTPrincipal>() ... }という各ルートの既存コードは
// 変更しない。トークンの取得元をAuthorizationヘッダーからCookieに差し替えるため、
// Ktorのjwt()プロバイダではなく自前のAuthenticationProviderを登録する。
private class CookieAuthenticationProvider(config: Config) : AuthenticationProvider(config) {
    class Config(name: String) : AuthenticationProvider.Config(name) {
        lateinit var sessionAuth: SessionAuth
    }

    private val sessionAuth = (config as Config).sessionAuth

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val principal = sessionAuth.authenticate(context.call)
        if (principal != null) {
            context.principal(principal)
        } else {
            context.challenge("CookieAuth", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized)
                challenge.complete()
            }
        }
    }
}

private fun AuthenticationConfig.cookieSession(name: String, sessionAuth: SessionAuth) {
    register(
        CookieAuthenticationProvider(CookieAuthenticationProvider.Config(name).also { it.sessionAuth = sessionAuth }),
    )
}

fun Application.configureKeycloakAuth(sessionAuth: SessionAuth) {
    authentication {
        cookieSession("keycloak", sessionAuth)
    }
}
