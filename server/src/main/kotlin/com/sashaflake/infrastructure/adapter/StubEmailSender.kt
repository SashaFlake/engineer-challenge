package com.sashaflake.infrastructure.adapter

import EmailSender
import auth.model.user.Email
import org.slf4j.LoggerFactory

class StubEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(StubEmailSender::class.java)

    override suspend fun sendPasswordResetEmail(
        email: Email,
        token: String
    ) {
        log.info("[STUB] Password reset email → ${email.value}, token=$token")
    }
}
