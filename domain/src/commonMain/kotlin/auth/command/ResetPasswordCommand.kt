package auth.command

data class ResetPasswordCommand(
    val token: String,
    val newPassword: String,
)
