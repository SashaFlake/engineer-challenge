package com.sashaflake.presentation.graphql

import arrow.core.Either
import auth.command.*
import com.expediagroup.graphql.server.operations.Mutation

class AuthMutation(
    private val registerHandler: RegisterUserHandler,
    private val resetRequestHandler: RequestPasswordResetHandler,
    private val resetHandler: ResetPasswordHandler,
    private val loginHandler: LoginUserHandler
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
        return true
    }

    suspend fun resetPassword(token: String, newPassword: String): Boolean {
        return resetHandler.handle(
            ResetPasswordCommand(token, newPassword)
        ).isRight()
    }
    suspend fun loginUser(email: String, password: String): LoginUserResult {
        return when (val result = loginHandler.handle(LoginUserCommand(email, password))) {
            is Either.Right -> LoginUserResult(success = true, token = result.value)
            is Either.Left -> when (result.value) {
                is LoginUserError.InvalidCredentials -> LoginUserResult(success = false, error = "Invalid credentials")
                is LoginUserError.AccountLocked      -> LoginUserResult(success = false, error = "Account temporarily locked")
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
