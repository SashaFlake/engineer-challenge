package com.sashaflake.infrastructure.graphql

import com.expediagroup.graphql.server.ktor.DefaultKtorGraphQLContextFactory
import graphql.GraphQLContext
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header

class KtorGraphQLContextFactory : DefaultKtorGraphQLContextFactory() {
    override suspend fun generateContext(request: ApplicationRequest): GraphQLContext =
        GraphQLContext.of(
            mapOf("clientIp" to resolveClientIp(request))
        )

    private fun resolveClientIp(request: ApplicationRequest): String =
        request.header("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?: request.header("X-Real-IP")
            ?: request.local.remoteHost
}
