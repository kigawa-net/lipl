package net.kigawa.lipl.menu

import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.kaft.KaftClient
import net.kigawa.lipl.kaft.KaftConfig
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
// このテストはメニュー写真用のルートがauthenticate("keycloak")配下に正しく
// 登録され、未認証リクエストを拒否することのみを確認する。所有権チェック・
// 写真の設定/削除ロジックはMenuItemRepositoryTestで検証済み。
class MenuItemPhotoRouteTest {

    private val storeRepository = StoreRepository()
    private val menuItemRepository = MenuItemRepository()
    private val kaftConfig = KaftConfig(
        baseUrl = "http://kaft.internal:8080",
        publicBaseUrl = "https://kaft-stg.kigawa.net",
        internalJwtSecret = "test-secret",
    )
    private val keycloakConfig = KeycloakConfig(issuer = "https://example.invalid/realms/lipl", audience = "account")

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:menu_photo_route_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable, MenuItemsTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(MenuItemsTable, StoreSnsLinksTable, StoresTable)
        }
    }

    private fun testApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(
                storeRepository = storeRepository,
                menuItemRepository = menuItemRepository,
                keycloakConfig = keycloakConfig,
                kaftClient = KaftClient(kaftConfig),
                kaftConfig = kaftConfig,
            )
        }
        block()
    }

    @Test
    fun `upload-token without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )
        val item = menuItemRepository.create(store.id, CreateMenuItemRequest(name = "カレー"))

        val response = client.post("/api/stores/${store.id}/menu-items/${item.id}/photo/upload-token")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `photo confirm without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )
        val item = menuItemRepository.create(store.id, CreateMenuItemRequest(name = "カレー"))

        val response = client.post("/api/stores/${store.id}/menu-items/${item.id}/photo/confirm")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `photo delete without a token is rejected`() = testApp {
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )
        val item = menuItemRepository.create(store.id, CreateMenuItemRequest(name = "カレー"))

        val response = client.delete("/api/stores/${store.id}/menu-items/${item.id}/photo")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
