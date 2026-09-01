package net.kigawa.lipl.photo

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

class PhotoRepositoryTest {

    private val storeRepository = StoreRepository()
    private val photoRepository = PhotoRepository()
    private var storeId: Long = 0

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:photo_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable, PhotosTable)
        }
        storeId = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        ).id
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(PhotosTable, StoreSnsLinksTable, StoresTable)
        }
    }

    @Test
    fun `create assigns incrementing display order`() {
        val first = photoRepository.create(storeId, "uuid-1", "a.jpg")
        val second = photoRepository.create(storeId, "uuid-2", "b.jpg")

        assertEquals(0, first.displayOrder)
        assertEquals(1, second.displayOrder)
    }

    @Test
    fun `create beyond free plan limit is rejected`() {
        repeat(FREE_PLAN_PHOTO_LIMIT) {
            photoRepository.create(storeId, "uuid-$it", "$it.jpg")
        }

        assertFailsWith<PhotoLimitExceededException> {
            photoRepository.create(storeId, "uuid-over", "over.jpg")
        }
    }

    @Test
    fun `delete returns the kaft uuid and removes the record`() {
        val photo = photoRepository.create(storeId, "uuid-1", "a.jpg")

        val kaftUuid = photoRepository.delete(storeId, photo.id)

        assertEquals("uuid-1", kaftUuid)
        assertEquals(emptyList(), photoRepository.listByStore(storeId))
    }

    @Test
    fun `reorder updates displayOrder to match given sequence`() {
        val a = photoRepository.create(storeId, "uuid-a", "a.jpg")
        val b = photoRepository.create(storeId, "uuid-b", "b.jpg")

        photoRepository.reorder(storeId, listOf(b.id, a.id))

        val photos = photoRepository.listByStore(storeId)
        assertEquals(listOf("uuid-b", "uuid-a"), photos.map { it.kaftUuid })
    }
}
