import auth.command.RegisterUserCommand
import auth.command.RegisterUserError
import auth.command.RegisterUserHandler
import auth.model.user.Email
import auth.model.user.UserId
import auth.port.PasswordHasher
import auth.port.UserRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FeatureSpec
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID



class RegisterUserHandlerTest : FeatureSpec({
    val exampleEmail = "new@example.com"
    val takenEmail = "taken@example.com"

    val users = mockk<UserRepository>()
    val hasher = mockk<PasswordHasher>()
    val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"))
    val idGenerator = mockk<IdGenerator>()

    val handler = RegisterUserHandler(
        users = users,
        hasher = hasher,
        clock = clock,
        idGenerator = idGenerator,
    )

    val fixedId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    beforeEach {
        clearMocks(users, hasher, idGenerator)  // сброс всех стабов и вызовов
        every { idGenerator.generate() } returns fixedId
        every { hasher.hash(any()) } returns "hashed-password"
        every { hasher.verify(any(), any()) } returns false
    }

    feature("user registration") {

        scenario("should returns user id when happy path") {
            val expectedEmail = Email.create(exampleEmail)
            coEvery { users.existsByEmail(expectedEmail) } returns false
            coEvery { users.save(any()) } just Runs

            val result = handler.handle(RegisterUserCommand(exampleEmail, "Password1"))

            result.shouldBeRight(fixedId)
            coVerify(exactly = 1) { users.save(any()) }
        }

        scenario("should return UserAlreadyExists when email already exists") {
            coEvery { users.existsByEmail(Email.create(takenEmail)) } returns true

            val result = handler.handle(RegisterUserCommand(takenEmail, "Password1"))

            result.shouldBeLeft(RegisterUserError.UserAlreadyExists)
            coVerify(exactly = 0) { users.save(any()) }
        }

        scenario("should return InvalidEmail when email is invalid") {
            val result = handler.handle(RegisterUserCommand("not-an-email", "Password1"))

            result.shouldBeLeft(RegisterUserError.InvalidEmail)
            coVerify(exactly = 0) { users.existsByEmail(any()) }
        }

        scenario("should return UserCreationFailed when repository save throws") {
            coEvery { users.existsByEmail(any()) } returns false
            coEvery { users.save(any()) } throws RuntimeException("DB is down")

            val result = handler.handle(RegisterUserCommand(exampleEmail, "Password1"))

            result.shouldBeLeft(RegisterUserError.UserCreationFailed)
        }

        scenario("should return UserCreationFailed when repository throws an exception") {
            coEvery { users.existsByEmail(any()) } returns false
            coEvery { users.save(any()) } throws RuntimeException("DB is down")

            val result = handler.handle(RegisterUserCommand(exampleEmail, "Password1"))

            result.shouldBeLeft(RegisterUserError.UserCreationFailed)
        }

    }
})
