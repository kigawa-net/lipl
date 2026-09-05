package net.kigawa.lipl.ai

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import net.kigawa.lipl.auth.ownerSub
import net.kigawa.lipl.menu.MenuItemRepository
import net.kigawa.lipl.photo.PhotoRepository
import net.kigawa.lipl.store.StoreRepository

fun Route.lpRoutes(
    storeRepository: StoreRepository,
    lpRepository: LpRepository,
    menuItemRepository: MenuItemRepository,
    photoRepository: PhotoRepository,
    kaftBaseUrl: String,
) {
    authenticate("keycloak") {
        route("/api/stores/{storeId}/lp") {
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }

                val content = lpRepository.get(storeId)
                if (content == null) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(content)
                }
            }

            put {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@put call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<UpdateLpContentRequest>()
                try {
                    request.validate()
                } catch (e: LpContentValidationException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    return@put
                }

                try {
                    call.respond(lpRepository.update(storeId, request))
                } catch (e: LpContentNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/generate") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }

                val store = storeRepository.get(storeId)
                val menuItems = menuItemRepository.listByStore(storeId)
                val photos = photoRepository.listByStore(storeId)
                val context = buildGenerationContext(store, menuItems, photos, kaftBaseUrl)
                try {
                    call.respond(lpRepository.generate(storeId, principal.ownerSub, context))
                } catch (e: LpGenerationLimitExceededException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
                }
            }
        }
    }
}
