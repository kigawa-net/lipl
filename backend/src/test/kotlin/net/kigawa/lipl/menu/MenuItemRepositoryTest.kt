package net.kigawa.lipl.menu

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

class MenuItemRepositoryTest {

    private val storeRepository = StoreRepository()
    private val menuItemRepository = MenuItemRepository()
    private var storeId: Long = 0

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:menu_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable, MenuItemsTable)
        }
        storeId = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        ).id
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(MenuItemsTable, StoreSnsLinksTable, StoresTable)
        }
    }

    @Test
    fun `create assigns incrementing display order`() {
        val first = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "カレー"))
        val second = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "ラーメン"))

        assertEquals(0, first.displayOrder)
        assertEquals(1, second.displayOrder)
    }

    @Test
    fun `create beyond free plan limit is rejected`() {
        repeat(FREE_PLAN_MENU_ITEM_LIMIT) {
            menuItemRepository.create(storeId, CreateMenuItemRequest(name = "品目$it"))
        }

        assertFailsWith<MenuItemLimitExceededException> {
            menuItemRepository.create(storeId, CreateMenuItemRequest(name = "超過品目"))
        }
    }

    @Test
    fun `listByStore returns items ordered by displayOrder`() {
        menuItemRepository.create(storeId, CreateMenuItemRequest(name = "A"))
        menuItemRepository.create(storeId, CreateMenuItemRequest(name = "B"))

        val items = menuItemRepository.listByStore(storeId)

        assertEquals(listOf("A", "B"), items.map { it.name })
    }

    @Test
    fun `reorder updates displayOrder to match given sequence`() {
        val a = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "A"))
        val b = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "B"))

        menuItemRepository.reorder(storeId, listOf(b.id, a.id))

        val items = menuItemRepository.listByStore(storeId)
        assertEquals(listOf("B", "A"), items.map { it.name })
    }

    @Test
    fun `delete removes the item`() {
        val item = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "カレー"))

        menuItemRepository.delete(storeId, item.id)

        assertEquals(emptyList(), menuItemRepository.listByStore(storeId))
    }

    @Test
    fun `delete of nonexistent item throws`() {
        assertFailsWith<MenuItemNotFoundException> {
            menuItemRepository.delete(storeId, 9999)
        }
    }

    @Test
    fun `delete returns the photo kaftUuid when set, or null when not set`() {
        val withPhoto = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "カレー"))
        menuItemRepository.setPhoto(storeId, withPhoto.id, "uuid-1", "a.jpg")
        val withoutPhoto = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "ラーメン"))

        assertEquals("uuid-1", menuItemRepository.delete(storeId, withPhoto.id))
        assertEquals(null, menuItemRepository.delete(storeId, withoutPhoto.id))
    }

    @Test
    fun `setPhoto sets photo fields and returns null when no previous photo`() {
        val item = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "カレー"))

        val (updated, previous) = menuItemRepository.setPhoto(storeId, item.id, "uuid-1", "a.jpg")

        assertEquals("uuid-1", updated.photoKaftUuid)
        assertEquals("a.jpg", updated.photoFilename)
        assertEquals(null, previous)
    }

    @Test
    fun `setPhoto returns the previous kaftUuid when replacing an existing photo`() {
        val item = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "カレー"))
        menuItemRepository.setPhoto(storeId, item.id, "uuid-1", "a.jpg")

        val (updated, previous) = menuItemRepository.setPhoto(storeId, item.id, "uuid-2", "b.jpg")

        assertEquals("uuid-2", updated.photoKaftUuid)
        assertEquals("uuid-1", previous)
    }

    @Test
    fun `clearPhoto removes photo fields and returns the previous kaftUuid`() {
        val item = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "カレー"))
        menuItemRepository.setPhoto(storeId, item.id, "uuid-1", "a.jpg")

        val previous = menuItemRepository.clearPhoto(storeId, item.id)

        assertEquals("uuid-1", previous)
        val items = menuItemRepository.listByStore(storeId)
        assertEquals(null, items.single().photoKaftUuid)
        assertEquals(null, items.single().photoFilename)
    }

    @Test
    fun `clearPhoto returns null when no photo was set`() {
        val item = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "カレー"))

        assertEquals(null, menuItemRepository.clearPhoto(storeId, item.id))
    }

    @Test
    fun `deleteByStore returns kaftUuids of items that had a photo`() {
        val a = menuItemRepository.create(storeId, CreateMenuItemRequest(name = "A"))
        menuItemRepository.create(storeId, CreateMenuItemRequest(name = "B"))
        menuItemRepository.setPhoto(storeId, a.id, "uuid-1", "a.jpg")

        val kaftUuids = menuItemRepository.deleteByStore(storeId)

        assertEquals(listOf("uuid-1"), kaftUuids)
        assertEquals(emptyList(), menuItemRepository.listByStore(storeId))
    }
}
