package auth.command

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import auth.model.user.Email
import auth.model.user.PlainPassword
import auth.model.user.User
import auth.model.user.UserId
import auth.port.PasswordHasher
import auth.port.TokenIssuer
import auth.port.UserRepository
import java.time.Clock
import java.time.Instant

class LoginUserHandler(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
    private val clock: Clock,
) {
    suspend fun handle(cmd: LoginUserCommand): Either<LoginUserError, String> {
        val now = clock.instant()

        return when (val step = resolveStep(cmd, now)) {
            is LoginStep.AccountLocked    -> LoginUserError.AccountLocked.left()
            is LoginStep.InvalidPassword  -> LoginUserError.InvalidCredentials.left()
            is LoginStep.UserNotFound     -> LoginUserError.InvalidCredentials.left()
            is LoginStep.InvalidEmail     -> LoginUserError.InvalidCredentials.left()
            is LoginStep.Success          -> Either.Right(tokenIssuer.issue(step.userId))
        }
    }

    private suspend fun resolveStep(
        cmd: LoginUserCommand,
        now: Instant
    ): LoginStep {
        val email =
            Either.catch { Email.create(cmd.email) }
                .getOrElse { return LoginStep.InvalidEmail }

        val user =
            users.findByEmail(email)
                ?: return LoginStep.UserNotFound

        when {
            !user.canLogin(now) -> return LoginStep.AccountLocked
            else -> {
                val passwordOk = user.verifyPassword(PlainPassword(cmd.password), hasher, now)
                updateGuard(user)

                return when {
                    passwordOk -> LoginStep.Success(user.id)
                    else -> LoginStep.InvalidPassword
                }
            }
        }
    }

    private suspend fun updateGuard(user: User) {
        users.save(user)
    }
}

private sealed class LoginStep {
    data object InvalidEmail : LoginStep()
    data object UserNotFound : LoginStep()
    data object AccountLocked : LoginStep()
    data object InvalidPassword : LoginStep()
    data class Success(
        val userId: UserId
    ) : LoginStep()
}

sealed class LoginUserError {
    data object InvalidCredentials : LoginUserError()

    data object AccountLocked : LoginUserError()
}
