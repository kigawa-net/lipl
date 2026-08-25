package net.kigawa.lipl.store

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import net.kigawa.lipl.auth.ownerSub

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
        }
    }
}
