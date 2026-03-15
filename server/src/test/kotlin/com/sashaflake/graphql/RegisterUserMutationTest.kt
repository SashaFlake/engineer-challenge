package com.sashaflake.graphql

import com.sashaflake.module
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.koin.core.context.stopKoin

class RegisterUserMutationTest : DescribeSpec({

    afterEach { stopKoin() }

    fun graphqlBody(query: String): Map<String, String> = mapOf("query" to query)

    fun parseRegisterUser(responseText: String): JsonObject =
        Json.parseToJsonElement(responseText)
            .jsonObject["data"]!!
            .jsonObject["registerUser"]!!
            .jsonObject

    describe("registerUser mutation") {

        it("returns userId on successful registration") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            registerUser(email: "alice@example.com", password: "Password1") {
                                success userId error
                            }
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                val data = parseRegisterUser(response.bodyAsText())
                data["success"]!!.jsonPrimitive.boolean shouldBe true
                data["userId"]!!.jsonPrimitive.content.shouldNotBeEmpty()
                data["error"] shouldBe JsonNull
            }
        }

        it("returns error on duplicate email") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val body = graphqlBody("""
                    mutation {
                        registerUser(email: "bob@example.com", password: "Password1") {
                            success userId error
                        }
                    }
                """.trimIndent())

                client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

                response.status shouldBe HttpStatusCode.OK
                val data = parseRegisterUser(response.bodyAsText())
                data["success"]!!.jsonPrimitive.boolean shouldBe false
                data["error"]!!.jsonPrimitive.content.shouldNotBeEmpty()
            }
        }

        it("returns error on invalid email format") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            registerUser(email: "not-an-email", password: "Password1") {
                                success userId error
                            }
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                val data = parseRegisterUser(response.bodyAsText())
                data["success"]!!.jsonPrimitive.boolean shouldBe false
                data["error"]!!.jsonPrimitive.content.shouldNotBeEmpty()
            }
        }

        it("returns error on weak password") {
            testApplication {
                application { module() }
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }

                val response = client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(graphqlBody("""
                        mutation {
                            registerUser(email: "carol@example.com", password: "weak") {
                                success userId error
                            }
                        }
                    """.trimIndent()))
                }

                response.status shouldBe HttpStatusCode.OK
                val data = parseRegisterUser(response.bodyAsText())
                data["success"]!!.jsonPrimitive.boolean shouldBe false
                data["error"]!!.jsonPrimitive.content.shouldNotBeEmpty()
            }
        }
    }
})
