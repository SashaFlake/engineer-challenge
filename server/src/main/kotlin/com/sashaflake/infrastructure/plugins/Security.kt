package com.sashaflake.infrastructure.plugins

import io.ktor.server.application.*

// CSRF protection is not applicable for a pure API backend.
// Endpoints are protected via Authorization header (Bearer tokens).
fun Application.configureSecurity() = Unit
