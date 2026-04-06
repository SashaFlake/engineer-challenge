package com.sashaflake.infrastructure.persistence.dragonfly

import auth.model.passwordreset.PasswordResetToken
import auth.model.user.UserId
import auth.port.PasswordResetTokenRepository
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class DragonflyPasswordResetTokenRepository(
    private val connection: StatefulRedisConnection<String, String>,
) : PasswordResetTokenRepository {

    private val commands = connection.coroutines()

    override suspend fun save(token: PasswordResetToken) {
        val ttl = maxOf(0L, Instant.now().until(token.expiresAt, ChronoUnit.SECONDS))
        commands.set("reset:${token.value}", "${token.userId.value}|${token.expiresAt}")
        commands.expire("reset:${token.value}", ttl)
        commands.set("user:${token.userId.value}", token.value)
        commands.expire("user:${token.userId.value}", ttl)
    }

    override suspend fun findByValue(value: String): PasswordResetToken? {
        val raw = commands.get("reset:$value") ?: return null
        val (userIdStr, expiresAtStr) = raw.split("|", limit = 2)
        return PasswordResetToken(
            value = value,
            userId = UserId(UUID.fromString(userIdStr)),
            expiresAt = Instant.parse(expiresAtStr),
        )
    }

    override suspend fun deleteByUserId(userId: UserId) {
        val token = commands.get("user:${userId.value}") ?: return
        commands.del("reset:$token", "user:${userId.value}")
    }
}
