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
}
