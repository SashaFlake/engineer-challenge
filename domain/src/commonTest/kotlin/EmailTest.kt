import auth.model.user.Email
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class EmailTest :
    ShouldSpec({

        should("store a valid email as-is") {
            Email.create("user@example.com").value shouldBe "user@example.com"
        }

        should("lowercase the email") {
            Email.create("User@Example.COM").value shouldBe "user@example.com"
        }

        should("trim surrounding whitespace") {
            Email.create("  user@example.com  ").value shouldBe "user@example.com"
        }

        should("throw when missing @") {
            shouldThrow<IllegalArgumentException> { Email.create("notanemail") }
        }

        should("throw when domain is missing") {
            shouldThrow<IllegalArgumentException> { Email.create("user@") }
        }

        should("throw when domain has no dot") {
            shouldThrow<IllegalArgumentException> { Email.create("user@nodot") }
        }

        should("throw on empty string") {
            shouldThrow<IllegalArgumentException> { Email.create("") }
        }

        should("throw when multiple @ are present") {
            shouldThrow<IllegalArgumentException> { Email.create("a@b@c.com") }
        }

        should("fromStorage preserves value without normalization") {
            Email.fromStorage("raw@value.com").value shouldBe "raw@value.com"
        }

        should("treat emails differing only in case as equal") {
            Email.create("User@Example.com") shouldBe Email.create("user@example.com")
        }
    })
