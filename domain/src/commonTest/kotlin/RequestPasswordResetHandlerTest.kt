import auth.command.RequestPasswordResetCommand
import auth.command.RequestPasswordResetError
import auth.command.RequestPasswordResetHandler
import auth.model.user.Email
import auth.model.user.PlainPassword
import auth.model.user.User
import auth.model.user.UserId
import auth.port.PasswordResetTokenGenerator
import auth.port.PasswordResetTokenRepository
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

class RequestPasswordResetHandlerTest :
    ShouldSpec({
        val validEmail = "user@example.com"
        val fixedUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val fixedToken = "reset-token-abc123"

        val users = mockk<UserRepository>()
        val tokens = mockk<PasswordResetTokenRepository>()
        val emailSender = mockk<EmailSender>()
        val tokenGenerator = mockk<PasswordResetTokenGenerator>()
        val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"))

        val handler =
            RequestPasswordResetHandler(
                users = users,
                tokens = tokens,
                emailSender = emailSender,
                tokenGenerator = tokenGenerator,
                clock = clock,
            )

        beforeEach {
            clearMocks(users, tokens, emailSender, tokenGenerator)
            every { tokenGenerator.generate() } returns fixedToken
        }

        should("should return Unit and send email when user exists") {
            val email = Email.create(validEmail)
            val user =
                User.register(
                    userId = fixedUserId,
                    email = email,
                    plain = PlainPassword("Password1"),
                    hasher = mockk { every { hash(any()) } returns "hashed" },
                    createdAt = clock.instant(),
                )
            coEvery { users.findByEmail(email) } returns user
            coEvery { tokens.deleteByUserId(fixedUserId) } just Runs
            coEvery { tokens.save(any()) } just Runs
            coEvery { emailSender.sendPasswordResetEmail(email, fixedToken) } just Runs

            val result = handler.handle(RequestPasswordResetCommand(validEmail))

            result.shouldBeRight(Unit)
            coVerify(exactly = 1) { tokens.deleteByUserId(fixedUserId) }
            coVerify(exactly = 1) { tokens.save(any()) }
            coVerify(exactly = 1) { emailSender.sendPasswordResetEmail(email, fixedToken) }
        }

        should("should return Unit silently when user does not exist") {
            coEvery { users.findByEmail(Email.create(validEmail)) } returns null

            val result = handler.handle(RequestPasswordResetCommand(validEmail))

            result.shouldBeRight(Unit)
            coVerify(exactly = 0) { tokens.save(any()) }
            coVerify(exactly = 0) { emailSender.sendPasswordResetEmail(any(), any()) }
        }

        should("should return InvalidEmail when email is invalid") {
            val result = handler.handle(RequestPasswordResetCommand("not-an-email"))

            result.shouldBeLeft(RequestPasswordResetError.InvalidEmail)
            coVerify(exactly = 0) { users.findByEmail(any()) }
        }

        should("should return RequestPasswordResetFailed when token save throws") {
            val email = Email.create(validEmail)
            val user =
                User.register(
                    userId = fixedUserId,
                    email = email,
                    plain = PlainPassword("Password1"),
                    hasher = mockk { every { hash(any()) } returns "hashed" },
                    createdAt = clock.instant(),
                )
            coEvery { users.findByEmail(email) } returns user
            coEvery { tokens.deleteByUserId(fixedUserId) } just Runs
            coEvery { tokens.save(any()) } throws RuntimeException("DB is down")

            val result = handler.handle(RequestPasswordResetCommand(validEmail))

            result.shouldBeLeft(RequestPasswordResetError.RequestPasswordResetFailed)
        }

        should("should return RequestPasswordResetFailed when deleteByUserId throws") {
            val email = Email.create(validEmail)
            val user =
                User.register(
                    userId = fixedUserId,
                    email = email,
                    plain = PlainPassword("Password1"),
                    hasher = mockk { every { hash(any()) } returns "hashed" },
                    createdAt = clock.instant(),
                )
            coEvery { users.findByEmail(email) } returns user
            coEvery { tokens.deleteByUserId(fixedUserId) } throws RuntimeException("DB error")

            val result = handler.handle(RequestPasswordResetCommand(validEmail))

            result.shouldBeLeft(RequestPasswordResetError.RequestPasswordResetFailed)
            coVerify(exactly = 0) { tokens.save(any()) }
        }
    })
