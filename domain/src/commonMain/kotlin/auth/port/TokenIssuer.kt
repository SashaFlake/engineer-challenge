package auth.port

import auth.model.user.UserId

interface TokenIssuer {
    fun issue(userId: UserId): String
}
