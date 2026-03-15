package com.sashaflake.infrastructure.adapter

import auth.port.PasswordResetTokenGenerator
import java.util.UUID

class UuidPasswordResetTokenGenerator : PasswordResetTokenGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}
