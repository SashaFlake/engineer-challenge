package com.sashaflake.infrastructure.adapter

import auth.model.user.Email
import auth.model.user.User
import auth.model.user.UserId
import auth.port.UserRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryUserRepository : UserRepository {
    private val store = ConcurrentHashMap<UserId, User>()

    override suspend fun findById(id: UserId): User? = store[id]

    override suspend fun findByEmail(email: Email): User? = store.values.firstOrNull { it.email == email }

    override suspend fun save(user: User) {
        store[user.id] = user
    }

    override suspend fun existsByEmail(email: Email): Boolean = store.values.any { it.email == email }
}
