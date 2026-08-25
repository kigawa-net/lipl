package net.kigawa.lipl.store

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StoreRepositoryTest {

    private val repository = StoreRepository()

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:store_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(StoreSnsLinksTable, StoresTable)
        }
    }

    @Test
    fun `create fixed store persists with generated slug`() {
        val request = CreateStoreRequest(
            name = "テストカフェ",
            businessCategory = BusinessCategory.CAFE,
            address = "愛知県名古屋市中区1-1-1",
            snsLinks = listOf(SnsLinkInput(SnsPlatform.INSTAGRAM, "https://instagram.com/test")),
        )

        val created = repository.create("owner-1", request)

        assertEquals("テストカフェ", created.name)
        assertEquals(OperationType.FIXED, created.operationType)
        assertTrue(created.slug.isNotBlank())
        assertEquals(1, created.snsLinks.size)
    }

    @Test
    fun `create mobile store defaults operation type from kitchen car category`() {
        val request = CreateStoreRequest(
            name = "キッチンカーA",
            businessCategory = BusinessCategory.KITCHEN_CAR,
            businessArea = "名古屋市内中心",
        )

        val created = repository.create("owner-1", request)

        assertEquals(OperationType.MOBILE, created.operationType)
    }

    @Test
    fun `two stores get distinct slugs`() {
        val request = CreateStoreRequest(
            name = "店舗",
            businessCategory = BusinessCategory.CAFE,
            address = "住所",
        )

        val first = repository.create("owner-1", request)
        val second = repository.create("owner-1", request)

        assertNotEquals(first.slug, second.slug)
    }

    @Test
    fun `listByOwner only returns stores owned by that owner`() {
        val request = CreateStoreRequest(
            name = "店舗",
            businessCategory = BusinessCategory.CAFE,
            address = "住所",
        )
        repository.create("owner-1", request)
        repository.create("owner-2", request)

        val owner1Stores = repository.listByOwner("owner-1")

        assertEquals(1, owner1Stores.size)
    }
}
