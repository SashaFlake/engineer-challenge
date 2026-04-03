import auth.model.user.UserId

interface IdGenerator {
    fun generate(): UserId
}
