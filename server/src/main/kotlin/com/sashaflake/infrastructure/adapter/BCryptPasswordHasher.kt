package com.sashaflake.infrastructure.adapter

import auth.port.PasswordHasher
import org.mindrot.jbcrypt.BCrypt

class BCryptPasswordHasher : PasswordHasher {
    override fun hash(plain: String): String = BCrypt.hashpw(plain, BCrypt.gensalt())

    override fun verify(
        plain: String,
        hash: String
    ): Boolean = BCrypt.checkpw(plain, hash)
}
