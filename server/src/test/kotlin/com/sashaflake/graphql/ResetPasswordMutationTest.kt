package com.sashaflake.graphql

import com.sashaflake.module
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.koin.core.context.stopKoin

class ResetPasswordMutationTest : DescribeSpec({

    afterEach { stopKoin() }

    fun graphqlBody(query: String): Map<String, String> = mapOf("query" to query)

    fun parseRequestPasswordReset(responseText: String): JsonElement =
        Json.parseToJsonElement(responseText)
            .jsonObject["data"]!!
            .jsonObject["requestPasswordReset"]!!

    fun parseResetPassword(responseText: String): JsonElement =
        Json.parseToJsonElement(responseText)
            .jsonObject["data"]!!
            .jsonObject["resetPassword"]!!

    describe("requestPasswordReset mutation") {

        it("returns true for existing user email — does not leak user existence") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            registerUser(email: "reset-user@example.com", password: "Password1") {
                                success userId error
                            }
                        }
                    """.trimIndent()))
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            requestPasswordReset(email: "reset-user@example.com")
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                parseRequestPasswordReset(response.bodyAsText()).jsonPrimitive.boolean shouldBe true
            }
        }

        it("returns true even for non-existing email — avoids user enumeration") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            requestPasswordReset(email: "ghost@example.com")
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                parseRequestPasswordReset(response.bodyAsText()).jsonPrimitive.boolean shouldBe true
            }
        }
    }

    describe("resetPassword mutation") {

        it("returns false for invalid (non-existent) token") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            resetPassword(token: "invalid-token-00000000", newPassword: "NewPassword1")
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                parseResetPassword(response.bodyAsText()).jsonPrimitive.boolean shouldBe false
            }
        }

        it("returns false for empty token") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            resetPassword(token: "", newPassword: "NewPassword1")
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                parseResetPassword(response.bodyAsText()).jsonPrimitive.boolean shouldBe false
            }
        }

        it("returns false when new password is weak") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            resetPassword(token: "some-valid-looking-token", newPassword: "weak")
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                parseResetPassword(response.bodyAsText()).jsonPrimitive.boolean shouldBe false
            }
        }
    }
})