package net.kigawa.lipl.store

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import net.kigawa.lipl.auth.ownerSub
import net.kigawa.lipl.kaft.KaftClient
import net.kigawa.lipl.menu.MenuItemRepository
import net.kigawa.lipl.photo.PhotoRepository
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("net.kigawa.lipl.store.StoreRoutes")

fun Route.storeRoutes(repository: StoreRepository) {
    authenticate("keycloak") {
        route("/api/stores") {
            post {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<CreateStoreRequest>()

                try {
                    request.validate()
                } catch (e: StoreValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    return@post
                }

                val store = repository.create(principal.ownerSub, request)
                call.respond(HttpStatusCode.Created, store)
            }

            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(repository.listByOwner(principal.ownerSub))
            }

            get("/{storeId}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                if (!repository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                call.respond(repository.get(storeId))
            }

            put("/{storeId}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                if (!repository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@put call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<CreateStoreRequest>()
                try {
                    request.validate()
                } catch (e: StoreValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    return@put
                }

                call.respond(repository.update(storeId, request))
            }

            put("/{storeId}/publish") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                if (!repository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@put call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<UpdatePublishedRequest>()
                val store = repository.setPublished(storeId, request.published)
                call.respond(store)
            }
        }
    }
}

// 店舗削除。関連する写真はkaftからも削除する（1件失敗しても他の削除・店舗削除自体は続行する）。
fun Route.storeDeleteRoutes(
    storeRepository: StoreRepository,
    menuItemRepository: MenuItemRepository,
    photoRepository: PhotoRepository,
    kaftClient: KaftClient,
) {
    authenticate("keycloak") {
        delete("/api/stores/{storeId}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            val storeId = call.parameters["storeId"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

            if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                return@delete call.respond(HttpStatusCode.NotFound)
            }

            val photoKaftUuids = photoRepository.deleteByStore(storeId)
            menuItemRepository.deleteByStore(storeId)
            photoKaftUuids.forEach { uuid ->
                try {
                    kaftClient.delete(uuid)
                } catch (e: Exception) {
                    logger.warn("kaftの写真削除に失敗しました（uuid=$uuid）", e)
                }
            }
            storeRepository.delete(storeId)

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// 公開ページ（/p/{slug}）向けの未認証エンドポイント。非公開店舗は404を返す。
fun Route.publicStoreRoutes(
    storeRepository: StoreRepository,
    menuItemRepository: MenuItemRepository,
    photoRepository: PhotoRepository,
    kaftBaseUrl: String,
) {
    get("/api/public/stores/{slug}") {
        val slug = call.parameters["slug"]
            ?: return@get call.respond(HttpStatusCode.BadRequest)

        val store = storeRepository.findPublishedBySlug(slug)
            ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respond(
            PublicStoreResponse(
                name = store.name,
                businessCategory = store.businessCategory,
                operationType = store.operationType,
                address = store.address,
                businessArea = store.businessArea,
                businessHours = store.businessHours,
                phone = store.phone,
                snsLinks = store.snsLinks,
                menuItems = menuItemRepository.listByStore(store.id),
                photos = photoRepository.listByStore(store.id),
                kaftBaseUrl = kaftBaseUrl,
            ),
        )
    }
}
