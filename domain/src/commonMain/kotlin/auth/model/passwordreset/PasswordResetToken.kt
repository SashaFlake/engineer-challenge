package auth.model.passwordreset

import auth.model.user.UserId
import java.time.Instant

data class PasswordResetToken(
    val value: String,
    val userId: UserId,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)

    companion object {
        private const val TTL_SECONDS = 3600L

        fun create(value: String, userId: UserId, now: Instant): PasswordResetToken =
            PasswordResetToken(
                value = value,
                userId = userId,
                expiresAt = now.plusSeconds(TTL_SECONDS),
            )
    }
}
