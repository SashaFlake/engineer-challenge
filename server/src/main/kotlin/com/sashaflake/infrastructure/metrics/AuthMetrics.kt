package com.sashaflake.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val SUSPICIOUS_THRESHOLD = 5
class AuthMetrics(
    private val registry: MeterRegistry
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val ipFailureCounts = ConcurrentHashMap<String, AtomicInteger>()

    private val ipWindowCounts = ConcurrentHashMap<String, AtomicInteger>()

    val registerSuccess: Counter =
        Counter
            .builder("auth_register_total")
            .description("Total user registration attempts")
            .tag("result", "success")
            .register(registry)

    val registerError: Counter =
        Counter
            .builder("auth_register_total")
            .description("Total user registration attempts")
            .tag("result", "error")
            .register(registry)

    val loginSuccess: Counter =
        Counter
            .builder("auth_login_total")
            .description("Total login attempts")
            .tag("result", "success")
            .tag("error", "none")
            .register(registry)

    val loginInvalidCredentials: Counter =
        Counter
            .builder("auth_login_total")
            .description("Total login attempts")
            .tag("result", "error")
            .tag("error", "invalid_credentials")
            .register(registry)

    val loginAccountLocked: Counter =
        Counter
            .builder("auth_login_total")
            .description("Total login attempts")
            .tag("result", "error")
            .tag("error", "account_locked")
            .register(registry)

    fun recordLoginFailure(
        ip: String,
        error: String
    ) {
        val count =
            ipFailureCounts
                .getOrPut(ip) { AtomicInteger(0) }
                .incrementAndGet()

        if (count >= SUSPICIOUS_THRESHOLD) {
            Counter.builder("auth_login_suspicious_total")
                .description("Login failures from suspicious IPs")
                .tag("ip", ip)
                .tag("error", error)
                .register(registry)
                .increment()

            val windowCount = ipWindowCounts.getOrPut(ip) { AtomicInteger(0) }
            windowCount.incrementAndGet()

            registry.gauge(
                "auth_login_failures_window",
                listOf(io.micrometer.core.instrument.Tag.of("ip", ip)),
                windowCount
            ) { it.get().toDouble() }

            scope.launch {
                delay(60_000)
                windowCount.decrementAndGet()
            }
        }
    }

    val passwordResetRequestTotal: Counter =
        Counter
            .builder("auth_password_reset_request_total")
            .description("Total password reset requests")
            .tag("result", "success")
            .register(registry)

    fun recordPasswordResetRequest(ip: String) {
        val count =
            ipFailureCounts
                .getOrPut("reset:$ip") { AtomicInteger(0) }
                .incrementAndGet()

        if (count >= SUSPICIOUS_THRESHOLD) {
            Counter.builder("auth_password_reset_suspicious_total")
                .description("Password reset requests from suspicious IPs")
                .tag("ip", ip)
                .register(registry)
                .increment()
        }
    }

    val passwordResetSuccess: Counter =
        Counter
            .builder("auth_password_reset_total")
            .description("Total password reset completions")
            .tag("result", "success")
            .register(registry)

    val passwordResetError: Counter =
        Counter
            .builder("auth_password_reset_total")
            .description("Total password reset completions")
            .tag("result", "error")
            .register(registry)
}
