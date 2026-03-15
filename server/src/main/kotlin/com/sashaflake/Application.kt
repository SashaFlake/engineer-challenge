package com.sashaflake

import com.sashaflake.infrastructure.di.appModule
import com.sashaflake.infrastructure.plugins.configureGraphQL
import com.sashaflake.infrastructure.plugins.configureHTTP
import com.sashaflake.infrastructure.plugins.configureMonitoring
import com.sashaflake.infrastructure.plugins.configureSecurity
import com.sashaflake.infrastructure.plugins.configureSerialization
import com.sashaflake.presentation.configureRouting
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
    configureHTTP()
    configureSecurity()
    configureMonitoring()
    configureSerialization()
    configureGraphQL()
    configureRouting()
}
