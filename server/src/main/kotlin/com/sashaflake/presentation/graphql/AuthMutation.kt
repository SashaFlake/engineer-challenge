package com.sashaflake.presentation.graphql

import arrow.core.Either
import auth.command.*
import com.expediagroup.graphql.server.operations.Mutation

class AuthMutation(
    private val registerHandler: RegisterUserHandler,
    private val resetRequestHandler: RequestPasswordResetHandler,
    private val resetHandler: ResetPasswordHandler,
) : Mutation {

    suspend fun registerUser(email: String, password: String): RegisterUserResult {
        return when (val result = registerHandler.handle(
            RegisterUserCommand(email, password)
        )) {
            is Either.Right -> RegisterUserResult(success = true, userId = result.value.value.toString())
            is Either.Left  -> RegisterUserResult(success = false, error = result.value.toString())
        }
    }

    suspend fun requestPasswordReset(email: String): Boolean {
        resetRequestHandler.handle(RequestPasswordResetCommand(email))
        return true // всегда true — не раскрываем наличие email
    }

    suspend fun resetPassword(token: String, newPassword: String): Boolean {
        return resetHandler.handle(
            ResetPasswordCommand(token, newPassword)
        ).isRight()
    }
}

data class RegisterUserResult(
    val success: Boolean,
    val userId: String? = null,
    val error: String? = null,
)
