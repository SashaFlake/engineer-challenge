package auth.port

import auth.model.passwordreset.PasswordResetToken
import auth.model.user.UserId

interface PasswordResetTokenRepository {
    suspend fun save(token: PasswordResetToken)

    suspend fun findByValue(value: String): PasswordResetToken?

    suspend fun deleteByUserId(userId: UserId)
}
