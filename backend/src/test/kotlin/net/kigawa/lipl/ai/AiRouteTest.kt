package net.kigawa.lipl.ai

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.module
import net.kigawa.lipl.store.BusinessCategory
import net.kigawa.lipl.store.CreateStoreRequest
import net.kigawa.lipl.store.StoreRepository
import net.kigawa.lipl.store.StoreSnsLinksTable
import net.kigawa.lipl.store.StoresTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// 実際のKeycloak検証はしない（testApplicationはJWKS未設定のため401になる）。
// このテストはAIヒアリング・LPルートがauthenticate("keycloak")配下に正しく
// 登録され、未認証リクエストを拒否することのみを確認する。
class AiRouteTest {

    private val storeRepository = StoreRepository()
    private val interviewRepository = InterviewRepository(FakeClaudeClient(emptyList()))
    private val lpRepository = LpRepository(FakeClaudeClient(emptyList()), interviewRepository)
    private val keycloakConfig = KeycloakConfig(issuer = "https://example.invalid/realms/lipl", audience = "account")

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:ai_route_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable, InterviewMessagesTable, LpContentsTable, AiGenerationUsageTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(AiGenerationUsageTable, LpContentsTable, InterviewMessagesTable, StoreSnsLinksTable, StoresTable)
        }
    }

    private fun testApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(
                storeRepository = storeRepository,
                keycloakConfig = keycloakConfig,
                interviewRepository = interviewRepository,
                lpRepository = lpRepository,
            )
        }
        block()
    }

    @Test
    fun `get interview state without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        val response = client.get("/api/stores/${store.id}/interview")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `send interview message without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        val response = client.post("/api/stores/${store.id}/interview/messages")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `get lp content without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        val response = client.get("/api/stores/${store.id}/lp")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `update lp content without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        val response = client.put("/api/stores/${store.id}/lp")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `generate lp content without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        val response = client.post("/api/stores/${store.id}/lp/generate")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
