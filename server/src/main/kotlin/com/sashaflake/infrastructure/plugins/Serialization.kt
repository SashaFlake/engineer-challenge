package com.sashaflake.infrastructure.plugins

import io.ktor.server.application.*

// ContentNegotiation is intentionally not installed at application level.
// graphql-kotlin-ktor-server installs its own ContentNegotiation (Jackson) on /graphql route level.
// Installing it at application level causes DuplicatePluginException in Ktor 3.x.
fun Application.configureSerialization() = Unit
