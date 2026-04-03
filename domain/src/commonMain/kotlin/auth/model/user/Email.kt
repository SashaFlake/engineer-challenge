package auth.model.user

@JvmInline
value class Email private constructor(
    val value: String
) {
    companion object {
        private val PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun create(raw: String): Email {
            val normalized = raw.trim().lowercase()
            when (PATTERN.matches(normalized)) {
                true -> return Email(normalized)
                else -> throw IllegalArgumentException("Invalid email format: $raw")
            }
        }

        fun fromStorage(value: String): Email = Email(value)
    }

    override fun toString(): String = value
}
