package net.kigawa.lipl.auth

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URI
import java.util.concurrent.TimeUnit

data class KeycloakConfig(
    val issuer: String,
    val audience: String,
) {
    val jwksUri: String = "$issuer/protocol/openid-connect/certs"
}

fun keycloakConfigFromEnv(): KeycloakConfig {
    val issuer = System.getenv("KEYCLOAK_ISSUER")
        ?: error("環境変数 KEYCLOAK_ISSUER が設定されていません（例: https://user.kigawa.net/realms/lipl）")
    val audience = System.getenv("KEYCLOAK_AUDIENCE") ?: "account"
    return KeycloakConfig(issuer, audience)
}

fun Application.configureKeycloakAuth(config: KeycloakConfig) {
    val jwkProvider = JwkProviderBuilder(URI(config.jwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt("keycloak") {
            verifier(jwkProvider, config.issuer) {
                acceptLeeway(5)
            }
            validate { credential ->
                val subject = credential.payload.subject
                if (subject != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}

val JWTPrincipal.ownerSub: String
    get() = payload.subject ?: error("JWTにsubjectクレームがありません")
