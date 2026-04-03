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
import io.ktor.server.application.Application
import org.koin.dsl.module
import java.time.Clock

fun appModule(app: Application) =
    module {
        single<UserRepository> { InMemoryUserRepository() }
        single<PasswordHasher> { BCryptPasswordHasher() }
        single<PasswordResetTokenRepository> { InMemoryPasswordResetTokenRepository() }
        single<EmailSender> { StubEmailSender() }
        single<PasswordResetTokenGenerator> { UuidPasswordResetTokenGenerator() }
        single<IdGenerator> { UuidIdGenerator() }
        single<Clock> { Clock.systemUTC() }
        single<TokenIssuer> {
            JwtTokenIssuer(
                secret =
                    app.environment.config
                        .property("jwt.secret")
                        .getString(),
                issuer =
                    app.environment.config
                        .property("jwt.issuer")
                        .getString(),
                audience =
                    app.environment.config
                        .property("jwt.audience")
                        .getString(),
            )
        }

        single { RegisterUserHandler(get(), get(), get(), get()) }
        single { RequestPasswordResetHandler(get(), get(), get(), get(), get()) }
        single { ResetPasswordHandler(get(), get(), get(), get()) }
        single { LoginUserHandler(get(), get(), get(), get()) }
    }
