package auth.port

interface PasswordResetTokenGenerator {
    fun generate(): String
}
