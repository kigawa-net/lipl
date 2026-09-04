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
import io.ktor.server.routing.route
import net.kigawa.lipl.auth.ownerSub
import net.kigawa.lipl.store.StoreRepository

fun Route.interviewRoutes(
    storeRepository: StoreRepository,
    interviewRepository: InterviewRepository,
) {
    authenticate("keycloak") {
        route("/api/stores/{storeId}/interview") {
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                call.respond(interviewRepository.getState(storeId))
            }

            post("/messages") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }

                val store = storeRepository.get(storeId)
                val request = call.receive<SendInterviewMessageRequest>()
                try {
                    call.respond(interviewRepository.sendMessage(storeId, storeContextText(store), request.message))
                } catch (e: InterviewLimitExceededException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
                }
            }
        }
    }
}
