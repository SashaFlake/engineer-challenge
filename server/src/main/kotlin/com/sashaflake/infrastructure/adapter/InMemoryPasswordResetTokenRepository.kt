package com.sashaflake.infrastructure.adapter

import auth.model.passwordreset.PasswordResetToken
import auth.model.user.UserId
import auth.port.PasswordResetTokenRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryPasswordResetTokenRepository : PasswordResetTokenRepository {
    private val store = ConcurrentHashMap<String, PasswordResetToken>()

    override suspend fun save(token: PasswordResetToken) {
        store[token.value] = token
    }

    override suspend fun findByValue(value: String): PasswordResetToken? = store[value]

    override suspend fun deleteByUserId(userId: UserId) {
        store.entries.removeIf { it.value.userId == userId }
    }
}
