package com.sashaflake.infrastructure.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

fun Application.configureHTTP() {
    val logger = log

    val allowedHosts =
        environment.config
            .propertyOrNull("cors.allowedHosts")
            ?.getString()
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    install(CORS) {
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)

        if (allowedHosts.isEmpty()) {
            logger.warn("CORS: cors.allowedHosts not configured — cross-origin requests rejected")
        } else {
            allowedHosts.forEach { origin ->
                val isHttps = origin.startsWith("https://")
                allowHost(
                    host = origin.removePrefix("https://").removePrefix("http://"),
                    schemes = if (isHttps) listOf("https") else listOf("http"),
                )
            }
        }
    }

    install(XForwardedHeaders)
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
    }
}
