package com.sashaflake.infrastructure.persistence.dragonfly

import auth.model.user.Email
import auth.model.user.HashedPassword
import auth.model.user.LoginAttemptGuard
import auth.model.user.User
import auth.model.user.UserId
import auth.port.UserRepository
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.future.await
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class DragonflyUserRepository(
    private val connection: StatefulRedisConnection<String, String>,
) : UserRepository {

    private val commands = connection.coroutines()

    override suspend fun save(user: User) {
        val async = connection.async()
        async.multi()
        async.hset(
            "user:${user.id.value}",
            mapOf(
                "id"             to user.id.value.toString(),
                "email"          to user.email.value,
                "password"       to user.getHashedPassword().value,
                "failedAttempts" to user.getLoginAttemptGuard().failedAttempts.toString(),
                "lockedUntil"    to (user.getLoginAttemptGuard().lockedUntil?.toString() ?: ""),
                "createdAt"      to user.createdAt.toString(),
            )
        )
        async.set("user:email:${user.email.value}", user.id.value.toString())
        async.exec().await()
    }

    override suspend fun findById(id: UserId): User? {
        val fields = commands.hgetall("user:${id.value}")
            .fold(mutableMapOf<String, String>()) { acc, kv ->
                acc.also { it[kv.key] = kv.value }
            }
        if (fields.isEmpty()) return null
        return fields.toUser()
    }

    override suspend fun findByEmail(email: Email): User? {
        val id = commands.get("user:email:${email.value}") ?: return null
        return findById(UserId(UUID.fromString(id)))
    }

    override suspend fun existsByEmail(email: Email): Boolean =
        commands.exists("user:email:${email.value}") == 1L

    private fun Map<String, String>.toUser(): User = User(
        id = UserId(UUID.fromString(getValue("id"))),
        email = Email.fromStorage(getValue("email")),           // ← fromStorage
        hashedPassword = HashedPassword.fromStorage(getValue("password")), // ← fromStorage
        loginAttemptGuard = LoginAttemptGuard(
            failedAttempts = getValue("failedAttempts").toInt(),
            lockedUntil = getValue("lockedUntil")
                .takeIf { it.isNotEmpty() }
                ?.let { Instant.parse(it) },
        ),
        createdAt = Instant.parse(getValue("createdAt")),
    )
}
