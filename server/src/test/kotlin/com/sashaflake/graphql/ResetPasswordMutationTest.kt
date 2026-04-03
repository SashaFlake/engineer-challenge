package com.sashaflake.graphql

import com.sashaflake.module
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.stopKoin

class ResetPasswordMutationTest :
    DescribeSpec({

        lateinit var app: TestApplication
        lateinit var client: HttpClient

        beforeSpec {
            app =
                TestApplication {
                    environment {
                        config =
                            MapApplicationConfig(
                                "jwt.secret" to "test-secret-key-for-testing-only-minimum-32-chars",
                                "jwt.issuer" to "test-issuer",
                                "jwt.audience" to "test-audience",
                                "postgres.url" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                                "postgres.user" to "sa",
                                "postgres.password" to ""
                            )
                    }
                    application { module() }
                }
            app.start()
            client =
                app.createClient {
                    install(ContentNegotiation) { jackson() }
                }
        }

        afterSpec {
            client.close()
            app.stop()
            stopKoin()
        }

        fun graphqlBody(query: String): Map<String, String> = mapOf("query" to query)

        fun parseRequestPasswordReset(responseText: String): JsonElement =
            Json
                .parseToJsonElement(responseText)
                .jsonObject["data"]!!
                .jsonObject["requestPasswordReset"]!!

        fun parseResetPassword(responseText: String): JsonElement =
            Json
                .parseToJsonElement(responseText)
                .jsonObject["data"]!!
                .jsonObject["resetPassword"]!!

        describe("requestPasswordReset mutation") {

            it("returns true for existing user email — does not leak user existence") {
                client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        graphqlBody(
                            """
                            mutation {
                                registerUser(email: "reset-user@example.com", password: "Password1") {
                                    success userId error
                                }
                            }
                            """.trimIndent()
                        )
                    )
                }

                val response =
                    client.post("/graphql") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            graphqlBody(
                                """
                                mutation {
                                    requestPasswordReset(email: "reset-user@example.com")
                                }
                                """.trimIndent()
                            )
                        )
                    }

                response.status shouldBe HttpStatusCode.OK
                parseRequestPasswordReset(response.bodyAsText()).jsonPrimitive.boolean shouldBe true
            }

            it("returns true even for non-existing email — avoids user enumeration") {
                val response =
                    client.post("/graphql") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            graphqlBody(
                                """
                                mutation {
                                    requestPasswordReset(email: "ghost@example.com")
                                }
                                """.trimIndent()
                            )
                        )
                    }

                response.status shouldBe HttpStatusCode.OK
                parseRequestPasswordReset(response.bodyAsText()).jsonPrimitive.boolean shouldBe true
            }
        }

        describe("resetPassword mutation") {

            it("returns false for invalid (non-existent) token") {
                val response =
                    client.post("/graphql") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            graphqlBody(
                                """
                                mutation {
                                    resetPassword(token: "invalid-token-00000000", newPassword: "NewPassword1")
                                }
                                """.trimIndent()
                            )
                        )
                    }

                response.status shouldBe HttpStatusCode.OK
                parseResetPassword(response.bodyAsText()).jsonPrimitive.boolean shouldBe false
            }

            it("returns false for empty token") {
                val response =
                    client.post("/graphql") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            graphqlBody(
                                """
                                mutation {
                                    resetPassword(token: "", newPassword: "NewPassword1")
                                }
                                """.trimIndent()
                            )
                        )
                    }

                response.status shouldBe HttpStatusCode.OK
                parseResetPassword(response.bodyAsText()).jsonPrimitive.boolean shouldBe false
            }

            it("returns false when new password is weak") {
                val response =
                    client.post("/graphql") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            graphqlBody(
                                """
                                mutation {
                                    resetPassword(token: "some-valid-looking-token", newPassword: "weak")
                                }
                                """.trimIndent()
                            )
                        )
                    }

                response.status shouldBe HttpStatusCode.OK
                parseResetPassword(response.bodyAsText()).jsonPrimitive.boolean shouldBe false
            }
        }
    })
