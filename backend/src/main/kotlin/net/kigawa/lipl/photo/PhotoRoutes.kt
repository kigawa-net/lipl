package net.kigawa.lipl.photo

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import net.kigawa.lipl.auth.ownerSub
import net.kigawa.lipl.kaft.KaftClient
import net.kigawa.lipl.store.StoreRepository

@Serializable
data class KaftConfigResponse(val kaftBaseUrl: String)

fun Route.photoRoutes(
    storeRepository: StoreRepository,
    photoRepository: PhotoRepository,
    kaftClient: KaftClient,
    kaftBaseUrl: String,
) {
    // 写真表示URLの組み立てにフロントエンドが必要とする公開kaftベースURL。
    // 値自体は機密情報ではないため未認証で公開する。
    get("/api/kaft-config") {
        call.respond(KaftConfigResponse(kaftBaseUrl))
    }

    authenticate("keycloak") {
        route("/api/stores/{storeId}/photos") {
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                call.respond(photoRepository.listByStore(storeId))
            }

            post("/upload-token") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }

                val (uuid, token) = kaftClient.issueUploadToken()
                call.respond(UploadTokenResponse(uuid = uuid, uploadToken = token, kaftBaseUrl = kaftBaseUrl))
            }

            post("/confirm") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }

                val request = call.receive<ConfirmPhotoRequest>()
                try {
                    val photo = photoRepository.create(storeId, request.uuid, request.filename)
                    kaftClient.confirmAndPublish(request.uuid)
                    call.respond(HttpStatusCode.Created, photo)
                } catch (e: PhotoLimitExceededException) {
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

                val request = call.receive<ReorderPhotosRequest>()
                try {
                    photoRepository.reorder(storeId, request.orderedIds)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: PhotoNotFoundException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }

            delete("/{photoId}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val storeId = call.parameters["storeId"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val photoId = call.parameters["photoId"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)

                if (!storeRepository.isOwnedBy(storeId, principal.ownerSub)) {
                    return@delete call.respond(HttpStatusCode.NotFound)
                }

                try {
                    val kaftUuid = photoRepository.delete(storeId, photoId)
                    kaftClient.delete(kaftUuid)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: PhotoNotFoundException) {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
