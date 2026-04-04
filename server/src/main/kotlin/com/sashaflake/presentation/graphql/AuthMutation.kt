package com.sashaflake.presentation.graphql

import arrow.core.Either
import auth.command.LoginUserCommand
import auth.command.LoginUserError
import auth.command.LoginUserHandler
import auth.command.RegisterUserCommand
import auth.command.RegisterUserHandler
import auth.command.RequestPasswordResetCommand
import auth.command.RequestPasswordResetHandler
import auth.command.ResetPasswordCommand
import auth.command.ResetPasswordHandler
import com.expediagroup.graphql.server.operations.Mutation
import com.sashaflake.infrastructure.metrics.AuthMetrics
import graphql.schema.DataFetchingEnvironment

class AuthMutation(
    private val registerHandler: RegisterUserHandler,
    private val resetRequestHandler: RequestPasswordResetHandler,
    private val resetHandler: ResetPasswordHandler,
    private val loginHandler: LoginUserHandler,
    private val metrics: AuthMetrics,
) : Mutation {
    suspend fun registerUser(
        email: String,
        password: String
    ): RegisterUserResult =
        when (
            val result =
                registerHandler.handle(
                    RegisterUserCommand(email, password)
                )
        ) {
            is Either.Right -> {
                metrics.registerSuccess.increment()
                RegisterUserResult(success = true, userId = result.value.value.toString())
            }
            is Either.Left -> {
                metrics.registerError.increment()
                RegisterUserResult(success = false, error = result.value.toString())
            }
        }

    suspend fun requestPasswordReset(
        email: String,
        env: DataFetchingEnvironment,
    ): Boolean {
        resetRequestHandler.handle(RequestPasswordResetCommand(email))
            .also {
                metrics.passwordResetRequestTotal.increment()
                val ip = env.graphQlContext.get<String>("clientIp") ?: "unknown"
                metrics.recordPasswordResetRequest(ip)
            }
        return true
    }

    suspend fun resetPassword(
        token: String,
        newPassword: String
    ): Boolean =
        resetHandler
            .handle(
                ResetPasswordCommand(token, newPassword)
            ).isRight()
            .also { success ->
                when {
                    success -> metrics.passwordResetSuccess.increment()
                    else -> metrics.passwordResetError.increment()
                }
            }

    suspend fun loginUser(
        email: String,
        password: String,
        env: DataFetchingEnvironment,
    ): LoginUserResult {
        val ip = env.graphQlContext.get<String>("clientIp") ?: "unknown"
        return when (val result = loginHandler.handle(LoginUserCommand(email, password))) {
            is Either.Right -> LoginUserResult(success = true, token = result.value)
            is Either.Left ->
                when (result.value) {
                    is LoginUserError.InvalidCredentials -> {
                        metrics.loginInvalidCredentials.increment()
                        metrics.recordLoginFailure(ip, "invalid_credentials")
                        LoginUserResult(
                            success = false,
                            error = "Invalid credentials"
                        )
                    }

                    is LoginUserError.AccountLocked -> {
                        metrics.loginAccountLocked.increment()
                        metrics.recordLoginFailure(ip, "account_locked")
                        LoginUserResult(
                            success = false,
                            error = "Account temporarily locked"
                        )
                    }
                }
        }
    }
}

data class RegisterUserResult(
    val success: Boolean,
    val userId: String? = null,
    val error: String? = null,
)

data class LoginUserResult(
    val success: Boolean,
    val token: String? = null,
    val error: String? = null,
)
