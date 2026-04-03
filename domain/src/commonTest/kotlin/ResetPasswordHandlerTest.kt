import auth.command.ResetPasswordCommand
import auth.command.ResetPasswordError
import auth.command.ResetPasswordHandler
import auth.model.passwordreset.PasswordResetToken
import auth.model.user.Email
import auth.model.user.PlainPassword
import auth.model.user.User
import auth.model.user.UserId
import auth.port.PasswordHasher
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
import java.time.temporal.ChronoUnit
import java.util.UUID

class ResetPasswordHandlerTest : ShouldSpec({
    val fixedUserId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    val validTokenValue = "valid-reset-token"
    val newPassword = "NewPassword1"
    val fixedNow = Instant.parse("2026-01-01T00:00:00Z")

    val users = mockk<UserRepository>()
    val tokens = mockk<PasswordResetTokenRepository>()
    val hasher = mockk<PasswordHasher>()
    val clock = Clock.fixed(fixedNow, ZoneId.of("UTC"))

    val handler = ResetPasswordHandler(
        users = users,
        tokens = tokens,
        hasher = hasher,
        clock = clock,
    )

    val activeToken = PasswordResetToken(
        value = validTokenValue,
        userId = fixedUserId,
        expiresAt = fixedNow.plus(1, ChronoUnit.HOURS),
    )

    val expiredToken = PasswordResetToken(
        value = "expired-token",
        userId = fixedUserId,
        expiresAt = fixedNow.minusSeconds(1),
    )

    beforeEach {
        clearMocks(users, tokens, hasher)
        every { hasher.hash(any()) } returns "hashed-new-password"
        every { hasher.verify(any(), any()) } returns false
    }

    should("should return Unit and change password when happy path") {
        val email = Email.create("user@example.com")
        val user = User.register(
            userId = fixedUserId,
            email = email,
            plain = PlainPassword("OldPassword1"),
            hasher = hasher,
            createdAt = fixedNow,
        )
        coEvery { tokens.findByValue(validTokenValue) } returns activeToken
        coEvery { users.findById(fixedUserId) } returns user
        coEvery { users.save(user) } just Runs
        coEvery { tokens.deleteByUserId(fixedUserId) } just Runs

        val result = handler.handle(ResetPasswordCommand(validTokenValue, newPassword))

        result.shouldBeRight(Unit)
        coVerify(exactly = 1) { users.save(user) }
        coVerify(exactly = 1) { tokens.deleteByUserId(fixedUserId) }
    }

    should("should return TokenNotFound when token does not exist") {
        coEvery { tokens.findByValue("unknown-token") } returns null

        val result = handler.handle(ResetPasswordCommand("unknown-token", newPassword))

        result.shouldBeLeft(ResetPasswordError.TokenNotFound)
        coVerify(exactly = 0) { users.findById(any()) }
    }

    should("should return TokenExpired when token is expired") {
        coEvery { tokens.findByValue("expired-token") } returns expiredToken

        val result = handler.handle(ResetPasswordCommand("expired-token", newPassword))

        result.shouldBeLeft(ResetPasswordError.TokenExpired)
        coVerify(exactly = 0) { users.findById(any()) }
    }

    should("should return UserNotFound when user does not exist") {
        coEvery { tokens.findByValue(validTokenValue) } returns activeToken
        coEvery { users.findById(fixedUserId) } returns null

        val result = handler.handle(ResetPasswordCommand(validTokenValue, newPassword))

        result.shouldBeLeft(ResetPasswordError.UserNotFound)
        coVerify(exactly = 0) { users.save(any()) }
    }

    should("should return WeakPassword when changePassword throws") {
        val email = Email.create("user@example.com")
        val user = User.register(
            userId = fixedUserId,
            email = email,
            plain = PlainPassword("OldPassword1"),
            hasher = hasher,
            createdAt = fixedNow,
        )
        coEvery { tokens.findByValue(validTokenValue) } returns activeToken
        coEvery { users.findById(fixedUserId) } returns user
        every { hasher.hash(any()) } throws IllegalArgumentException("Password too weak")

        val result = handler.handle(ResetPasswordCommand(validTokenValue, "weak"))

        result.shouldBeLeft(ResetPasswordError.WeakPassword)
        coVerify(exactly = 0) { users.save(any()) }
    }

    should("should return SaveFailed when user repository save throws") {
        val email = Email.create("user@example.com")
        val user = User.register(
            userId = fixedUserId,
            email = email,
            plain = PlainPassword("OldPassword1"),
            hasher = hasher,
            createdAt = fixedNow,
        )
        coEvery { tokens.findByValue(validTokenValue) } returns activeToken
        coEvery { users.findById(fixedUserId) } returns user
        coEvery { users.save(user) } throws RuntimeException("DB is down")

        val result = handler.handle(ResetPasswordCommand(validTokenValue, newPassword))

        result.shouldBeLeft(ResetPasswordError.SaveFailed)
        coVerify(exactly = 0) { tokens.deleteByUserId(any()) }
    }
})