package com.sashaflake.infrastructure.adapter

import IdGenerator
import auth.model.user.UserId

class UuidIdGenerator : IdGenerator {
    override fun generate(): UserId = UserId.generate()
}
