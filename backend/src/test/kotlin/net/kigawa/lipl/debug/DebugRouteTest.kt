package net.kigawa.lipl.debug

import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import net.kigawa.lipl.ai.AiGenerationUsageTable
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.ai.FakeClaudeClient
import net.kigawa.lipl.ai.InterviewMessagesTable
import net.kigawa.lipl.ai.InterviewRepository
import net.kigawa.lipl.ai.LpContentsTable
import net.kigawa.lipl.ai.LpRepository
import net.kigawa.lipl.module
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// このテストは、DEBUG_MENU_ENABLEDが無効な場合にai-usageエンドポイント自体が
// 存在しないこと（フロントエンドの表示制御だけに頼らず、サーバー側で確実に
// 無効化されること）と、有効な場合は認証を要求することを確認する。
class DebugRouteTest {

    private val interviewRepository = InterviewRepository(FakeClaudeClient(emptyList()))
    private val lpRepository = LpRepository(FakeClaudeClient(emptyList()), interviewRepository)

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:debug_route_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(InterviewMessagesTable, LpContentsTable, AiGenerationUsageTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(AiGenerationUsageTable, LpContentsTable, InterviewMessagesTable)
        }
    }

    @Test
    fun `ai-usage endpoints are not registered when the debug menu is disabled`() = testApplication {
        application {
            module(lpRepository = lpRepository, debugConfig = DebugConfig(debugMenuEnabled = false))
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/debug/ai-usage").status)
        assertEquals(HttpStatusCode.NotFound, client.put("/api/debug/ai-usage").status)
    }

    @Test
    fun `debug config reflects whether the debug menu is enabled`() = testApplication {
        application {
            module(lpRepository = lpRepository, debugConfig = DebugConfig(debugMenuEnabled = false))
        }

        val body = client.get("/api/debug/config").bodyAsText()
        assertEquals("""{"debugMenuEnabled":false}""", body)
    }

    @Test
    fun `ai-usage requires authentication when the debug menu is enabled`() = testApplication {
        val keycloakConfig = KeycloakConfig(issuer = "https://example.invalid/realms/lipl", audience = "account")
        application {
            module(
                keycloakConfig = keycloakConfig,
                lpRepository = lpRepository,
                debugConfig = DebugConfig(debugMenuEnabled = true),
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/debug/ai-usage").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/api/debug/ai-usage").status)
    }
}
