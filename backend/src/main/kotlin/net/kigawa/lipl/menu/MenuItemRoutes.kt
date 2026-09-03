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
import net.kigawa.lipl.kaft.KaftClient
import net.kigawa.lipl.store.StoreRepository

fun Route.menuItemRoutes(
    storeRepository: StoreRepository,
    menuItemRepository: MenuItemRepository,
    kaftClient: KaftClient,
    kaftBaseUrl: String,
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
                    val kaftUuid = menuItemRepository.delete(storeId, menuItemId)
                    if (kaftUuid != null) {
                        kaftClient.delete(kaftUuid)
                    }
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: MenuItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/{menuItemId}/photo/upload-token") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }

                val (uuid, token) = kaftClient.issueUploadToken()
                call.respond(MenuItemUploadTokenResponse(uuid = uuid, uploadToken = token, kaftBaseUrl = kaftBaseUrl))
            }

            post("/{menuItemId}/photo/confirm") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                val menuItemId = call.parameters["menuItemId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<ConfirmMenuItemPhotoRequest>()
                try {
                    val (menuItem, previousKaftUuid) =
                        menuItemRepository.setPhoto(storeId, menuItemId, request.uuid, request.filename)
                    kaftClient.confirmAndPublish(request.uuid)
                    if (previousKaftUuid != null) {
                        kaftClient.delete(previousKaftUuid)
                    }
                    call.respond(menuItem)
                } catch (e: MenuItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete("/{menuItemId}/photo") {
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
                    val kaftUuid = menuItemRepository.clearPhoto(storeId, menuItemId)
                    if (kaftUuid != null) {
                        kaftClient.delete(kaftUuid)
                    }
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: MenuItemNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
