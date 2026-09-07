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
import net.kigawa.lipl.ai.AnthropicClaudeClient
import net.kigawa.lipl.ai.ClaudeClient
import net.kigawa.lipl.ai.InterviewRepository
import net.kigawa.lipl.ai.LpRepository
import net.kigawa.lipl.ai.claudeConfigFromEnv
import net.kigawa.lipl.ai.interviewRoutes
import net.kigawa.lipl.ai.lpRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json as clientJson
import net.kigawa.lipl.auth.KeycloakConfig
import net.kigawa.lipl.auth.SessionAuth
import net.kigawa.lipl.auth.SessionCookieCodec
import net.kigawa.lipl.auth.authRoutes
import net.kigawa.lipl.auth.buildJwkProvider
import net.kigawa.lipl.auth.configureKeycloakAuth
import net.kigawa.lipl.auth.keycloakConfigFromEnv
import net.kigawa.lipl.auth.sessionEncryptionKeyFromEnv
import kotlinx.serialization.json.Json
import net.kigawa.lipl.db.connectDatabase
import net.kigawa.lipl.db.createDataSource
import net.kigawa.lipl.db.dbConfigFromEnv
import net.kigawa.lipl.db.migrate
import net.kigawa.lipl.debug.DebugConfig
import net.kigawa.lipl.debug.debugConfigFromEnv
import net.kigawa.lipl.debug.debugRoutes
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
    val sessionEncryptionKey = sessionEncryptionKeyFromEnv()
    val kaftConfig = kaftConfigFromEnv()
    val kaftClient = KaftClient(kaftConfig)
    val claudeClient: ClaudeClient = AnthropicClaudeClient(claudeConfigFromEnv())
    val interviewRepository = InterviewRepository(claudeClient)
    val lpRepository = LpRepository(claudeClient, interviewRepository)
    val debugConfig = debugConfigFromEnv()

    embeddedServer(Netty, port = port) {
        module(
            storeRepository = storeRepository,
            menuItemRepository = menuItemRepository,
            photoRepository = photoRepository,
            keycloakConfig = keycloakConfig,
            sessionEncryptionKey = sessionEncryptionKey,
            kaftClient = kaftClient,
            kaftConfig = kaftConfig,
            interviewRepository = interviewRepository,
            lpRepository = lpRepository,
            debugConfig = debugConfig,
        )
    }.start(wait = true)
}

private val logger = LoggerFactory.getLogger("net.kigawa.lipl.Application")

fun Application.module(
    storeRepository: StoreRepository? = null,
    menuItemRepository: MenuItemRepository? = null,
    photoRepository: PhotoRepository? = null,
    keycloakConfig: KeycloakConfig? = null,
    sessionEncryptionKey: ByteArray? = null,
    kaftClient: KaftClient? = null,
    kaftConfig: KaftConfig? = null,
    interviewRepository: InterviewRepository? = null,
    lpRepository: LpRepository? = null,
    debugConfig: DebugConfig = DebugConfig(debugMenuEnabled = false),
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
        val jwkProvider = buildJwkProvider(keycloakConfig)
        val authHttpClient = HttpClient(CIO) {
            install(ClientContentNegotiation) {
                clientJson(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            // 明示的なタイムアウトがないと、Keycloakへの疎通が一時的に詰まった際に
            // ブラウザ側が20秒近く待たされた末に生の500になる。短めに切って早く失敗させる。
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
            }
        }
        // 本番はmain()がSESSION_ENCRYPTION_KEYを起動時に必須チェックして渡す。
        // テストなどsessionEncryptionKeyを渡さないmodule()呼び出しでのみダミー鍵を使う。
        val sessionKey = sessionEncryptionKey ?: ByteArray(32)
        val sessionAuth = SessionAuth(keycloakConfig, jwkProvider, authHttpClient, SessionCookieCodec(sessionKey))
        configureKeycloakAuth(sessionAuth)
        routing { authRoutes(keycloakConfig, sessionAuth, authHttpClient) }
    }
    if (storeRepository != null) {
        routing { storeRoutes(storeRepository) }
    }
    if (storeRepository != null && menuItemRepository != null) {
        routing { menuItemRoutes(storeRepository, menuItemRepository) }
    }
    if (storeRepository != null && photoRepository != null && kaftClient != null && kaftConfig != null) {
        routing { photoRoutes(storeRepository, photoRepository, kaftClient, kaftConfig.publicBaseUrl) }
    }
    if (storeRepository != null && menuItemRepository != null && photoRepository != null && kaftClient != null) {
        routing { storeDeleteRoutes(storeRepository, menuItemRepository, photoRepository, kaftClient) }
    }
    if (storeRepository != null && menuItemRepository != null && photoRepository != null && kaftConfig != null) {
        routing {
            publicStoreRoutes(storeRepository, menuItemRepository, photoRepository, lpRepository, kaftConfig.publicBaseUrl)
        }
    }
    if (storeRepository != null && interviewRepository != null) {
        routing { interviewRoutes(storeRepository, interviewRepository) }
    }
    if (storeRepository != null && lpRepository != null && menuItemRepository != null &&
        photoRepository != null && kaftConfig != null
    ) {
        routing {
            lpRoutes(storeRepository, lpRepository, menuItemRepository, photoRepository, kaftConfig.publicBaseUrl)
        }
    }
    if (lpRepository != null) {
        routing { debugRoutes(debugConfig, lpRepository) }
    }
}
