package auth.command

data class LoginUserCommand(
    val email: String,
    val password: String
)
