package net.kigawa.lipl.photo

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.kaft.KaftClient
import net.kigawa.lipl.kaft.KaftConfig
import net.kigawa.lipl.module
import net.kigawa.lipl.store.StoreRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class KaftConfigRouteTest {
    @Test
    fun `kaft-config endpoint returns the public base url`() = testApplication {
        val kaftConfig = KaftConfig(
            baseUrl = "http://kaft.internal:8080",
            publicBaseUrl = "https://kaft-stg.kigawa.net",
            internalJwtSecret = "test-secret",
        )
        application {
            module(
                storeRepository = StoreRepository(),
                photoRepository = PhotoRepository(),
                keycloakConfig = KeycloakConfig(issuer = "https://example.invalid/realms/lipl", audience = "account"),
                kaftClient = KaftClient(kaftConfig),
                kaftConfig = kaftConfig,
            )
        }

        val response = client.get("/api/kaft-config")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"kaftBaseUrl":"https://kaft-stg.kigawa.net"}""", response.bodyAsText())
    }
}
