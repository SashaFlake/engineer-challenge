package com.sashaflake.presentation

import com.sashaflake.infrastructure.plugins.appMicrometerRegistry
import com.sashaflake.presentation.routes.metricsRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(AutoHeadResponse)
    routing {
        metricsRoutes(appMicrometerRegistry)
    }
}
