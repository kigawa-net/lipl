package net.kigawa.lipl.store

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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

    @Test
    fun `new store is unpublished by default`() {
        val request = CreateStoreRequest(
            name = "店舗",
            businessCategory = BusinessCategory.CAFE,
            address = "住所",
        )

        val created = repository.create("owner-1", request)

        assertFalse(created.published)
        assertNull(repository.findPublishedBySlug(created.slug))
    }

    @Test
    fun `setPublished makes the store visible via findPublishedBySlug`() {
        val request = CreateStoreRequest(
            name = "店舗",
            businessCategory = BusinessCategory.CAFE,
            address = "住所",
        )
        val created = repository.create("owner-1", request)

        val updated = repository.setPublished(created.id, true)

        assertTrue(updated.published)
        assertEquals(created.slug, repository.findPublishedBySlug(created.slug)?.slug)
    }

    @Test
    fun `setPublished to false hides the store again`() {
        val request = CreateStoreRequest(
            name = "店舗",
            businessCategory = BusinessCategory.CAFE,
            address = "住所",
        )
        val created = repository.create("owner-1", request)
        repository.setPublished(created.id, true)

        repository.setPublished(created.id, false)

        assertNull(repository.findPublishedBySlug(created.slug))
    }

    @Test
    fun `setPublished on unknown store throws`() {
        assertFailsWith<StoreNotFoundException> {
            repository.setPublished(999L, true)
        }
    }

    @Test
    fun `update overwrites fields and replaces sns links`() {
        val created = repository.create(
            "owner-1",
            CreateStoreRequest(
                name = "店舗",
                businessCategory = BusinessCategory.CAFE,
                address = "住所",
                snsLinks = listOf(SnsLinkInput(SnsPlatform.INSTAGRAM, "https://instagram.com/old")),
            ),
        )

        val updated = repository.update(
            created.id,
            CreateStoreRequest(
                name = "新店舗名",
                businessCategory = BusinessCategory.RAMEN,
                address = "新住所",
                businessHours = "11:00-22:00",
                phone = "03-1234-5678",
                snsLinks = listOf(SnsLinkInput(SnsPlatform.X, "https://x.com/new")),
            ),
        )

        assertEquals("新店舗名", updated.name)
        assertEquals(BusinessCategory.RAMEN, updated.businessCategory)
        assertEquals("新住所", updated.address)
        assertEquals("11:00-22:00", updated.businessHours)
        assertEquals("03-1234-5678", updated.phone)
        assertEquals(listOf(SnsLinkInput(SnsPlatform.X, "https://x.com/new")), updated.snsLinks)
        assertEquals(created.slug, updated.slug)
    }

    @Test
    fun `update on unknown store throws`() {
        assertFailsWith<StoreNotFoundException> {
            repository.update(
                999L,
                CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
            )
        }
    }

    @Test
    fun `get returns the current state of the store`() {
        val created = repository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        assertEquals(created, repository.get(created.id))
    }

    @Test
    fun `delete removes the store and its sns links`() {
        val request = CreateStoreRequest(
            name = "店舗",
            businessCategory = BusinessCategory.CAFE,
            address = "住所",
            snsLinks = listOf(SnsLinkInput(SnsPlatform.INSTAGRAM, "https://instagram.com/test")),
        )
        val created = repository.create("owner-1", request)

        repository.delete(created.id)

        assertEquals(emptyList(), repository.listByOwner("owner-1"))
        val remainingLinks = transaction {
            StoreSnsLinksTable.selectAll().andWhere { StoreSnsLinksTable.storeId eq created.id }.count()
        }
        assertEquals(0, remainingLinks)
    }

    @Test
    fun `delete on unknown store throws`() {
        assertFailsWith<StoreNotFoundException> {
            repository.delete(999L)
        }
    }
}
