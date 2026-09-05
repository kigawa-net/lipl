package net.kigawa.lipl.ai

import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LpRepositoryTest {

    private val storeRepository = StoreRepository()
    private var storeId: Long = 0
    private val ownerSub = "owner-1"
    private val generationResponse =
        """{"catchphrase": "こだわりの一杯を、あなたに。", "pageHtml": "<p>自家焙煎の豆を使った特製ブレンドが自慢の喫茶店です。</p>"}"""

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:lp_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable, InterviewMessagesTable, LpContentsTable, AiGenerationUsageTable)
        }
        storeId = storeRepository.create(
            ownerSub,
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        ).id
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(AiGenerationUsageTable, LpContentsTable, InterviewMessagesTable, StoreSnsLinksTable, StoresTable)
        }
    }

    @Test
    fun `get returns null when nothing has been generated yet`() {
        val repository = LpRepository(FakeClaudeClient(emptyList()), InterviewRepository(FakeClaudeClient(emptyList())))

        assertNull(repository.get(storeId))
    }

    @Test
    fun `generate creates lp content and consumes the lifetime generation limit`() = runBlocking {
        val interviewRepository = InterviewRepository(FakeClaudeClient(listOf("質問1")))
        interviewRepository.sendMessage(storeId, "店舗情報", null)
        val repository = LpRepository(FakeClaudeClient(listOf(generationResponse)), interviewRepository)

        val content = repository.generate(storeId, ownerSub, "店舗情報")

        assertEquals("こだわりの一杯を、あなたに。", content.catchphrase)
        assertEquals("<p>自家焙煎の豆を使った特製ブレンドが自慢の喫茶店です。</p>", content.pageHtml)
        assertEquals(content, repository.get(storeId))
    }

    @Test
    fun `generate resets the interview session for a fresh next round`() = runBlocking {
        val interviewRepository = InterviewRepository(FakeClaudeClient(listOf("質問1")))
        interviewRepository.sendMessage(storeId, "店舗情報", null)
        val repository = LpRepository(FakeClaudeClient(listOf(generationResponse)), interviewRepository)

        repository.generate(storeId, ownerSub, "店舗情報")

        assertEquals(0, interviewRepository.getState(storeId).messages.size)
    }

    @Test
    fun `generate a second time for the same account throws`() = runBlocking {
        val interviewRepository = InterviewRepository(FakeClaudeClient(listOf("質問1")))
        val repository = LpRepository(FakeClaudeClient(listOf(generationResponse, generationResponse)), interviewRepository)
        repository.generate(storeId, ownerSub, "店舗情報")

        assertFailsWith<LpGenerationLimitExceededException> {
            repository.generate(storeId, ownerSub, "店舗情報")
        }
    }

    @Test
    fun `generate for a different account is not blocked by another account's usage`() = runBlocking {
        val otherStoreId = storeRepository.create(
            "owner-2",
            CreateStoreRequest(name = "店舗2", businessCategory = BusinessCategory.CAFE, address = "住所2"),
        ).id
        val interviewRepository = InterviewRepository(FakeClaudeClient(listOf("質問1")))
        val repository = LpRepository(FakeClaudeClient(listOf(generationResponse, generationResponse)), interviewRepository)
        repository.generate(storeId, ownerSub, "店舗情報")

        val content = repository.generate(otherStoreId, "owner-2", "店舗情報2")

        assertEquals("こだわりの一杯を、あなたに。", content.catchphrase)
    }

    @Test
    fun `update overwrites an existing lp content`() = runBlocking {
        val interviewRepository = InterviewRepository(FakeClaudeClient(listOf("質問1")))
        val repository = LpRepository(FakeClaudeClient(listOf(generationResponse)), interviewRepository)
        repository.generate(storeId, ownerSub, "店舗情報")

        val updated = repository.update(
            storeId,
            UpdateLpContentRequest(catchphrase = "手直し後のコピー", pageHtml = "<p>手直し後の説明文</p>"),
        )

        assertEquals("手直し後のコピー", updated.catchphrase)
        assertEquals(updated, repository.get(storeId))
    }

    @Test
    fun `update of a store with no lp content throws`() {
        val repository = LpRepository(FakeClaudeClient(emptyList()), InterviewRepository(FakeClaudeClient(emptyList())))

        assertFailsWith<LpContentNotFoundException> {
            repository.update(storeId, UpdateLpContentRequest(catchphrase = "コピー", pageHtml = "<p>説明文</p>"))
        }
    }

    @Test
    fun `generate parses a response wrapped in a markdown code fence`() = runBlocking {
        val interviewRepository = InterviewRepository(FakeClaudeClient(listOf("質問1")))
        val fenced = "```json\n$generationResponse\n```"
        val repository = LpRepository(FakeClaudeClient(listOf(fenced)), interviewRepository)

        val content = repository.generate(storeId, ownerSub, "店舗情報")

        assertEquals("こだわりの一杯を、あなたに。", content.catchphrase)
    }
}
