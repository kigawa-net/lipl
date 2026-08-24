package net.kigawa.lipl.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.healthRoutes() {
    routing {
        get("/health") {
            call.respondText("ok", status = HttpStatusCode.OK)
        }
    }
}
