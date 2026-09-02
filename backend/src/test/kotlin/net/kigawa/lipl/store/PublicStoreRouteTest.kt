package net.kigawa.lipl.store

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.kaft.KaftConfig
import net.kigawa.lipl.menu.MenuItemRepository
import net.kigawa.lipl.menu.MenuItemsTable
import net.kigawa.lipl.module
import net.kigawa.lipl.photo.PhotoRepository
import net.kigawa.lipl.photo.PhotosTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicStoreRouteTest {

    private val storeRepository = StoreRepository()
    private val menuItemRepository = MenuItemRepository()
    private val photoRepository = PhotoRepository()
    private val kaftConfig = KaftConfig(
        baseUrl = "http://kaft.internal:8080",
        publicBaseUrl = "https://kaft-stg.kigawa.net",
        internalJwtSecret = "test-secret",
    )
    private val keycloakConfig = KeycloakConfig(issuer = "https://example.invalid/realms/lipl", audience = "account")

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:public_store_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(StoresTable, StoreSnsLinksTable, MenuItemsTable, PhotosTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(PhotosTable, MenuItemsTable, StoreSnsLinksTable, StoresTable)
        }
    }

    @Test
    fun `unpublished store returns 404`() = testApplication {
        application {
            module(storeRepository = storeRepository, menuItemRepository = menuItemRepository, photoRepository = photoRepository, keycloakConfig = keycloakConfig, kaftConfig = kaftConfig)
        }
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        val response = client.get("/api/public/stores/${store.slug}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `published store returns its public info`() = testApplication {
        application {
            module(storeRepository = storeRepository, menuItemRepository = menuItemRepository, photoRepository = photoRepository, keycloakConfig = keycloakConfig, kaftConfig = kaftConfig)
        }
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "公開カフェ", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )
        storeRepository.setPublished(store.id, true)

        val response = client.get("/api/public/stores/${store.slug}")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("公開カフェ"))
    }

    @Test
    fun `unknown slug returns 404`() = testApplication {
        application {
            module(storeRepository = storeRepository, menuItemRepository = menuItemRepository, photoRepository = photoRepository, keycloakConfig = keycloakConfig, kaftConfig = kaftConfig)
        }

        val response = client.get("/api/public/stores/not-a-real-slug")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
