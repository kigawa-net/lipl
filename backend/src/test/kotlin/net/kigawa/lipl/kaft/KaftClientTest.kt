package net.kigawa.lipl.kaft

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KaftClientTest {

    private val config = KaftConfig(
        baseUrl = "http://kaft.internal:8080",
        publicBaseUrl = "https://kaft-stg.kigawa.net",
        internalJwtSecret = "test-secret",
    )

    // kaftのJwtService.internalVerifierは issuer="api-server" / audience="kaft" /
    // claim scope="internal" を必須とする（kaft側のデフォルト設定）。
    // このテストはlipl側が発行するinternal tokenがその要件を満たすことを保証する。
    @Test
    fun `internal token satisfies kaft's expected issuer, audience and scope claim`() {
        val client = KaftClient(config)

        val token = client.internalToken()
        val verifier = JWT.require(Algorithm.HMAC256(config.internalJwtSecret))
            .withIssuer("api-server")
            .withAudience("kaft")
            .withClaim("scope", "internal")
            .build()
        val decoded = verifier.verify(token)

        assertEquals("api-server", decoded.issuer)
        assertTrue(decoded.audience.contains("kaft"))
        assertEquals("internal", decoded.getClaim("scope").asString())
    }
}
