package net.kigawa.lipl

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.auth.configureKeycloakAuth
import net.kigawa.lipl.auth.keycloakConfigFromEnv
import net.kigawa.lipl.db.connectDatabase
import net.kigawa.lipl.db.createDataSource
import net.kigawa.lipl.db.dbConfigFromEnv
import net.kigawa.lipl.db.migrate
import net.kigawa.lipl.health.healthRoutes
import net.kigawa.lipl.kaft.KaftClient
import net.kigawa.lipl.kaft.KaftConfig
import net.kigawa.lipl.kaft.kaftConfigFromEnv
import net.kigawa.lipl.menu.MenuItemRepository
import net.kigawa.lipl.menu.menuItemRoutes
import net.kigawa.lipl.photo.PhotoRepository
import net.kigawa.lipl.photo.photoRoutes
import net.kigawa.lipl.store.StoreRepository
import net.kigawa.lipl.store.publicStoreRoutes
import net.kigawa.lipl.store.storeDeleteRoutes
import net.kigawa.lipl.store.storeRoutes
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val dataSource = createDataSource(dbConfigFromEnv())
    migrate(dataSource)
    connectDatabase(dataSource)
    val storeRepository = StoreRepository()
    val menuItemRepository = MenuItemRepository()
    val photoRepository = PhotoRepository()
    val keycloakConfig = keycloakConfigFromEnv()
    val kaftConfig = kaftConfigFromEnv()
    val kaftClient = KaftClient(kaftConfig)

    embeddedServer(Netty, port = port) {
        module(
            storeRepository = storeRepository,
            menuItemRepository = menuItemRepository,
            photoRepository = photoRepository,
            keycloakConfig = keycloakConfig,
            kaftClient = kaftClient,
            kaftConfig = kaftConfig,
        )
    }.start(wait = true)
}

private val logger = LoggerFactory.getLogger("net.kigawa.lipl.Application")

fun Application.module(
    storeRepository: StoreRepository? = null,
    menuItemRepository: MenuItemRepository? = null,
    photoRepository: PhotoRepository? = null,
    keycloakConfig: KeycloakConfig? = null,
    kaftClient: KaftClient? = null,
    kaftConfig: KaftConfig? = null,
) {
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respondText(status = HttpStatusCode.InternalServerError) {
                "internal server error"
            }
        }
    }

    healthRoutes()

    if (keycloakConfig != null) {
        configureKeycloakAuth(keycloakConfig)
    }
    if (storeRepository != null) {
        routing { storeRoutes(storeRepository) }
    }
    if (storeRepository != null && menuItemRepository != null && kaftClient != null && kaftConfig != null) {
        routing { menuItemRoutes(storeRepository, menuItemRepository, kaftClient, kaftConfig.publicBaseUrl) }
    }
    if (storeRepository != null && photoRepository != null && kaftClient != null && kaftConfig != null) {
        routing { photoRoutes(storeRepository, photoRepository, kaftClient, kaftConfig.publicBaseUrl) }
    }
    if (storeRepository != null && menuItemRepository != null && photoRepository != null && kaftClient != null) {
        routing { storeDeleteRoutes(storeRepository, menuItemRepository, photoRepository, kaftClient) }
    }
    if (storeRepository != null && menuItemRepository != null && photoRepository != null && kaftConfig != null) {
        routing {
            publicStoreRoutes(storeRepository, menuItemRepository, photoRepository, kaftConfig.publicBaseUrl)
        }
    }
}
