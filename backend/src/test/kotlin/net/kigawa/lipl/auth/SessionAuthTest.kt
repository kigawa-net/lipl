package net.kigawa.lipl.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// KeycloakのJWKS/リフレッシュ動作を実サーバーなしで検証するため、自前のRSA鍵ペアで
// JWTに署名し、JwkProviderをその公開鍵を返すフェイクに差し替える。
class SessionAuthTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey
    private val issuer = "https://example.invalid/realms/lipl"
    private val kid = "test-kid"

    private val fakeJwkProvider = object : JwkProvider {
        override fun get(keyId: String): Jwk {
            fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            return Jwk.fromValues(
                mapOf(
                    "kid" to keyId,
                    "kty" to "RSA",
                    "alg" to "RS256",
                    "n" to encode(publicKey.modulus.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }),
                    "e" to encode(publicKey.publicExponent.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }),
                ),
            )
        }
    }

    private fun signToken(expiresInSeconds: Long): String =
        JWT.create()
            .withKeyId(kid)
            .withIssuer(issuer)
            .withSubject("owner-1")
            .withExpiresAt(Date.from(Instant.now().plusSeconds(expiresInSeconds)))
            .sign(Algorithm.RSA256(publicKey, privateKey))

    private fun config() = KeycloakConfig(
        issuer = issuer,
        audience = "account",
        backendClientId = "lipl-backend",
        backendClientSecret = "secret",
    )

    @Test
    fun `missing cookie yields no principal`() = testApplication {
        application {
            routing {
                get("/check") {
                    val sessionAuth = SessionAuth(config(), fakeJwkProvider, HttpClient(MockEngine { respond("") }), SessionCookieCodec(ByteArray(32)))
                    val principal = sessionAuth.authenticate(call)
                    call.respond(if (principal == null) HttpStatusCode.Unauthorized else HttpStatusCode.OK)
                }
            }
        }
        val response = client.get("/check")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `valid unexpired access token authenticates without calling refresh`() = testApplication {
        val codec = SessionCookieCodec(ByteArray(32))
        val session = SessionPayload(
            accessToken = signToken(expiresInSeconds = 300),
            refreshToken = "refresh-token",
            accessExpiresAt = Instant.now().epochSecond + 300,
            refreshExpiresAt = Instant.now().epochSecond + 3600,
        )
        var refreshCalled = false
        val client = HttpClient(MockEngine { refreshCalled = true; respond("") })

        application {
            routing {
                get("/check") {
                    val sessionAuth = SessionAuth(config(), fakeJwkProvider, client, codec)
                    val principal = sessionAuth.authenticate(call)
                    call.respond(if (principal?.payload?.subject == "owner-1") HttpStatusCode.OK else HttpStatusCode.Unauthorized)
                }
            }
        }
        val response = this.client.get("/check") {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE_NAME=${codec.encode(session)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(false, refreshCalled)
    }

    @Test
    fun `expired access token triggers refresh and re-authenticates`() = testApplication {
        val codec = SessionCookieCodec(ByteArray(32))
        val expiredSession = SessionPayload(
            accessToken = signToken(expiresInSeconds = -300),
            refreshToken = "old-refresh-token",
            accessExpiresAt = Instant.now().epochSecond - 300,
            refreshExpiresAt = Instant.now().epochSecond + 3600,
        )
        val newAccessToken = signToken(expiresInSeconds = 300)
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"access_token":"$newAccessToken","refresh_token":"new-refresh-token","expires_in":300,"refresh_expires_in":3600}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        var settCookie: String? = null
        application {
            routing {
                get("/check") {
                    val sessionAuth = SessionAuth(config(), fakeJwkProvider, mockClient, codec)
                    val principal = sessionAuth.authenticate(call)
                    settCookie = call.response.headers["Set-Cookie"]
                    call.respond(if (principal?.payload?.subject == "owner-1") HttpStatusCode.OK else HttpStatusCode.Unauthorized)
                }
            }
        }
        val response = client.get("/check") {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE_NAME=${codec.encode(expiredSession)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assert(settCookie?.contains(SESSION_COOKIE_NAME) == true) { "refreshed session cookie should be set" }
    }

    @Test
    fun `refresh failure clears the cookie and yields no principal`() = testApplication {
        val codec = SessionCookieCodec(ByteArray(32))
        val expiredSession = SessionPayload(
            accessToken = signToken(expiresInSeconds = -300),
            refreshToken = "old-refresh-token",
            accessExpiresAt = Instant.now().epochSecond - 300,
            refreshExpiresAt = Instant.now().epochSecond + 3600,
        )
        val mockClient = HttpClient(MockEngine { respond("invalid_grant", HttpStatusCode.BadRequest) })

        var setCookieHeader: String? = null
        application {
            routing {
                get("/check") {
                    val sessionAuth = SessionAuth(config(), fakeJwkProvider, mockClient, codec)
                    val principal = sessionAuth.authenticate(call)
                    setCookieHeader = call.response.headers["Set-Cookie"]
                    call.respond(if (principal == null) HttpStatusCode.Unauthorized else HttpStatusCode.OK)
                }
            }
        }
        val response = client.get("/check") {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE_NAME=${codec.encode(expiredSession)}")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assert(setCookieHeader?.contains("$SESSION_COOKIE_NAME=;") == true || setCookieHeader?.contains("Max-Age=0") == true) {
            "cookie should be cleared on refresh failure, was: $setCookieHeader"
        }
    }
}
