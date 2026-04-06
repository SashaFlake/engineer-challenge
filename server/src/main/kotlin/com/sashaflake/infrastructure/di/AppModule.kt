package com.sashaflake.infrastructure.di

import EmailSender
import IdGenerator
import auth.command.LoginUserHandler
import auth.command.RegisterUserHandler
import auth.command.RequestPasswordResetHandler
import auth.command.ResetPasswordHandler
import auth.port.PasswordHasher
import auth.port.PasswordResetTokenGenerator
import auth.port.PasswordResetTokenRepository
import auth.port.TokenIssuer
import auth.port.UserRepository
import com.sashaflake.infrastructure.adapter.BCryptPasswordHasher
import com.sashaflake.infrastructure.adapter.InMemoryPasswordResetTokenRepository
import com.sashaflake.infrastructure.adapter.InMemoryUserRepository
import com.sashaflake.infrastructure.adapter.JwtTokenIssuer
import com.sashaflake.infrastructure.adapter.StubEmailSender
import com.sashaflake.infrastructure.adapter.UuidIdGenerator
import com.sashaflake.infrastructure.adapter.UuidPasswordResetTokenGenerator
import com.sashaflake.infrastructure.metrics.AuthMetrics
import com.sashaflake.infrastructure.persistence.dragonfly.DragonflyPasswordResetTokenRepository
import com.sashaflake.infrastructure.persistence.dragonfly.DragonflyUserRepository
import com.sashaflake.infrastructure.plugins.appMicrometerRegistry
import io.ktor.server.application.Application
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.time.Clock

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun appModule(app: Application) =
    module {
        single<PasswordHasher> { BCryptPasswordHasher() }
        single<EmailSender> { StubEmailSender() }
        single<PasswordResetTokenGenerator> { UuidPasswordResetTokenGenerator() }
        single<IdGenerator> { UuidIdGenerator() }
        single<Clock> { Clock.systemUTC() }
        single<TokenIssuer> {
            JwtTokenIssuer(
                secret = app.environment.config.property("jwt.secret").getString(),
                issuer = app.environment.config.property("jwt.issuer").getString(),
                audience = app.environment.config.property("jwt.audience").getString(),
            )
        }
        single { AuthMetrics(appMicrometerRegistry) }

        val storageBackend = app.environment.config
            .propertyOrNull("storage.backend")
            ?.getString() ?: "memory"

        when (storageBackend) {
            "dragonfly" -> {
                single {
                    val host = app.environment.config.property("storage.dragonfly.host").getString()
                    val port = app.environment.config.property("storage.dragonfly.port").getString()
                    RedisClient.create("redis://$host:$port")
                } onClose { it?.shutdown() }
                single<StatefulRedisConnection<String, String>> {
                    get<RedisClient>().connect()
                } onClose { it?.close() }
                single<UserRepository> { DragonflyUserRepository(get()) }
                single<PasswordResetTokenRepository> { DragonflyPasswordResetTokenRepository(get()) }
            }
            else -> {
                single<UserRepository> { InMemoryUserRepository() }
                single<PasswordResetTokenRepository> { InMemoryPasswordResetTokenRepository() }
            }
        }

        single { RegisterUserHandler(get(), get(), get(), get()) }
        single { RequestPasswordResetHandler(get(), get(), get(), get(), get()) }
        single { ResetPasswordHandler(get(), get(), get(), get()) }
        single { LoginUserHandler(get(), get(), get(), get()) }
    }
