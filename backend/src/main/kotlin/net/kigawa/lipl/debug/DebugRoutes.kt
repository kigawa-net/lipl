package net.kigawa.lipl.debug

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import net.kigawa.lipl.ai.FREE_PLAN_LIFETIME_GENERATION_LIMIT
import net.kigawa.lipl.ai.LpRepository
import net.kigawa.lipl.auth.ownerSub

// stg等の検証環境限定のデバッグ機能。DEBUG_MENU_ENABLEDが未設定/falseの本番環境では
// ai-usageエンドポイント自体が登録されず404になる（フロントエンドの表示制御だけに頼らない）。
fun Route.debugRoutes(config: DebugConfig, lpRepository: LpRepository) {
    get("/api/debug/config") {
        call.respond(DebugConfigResponse(config.debugMenuEnabled))
    }

    if (!config.debugMenuEnabled) return

    authenticate("keycloak") {
        get("/api/debug/ai-usage") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(
                AiUsageResponse(
                    generationCount = lpRepository.getUsage(principal.ownerSub),
                    limit = FREE_PLAN_LIFETIME_GENERATION_LIMIT,
                ),
            )
        }

        put("/api/debug/ai-usage") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<SetAiUsageRequest>()
            lpRepository.setUsage(principal.ownerSub, request.generationCount)
            call.respond(
                AiUsageResponse(
                    generationCount = lpRepository.getUsage(principal.ownerSub),
                    limit = FREE_PLAN_LIFETIME_GENERATION_LIMIT,
                ),
            )
        }
    }
}
