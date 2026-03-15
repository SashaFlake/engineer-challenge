package com.sashaflake.presentation.graphql

import com.expediagroup.graphql.server.operations.Query

class HealthQuery : Query {
    fun health(): String = "ok"
}
