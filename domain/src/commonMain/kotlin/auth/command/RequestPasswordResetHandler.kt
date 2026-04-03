package auth.command

import EmailSender
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import auth.model.passwordreset.PasswordResetToken
import auth.model.user.Email
import auth.port.PasswordResetTokenGenerator
import auth.port.PasswordResetTokenRepository
import auth.port.UserRepository
import java.time.Clock

class RequestPasswordResetHandler(
    private val users: UserRepository,
    private val tokens: PasswordResetTokenRepository,
    private val emailSender: EmailSender,
    private val tokenGenerator: PasswordResetTokenGenerator,
    private val clock: Clock,
) {
    suspend fun handle(cmd: RequestPasswordResetCommand): Either<RequestPasswordResetError, Unit> =
        Either
            .catch { Email.create(cmd.email) }
            .mapLeft { RequestPasswordResetError.InvalidEmail }
            .flatMap { email ->
                val user =
                    users.findByEmail(email)
                        ?: return@flatMap Unit.right()

                val token =
                    PasswordResetToken.create(
                        value = tokenGenerator.generate(),
                        userId = user.id,
                        now = clock.instant(),
                    )
                Either
                    .catch {
                        tokens.deleteByUserId(user.id)
                        tokens.save(token)
                        emailSender.sendPasswordResetEmail(email, token.value)
                    }.mapLeft { return@flatMap RequestPasswordResetError.RequestPasswordResetFailed.left() }
                Unit.right()
            }
}

sealed class RequestPasswordResetError {
    data object InvalidEmail : RequestPasswordResetError()

    data object RequestPasswordResetFailed : RequestPasswordResetError()
}
