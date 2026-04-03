package auth.command

import IdGenerator
import arrow.core.Either
import arrow.core.flatMap
import auth.model.user.Email
import auth.model.user.PlainPassword
import auth.model.user.User
import auth.model.user.UserId
import auth.port.PasswordHasher
import auth.port.UserRepository
import java.time.Clock

class RegisterUserHandler(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend fun handle(cmd: RegisterUserCommand): Either<RegisterUserError, UserId> =
        Either
            .catch { Email.create(cmd.email) }
            .mapLeft { RegisterUserError.InvalidEmail }
            .flatMap { email -> checkEmailExists(email) }
            .flatMap { email -> saveUser(email, cmd.password) }

    private suspend fun checkEmailExists(email: Email): Either<RegisterUserError, Email> {
        val userExists = users.existsByEmail(email)
        return when (userExists) {
            true -> Either.Left(RegisterUserError.UserAlreadyExists)
            false -> Either.Right(email)
        }
    }

    private suspend fun saveUser(
        email: Email,
        password: String
    ): Either<RegisterUserError, UserId> =
        Either
            .catch {
                val user =
                    User.register(
                        userId = idGenerator.generate(),
                        email = email,
                        plain = PlainPassword(password),
                        hasher = hasher,
                        createdAt = clock.instant(),
                    )
                users.save(user)
                user.id
            }.mapLeft { RegisterUserError.UserCreationFailed }
}

sealed class RegisterUserError {
    data object UserAlreadyExists : RegisterUserError()

    data object InvalidEmail : RegisterUserError()

    data object UserCreationFailed : RegisterUserError()
}
