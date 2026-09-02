package net.kigawa.lipl.kaft

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import java.util.Date
import java.util.UUID

@Serializable
private data class TokenRequest(
    val scope: String,
    val uuid: String? = null,
    val uuids: List<String>? = null,
)

@Serializable
private data class TokenResponse(val token: String)

@Serializable
private data class VisibilityRequest(val visibility: String)

class KaftClient(private val config: KaftConfig) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    private val algorithm = Algorithm.HMAC256(config.internalJwtSecret)

    // kaft側（JwtService.internalVerifier）はissuer="api-server"・audience="kaft"を
    // 必須クレームとして要求する（kaftのapplication.confのデフォルト値、
    // KAFT_INTERNAL_JWT_ISSUER/KAFT_INTERNAL_JWT_AUDIENCEで上書き可能だが
    // lipl側では未設定のため常にデフォルト値を使う）。
    internal fun internalToken(): String =
        JWT.create()
            .withIssuer("api-server")
            .withAudience("kaft")
            .withClaim("scope", "internal")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
            .sign(algorithm)

    /** アップロード用のUUIDとupload_tokenを新規発行する。 */
    suspend fun issueUploadToken(): Pair<String, String> {
        val uuid = UUID.randomUUID().toString()
        val response = client.post("${config.baseUrl}/internal/token") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${internalToken()}")
            setBody(TokenRequest(scope = "upload", uuid = uuid))
        }.body<TokenResponse>()
        return uuid to response.token
    }

    /** アップロード確定後、public設定にする。 */
    suspend fun confirmAndPublish(uuid: String) {
        client.post("${config.baseUrl}/internal/files/$uuid/confirm") {
            header("Authorization", "Bearer ${internalToken()}")
        }
        client.patch("${config.baseUrl}/internal/files/$uuid/visibility") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${internalToken()}")
            setBody(VisibilityRequest(visibility = "public"))
        }
    }

    suspend fun delete(uuid: String) {
        client.delete("${config.baseUrl}/internal/files/$uuid") {
            header("Authorization", "Bearer ${internalToken()}")
        }
    }
}
