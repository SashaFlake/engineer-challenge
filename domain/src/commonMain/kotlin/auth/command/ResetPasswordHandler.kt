package auth.command

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import auth.model.user.PlainPassword
import auth.port.PasswordHasher
import auth.port.PasswordResetTokenRepository
import auth.port.UserRepository
import java.time.Clock

class ResetPasswordHandler(
    private val users: UserRepository,
    private val tokens: PasswordResetTokenRepository,
    private val hasher: PasswordHasher,
    private val clock: Clock,
) {
    suspend fun handle(cmd: ResetPasswordCommand): Either<ResetPasswordError, Unit> {
        val token =
            tokens.findByValue(cmd.token)
                ?: return ResetPasswordError.TokenNotFound.left()

        if (token.isExpired(clock.instant())) {
            return ResetPasswordError.TokenExpired.left()
        }

        val user =
            users.findById(token.userId)
                ?: return ResetPasswordError.UserNotFound.left()

        return Either
            .catch {
                user.changePassword(PlainPassword(cmd.newPassword), hasher)
            }.mapLeft { ResetPasswordError.WeakPassword }
            .flatMap {
                Either
                    .catch {
                        users.save(user)
                        tokens.deleteByUserId(user.id)
                    }.mapLeft { ResetPasswordError.SaveFailed }
            }
    }
}

sealed class ResetPasswordError {
    data object TokenNotFound : ResetPasswordError()

    data object TokenExpired : ResetPasswordError()

    data object UserNotFound : ResetPasswordError()

    data object WeakPassword : ResetPasswordError()

    data object SaveFailed : ResetPasswordError()
}
