package com.sashaflake.infrastructure.plugins

import auth.command.LoginUserHandler
import auth.command.RegisterUserHandler
import auth.command.RequestPasswordResetHandler
import auth.command.ResetPasswordHandler
import com.expediagroup.graphql.server.ktor.GraphQL
import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.ktor.graphQLSDLRoute
import com.expediagroup.graphql.server.ktor.graphiQLRoute
import com.sashaflake.presentation.graphql.AuthMutation
import com.sashaflake.presentation.graphql.HealthQuery
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureGraphQL() {
    val registerHandler by inject<RegisterUserHandler>()
    val resetRequestHandler by inject<RequestPasswordResetHandler>()
    val resetHandler by inject<ResetPasswordHandler>()
    val loginUserHandler by inject<LoginUserHandler>()

    install(GraphQL) {
        schema {
            packages = listOf("com.sashaflake.presentation.graphql")
            queries = listOf(HealthQuery())
            mutations = listOf(
                AuthMutation(registerHandler, resetRequestHandler, resetHandler, loginUserHandler)
            )
        }
    }

    routing {
        graphQLPostRoute()
        graphiQLRoute()
        graphQLSDLRoute()
    }
}
