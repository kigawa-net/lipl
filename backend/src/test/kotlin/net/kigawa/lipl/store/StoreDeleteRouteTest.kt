package net.kigawa.lipl.store

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.kaft.KaftClient
import net.kigawa.lipl.kaft.KaftConfig
import net.kigawa.lipl.menu.MenuItemRepository
import net.kigawa.lipl.menu.MenuItemsTable
import net.kigawa.lipl.module
import net.kigawa.lipl.photo.PhotoRepository
import net.kigawa.lipl.photo.PhotosTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// 実際のKeycloak検証はしない（testApplicationはJWKS未設定のため401になる）。
// このテストはstoreDeleteRoutesがauthenticate("keycloak")配下に正しく登録され、
// 未認証リクエストを拒否することのみを確認する。所有権チェック・カスケード削除の
// ロジックはStoreRepositoryTest / MenuItemRepositoryTest / PhotoRepositoryTestで検証済み。
class StoreDeleteRouteTest {

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
            "jdbc:h2:mem:store_delete_test_${System.identityHashCode(this)};DB_CLOSE_DELAY=-1",
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
    fun `delete without a token is rejected`() = testApplication {
        application {
            module(
                storeRepository = storeRepository,
                menuItemRepository = menuItemRepository,
                photoRepository = photoRepository,
                keycloakConfig = keycloakConfig,
                kaftClient = KaftClient(kaftConfig),
                kaftConfig = kaftConfig,
            )
        }
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )

        val response = client.delete("/api/stores/${store.id}")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `delete with a token for a different issuer is rejected`() = testApplication {
        application {
            module(
                storeRepository = storeRepository,
                menuItemRepository = menuItemRepository,
                photoRepository = photoRepository,
                keycloakConfig = keycloakConfig,
                kaftClient = KaftClient(kaftConfig),
                kaftConfig = kaftConfig,
            )
        }
        val store = storeRepository.create(
            "owner-1",
            CreateStoreRequest(name = "店舗", businessCategory = BusinessCategory.CAFE, address = "住所"),
        )
        val forgedToken = JWT.create()
            .withSubject("owner-1")
            .withIssuer("https://not-the-real-issuer.invalid")
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.HMAC256("irrelevant"))

        val response = client.delete("/api/stores/${store.id}") {
            header("Authorization", "Bearer $forgedToken")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        // 店舗は削除されていないこと
        assertEquals(1, storeRepository.listByOwner("owner-1").size)
    }
}
