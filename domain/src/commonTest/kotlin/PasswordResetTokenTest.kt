import auth.model.passwordreset.PasswordResetToken
import auth.model.user.UserId
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class PasswordResetTokenTest :
    ShouldSpec({

        val userId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val now = Instant.parse("2026-01-01T00:00:00Z")

        fun token() = PasswordResetToken.create(value = "token-value", userId = userId, now = now)

        should("not be expired immediately after creation") {
            token().isExpired(now) shouldBe false
        }

        should("not be expired at 14 minutes 59 seconds") {
            token().isExpired(now.plusSeconds(899)) shouldBe false
        }

        should("be expired exactly at TTL boundary (15 minutes)") {
            token().isExpired(now.plusSeconds(900)) shouldBe false
        }

        should("be expired after 15 minutes") {
            token().isExpired(now.plusSeconds(901)) shouldBe true
        }

        should("be expired long after creation") {
            token().isExpired(now.plusSeconds(86400)) shouldBe true
        }
    })
