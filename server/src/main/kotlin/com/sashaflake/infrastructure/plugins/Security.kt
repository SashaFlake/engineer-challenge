package com.sashaflake.infrastructure.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respondText

fun Application.configureSecurity() {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .withClaimPresence("sub")
                    .build()
            )
            validate { credential ->
                when {
                    credential.payload.subject != null -> JWTPrincipal(credential.payload)
                    else -> null
                }
            }
            challenge { _, _ ->
                call.respondText(
                    text = """{"error":"Token is invalid or expired"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Unauthorized,
                )
            }
        }
    }
}
