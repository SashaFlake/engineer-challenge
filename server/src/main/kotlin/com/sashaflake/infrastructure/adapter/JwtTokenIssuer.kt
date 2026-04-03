package com.sashaflake.infrastructure.adapter

import auth.model.user.UserId
import auth.port.TokenIssuer
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtTokenIssuer(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val expirationMs: Long = 24 * 60 * 60 * 1000L, // 24h
) : TokenIssuer {
    private val algorithm = Algorithm.HMAC256(secret)

    override fun issue(userId: UserId): String =
        JWT
            .create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.value.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + expirationMs))
            .sign(algorithm)
}
