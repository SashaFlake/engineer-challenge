package com.sashaflake.infrastructure.di

import EmailSender
import IdGenerator
import auth.command.RegisterUserHandler
import auth.command.RequestPasswordResetHandler
import auth.command.ResetPasswordHandler
import auth.port.PasswordHasher
import auth.port.PasswordResetTokenGenerator
import auth.port.PasswordResetTokenRepository
import auth.port.UserRepository
import com.sashaflake.infrastructure.adapter.*
import org.koin.dsl.module
import java.time.Clock

val appModule = module {
    single<UserRepository> { InMemoryUserRepository() }
    single<PasswordHasher> { BCryptPasswordHasher() }
    single<PasswordResetTokenRepository> { InMemoryPasswordResetTokenRepository() }
    single<EmailSender> { StubEmailSender() }
    single<PasswordResetTokenGenerator> { UuidPasswordResetTokenGenerator() }
    single<IdGenerator> { UuidIdGenerator() }
    single<Clock> { Clock.systemUTC() }

    single { RegisterUserHandler(get(), get(), get(), get()) }
    single { RequestPasswordResetHandler(get(), get(), get(), get(), get()) }
    single { ResetPasswordHandler(get(), get(), get(), get()) }
}
