import auth.model.user.Email

interface EmailSender {
    suspend fun sendPasswordResetEmail(
        email: Email,
        token: String
    )
}
