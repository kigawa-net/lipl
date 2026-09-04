package net.kigawa.lipl.menu

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
import net.kigawa.lipl.store.StoreRepository

fun Route.menuItemRoutes(
    storeRepository: StoreRepository,
    menuItemRepository: MenuItemRepository,
) {
    authenticate("keycloak") {
        route("/api/stores/{storeId}/menu-items") {
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                call.respond(menuItemRepository.listByStore(storeId))
            }

            post {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<CreateMenuItemRequest>()
                try {
                    request.validate()
                } catch (e: MenuItemValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    return@post
                }

                try {
                    val menuItem = menuItemRepository.create(storeId, request)
                    call.respond(HttpStatusCode.Created, menuItem)
                } catch (e: MenuItemLimitExceededException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
                }
            }

            put("/{menuItemId}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)
                val menuItemId = call.parameters["menuItemId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@put call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<CreateMenuItemRequest>()
                try {
                    request.validate()
                } catch (e: MenuItemValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    return@put
                }

                try {
                    call.respond(menuItemRepository.update(storeId, menuItemId, request))
                } catch (e: MenuItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            put("/reorder") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@put call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<ReorderMenuItemsRequest>()
                try {
                    menuItemRepository.reorder(storeId, request.orderedIds)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: MenuItemNotFoundException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }

            delete("/{menuItemId}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val menuItemId = call.parameters["menuItemId"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@delete call.respond(HttpStatusCode.NotFound)
                }

                try {
                    menuItemRepository.delete(storeId, menuItemId)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: MenuItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            // 写真は独立アップロードではなく、店舗が既にアップロード済みのphotosから選択する。
            put("/{menuItemId}/photo") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)
                val menuItemId = call.parameters["menuItemId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@put call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<SetMenuItemPhotoRequest>()
                try {
                    val menuItem = menuItemRepository.setPhoto(storeId, menuItemId, request.photoId)
                    call.respond(menuItem)
                } catch (e: MenuItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                } catch (e: PhotoNotFoundException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }
        }
    }
}
