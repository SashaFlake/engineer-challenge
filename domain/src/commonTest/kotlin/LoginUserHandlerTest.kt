import auth.command.LoginUserCommand
import auth.command.LoginUserError
import auth.command.LoginUserHandler
import auth.model.user.Email
import auth.model.user.HashedPassword
import auth.model.user.LoginAttemptGuard
import auth.model.user.PlainPassword
import auth.model.user.User
import auth.model.user.UserId
import auth.port.PasswordHasher
import auth.port.TokenIssuer
import auth.port.UserRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class LoginUserHandlerTest :
    ShouldSpec({

        val users = mockk<UserRepository>()
        val hasher = mockk<PasswordHasher>()
        val tokenIssuer = mockk<TokenIssuer>()
        val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"))

        val handler =
            LoginUserHandler(
                users = users,
                hasher = hasher,
                tokenIssuer = tokenIssuer,
                clock = clock,
            )

        val fixedId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val fixedToken = "jwt.token.here"
        val email = "user@example.com"
        val password = "Password1"

        fun makeUser(guard: LoginAttemptGuard = LoginAttemptGuard.DEFAULT) =
            User(
                id = fixedId,
                email = Email.create(email),
                hashedPassword = HashedPassword.create(PlainPassword(password), hasher),
                loginAttemptGuard = guard,
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            )

        beforeEach {
            clearMocks(users, hasher, tokenIssuer)
            every { hasher.hash(any()) } returns "hashed"
            every { hasher.verify(password, "hashed") } returns true
            every { hasher.verify(neq(password), any()) } returns false
            every { tokenIssuer.issue(fixedId) } returns fixedToken
            coEvery { users.save(any()) } just Runs
        }

        should("return token when credentials are correct") {
            coEvery { users.findByEmail(Email.create(email)) } returns makeUser()

            val result = handler.handle(LoginUserCommand(email, password))

            result.shouldBeRight(fixedToken)
            coVerify(exactly = 1) { users.save(any()) }
        }

        should("return InvalidCredentials when password is wrong") {
            coEvery { users.findByEmail(Email.create(email)) } returns makeUser()

            val result = handler.handle(LoginUserCommand(email, "WrongPassword"))

            result.shouldBeLeft(LoginUserError.InvalidCredentials)
            coVerify(exactly = 1) { users.save(any()) }
        }

        should("return InvalidCredentials when user does not exist") {
            coEvery { users.findByEmail(any()) } returns null

            val result = handler.handle(LoginUserCommand(email, password))

            result.shouldBeLeft(LoginUserError.InvalidCredentials)
            coVerify(exactly = 0) { users.save(any()) }
        }

        should("return InvalidCredentials when email is malformed") {
            val result = handler.handle(LoginUserCommand("not-an-email", password))

            result.shouldBeLeft(LoginUserError.InvalidCredentials)
            coVerify(exactly = 0) { users.findByEmail(any()) }
            coVerify(exactly = 0) { users.save(any()) }
        }

        should("return AccountLocked when account is locked") {
            val lockedGuard =
                LoginAttemptGuard(
                    failedAttempts = LoginAttemptGuard.MAX_ATTEMPTS,
                    lockedUntil = Instant.parse("2026-01-01T00:15:00Z"),
                )
            coEvery { users.findByEmail(Email.create(email)) } returns makeUser(lockedGuard)

            val result = handler.handle(LoginUserCommand(email, password))

            result.shouldBeLeft(LoginUserError.AccountLocked)
            coVerify(exactly = 0) { users.save(any()) }
        }

        should("persist incremented failure counter after wrong password") {
            coEvery { users.findByEmail(Email.create(email)) } returns makeUser()

            handler.handle(LoginUserCommand(email, "WrongPassword"))

            coVerify(exactly = 1) { users.save(match { it.getLoginAttemptGuard().failedAttempts == 1 }) }
        }

        should("persist reset guard after successful login") {
            val guardWithFailures = LoginAttemptGuard(failedAttempts = 3)
            coEvery { users.findByEmail(Email.create(email)) } returns makeUser(guardWithFailures)

            handler.handle(LoginUserCommand(email, password))

            coVerify(exactly = 1) { users.save(match { it.getLoginAttemptGuard().failedAttempts == 0 }) }
        }
    })
