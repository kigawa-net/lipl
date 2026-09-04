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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterviewRepositoryTest {

    private var storeId: Long = 0

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:interview_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable, InterviewMessagesTable)
        }
        storeId = StoreRepository().create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        ).id
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(InterviewMessagesTable, StoreSnsLinksTable, StoresTable)
        }
    }

    @Test
    fun `sendMessage with no prior messages asks the first question`() = runBlocking {
        val repository = InterviewRepository(FakeClaudeClient(listOf("お店のこだわりは？")))

        val state = repository.sendMessage(storeId, "店舗情報", null)

        assertEquals(1, state.messages.size)
        assertEquals("assistant", state.messages.single().role)
        assertEquals("お店のこだわりは？", state.messages.single().content)
        assertEquals(1, state.questionCount)
        assertFalse(state.limitReached)
    }

    @Test
    fun `sendMessage when already started returns current state without asking again`() = runBlocking {
        val client = FakeClaudeClient(listOf("質問1"))
        val repository = InterviewRepository(client)
        repository.sendMessage(storeId, "店舗情報", null)

        val state = repository.sendMessage(storeId, "店舗情報", null)

        assertEquals(1, state.messages.size)
        assertEquals(1, client.callCount)
    }

    @Test
    fun `answering before the limit asks the next question`() = runBlocking {
        val repository = InterviewRepository(FakeClaudeClient(listOf("質問1", "質問2")))
        repository.sendMessage(storeId, "店舗情報", null)

        val state = repository.sendMessage(storeId, "店舗情報", "回答1")

        assertEquals(listOf("質問1", "回答1", "質問2"), state.messages.map { it.content })
        assertEquals(2, state.questionCount)
        assertFalse(state.limitReached)
    }

    @Test
    fun `reaching the question limit stops asking further questions`() = runBlocking {
        val repository = InterviewRepository(FakeClaudeClient(listOf("質問1", "質問2", "質問3")))
        repository.sendMessage(storeId, "店舗情報", null)
        repository.sendMessage(storeId, "店舗情報", "回答1")
        repository.sendMessage(storeId, "店舗情報", "回答2")

        val state = repository.sendMessage(storeId, "店舗情報", "回答3")

        assertEquals(3, state.questionCount)
        assertTrue(state.limitReached)
        assertEquals("回答3", state.messages.last().content)
    }

    @Test
    fun `sending another answer after the limit throws`() = runBlocking {
        val repository = InterviewRepository(FakeClaudeClient(listOf("質問1", "質問2", "質問3")))
        repository.sendMessage(storeId, "店舗情報", null)
        repository.sendMessage(storeId, "店舗情報", "回答1")
        repository.sendMessage(storeId, "店舗情報", "回答2")
        repository.sendMessage(storeId, "店舗情報", "回答3")

        assertFailsWith<InterviewLimitExceededException> {
            repository.sendMessage(storeId, "店舗情報", "回答4")
        }
    }

    @Test
    fun `reset clears the conversation`() = runBlocking {
        val repository = InterviewRepository(FakeClaudeClient(listOf("質問1")))
        repository.sendMessage(storeId, "店舗情報", null)

        repository.reset(storeId)

        assertEquals(0, repository.getState(storeId).messages.size)
    }
}
