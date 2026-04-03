package com.sashaflake.graphql

import com.sashaflake.module
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.koin.core.context.stopKoin

class LoginUserMutationTest : DescribeSpec({

    lateinit var app: TestApplication
    lateinit var client: HttpClient

    beforeSpec {
        app = TestApplication {
            environment {
                config = MapApplicationConfig(
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
        client = app.createClient {
            install(ContentNegotiation) { jackson() }
        }

        client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("query" to """
                mutation {
                    registerUser(email: "login-user@example.com", password: "Password1") {
                        success
                    }
                }
            """.trimIndent()))
        }
    }

    afterSpec {
        client.close()
        app.stop()
        stopKoin()
    }

    fun graphqlBody(query: String): Map<String, String> = mapOf("query" to query)

    fun parseLoginUser(responseText: String): JsonObject =
        Json.parseToJsonElement(responseText)
            .jsonObject["data"]!!
            .jsonObject["loginUser"]!!
            .jsonObject

    describe("loginUser mutation") {

        it("returns token on successful login") {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                setBody(graphqlBody("""
                    mutation {
                        loginUser(email: "login-user@example.com", password: "Password1") {
                            success token error
                        }
                    }
                """.trimIndent()))
            }

            response.status shouldBe HttpStatusCode.OK
            val data = parseLoginUser(response.bodyAsText())
            data["success"]!!.jsonPrimitive.boolean shouldBe true
            data["token"]!!.jsonPrimitive.content.shouldNotBeEmpty()
            data["error"] shouldBe JsonNull
        }

        it("returns error on wrong password") {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                setBody(graphqlBody("""
                    mutation {
                        loginUser(email: "login-user@example.com", password: "WrongPassword1") {
                            success token error
                        }
                    }
                """.trimIndent()))
            }

            response.status shouldBe HttpStatusCode.OK
            val data = parseLoginUser(response.bodyAsText())
            data["success"]!!.jsonPrimitive.boolean shouldBe false
            data["token"] shouldBe JsonNull
            data["error"]!!.jsonPrimitive.content.shouldNotBeEmpty()
        }

        it("returns error on non-existing email") {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                setBody(graphqlBody("""
                    mutation {
                        loginUser(email: "nobody@example.com", password: "Password1") {
                            success token error
                        }
                    }
                """.trimIndent()))
            }

            response.status shouldBe HttpStatusCode.OK
            val data = parseLoginUser(response.bodyAsText())
            data["success"]!!.jsonPrimitive.boolean shouldBe false
            data["token"] shouldBe JsonNull
            data["error"]!!.jsonPrimitive.content.shouldNotBeEmpty()
        }

        it("returns error on invalid email format") {
            val response = client.post("/graphql") {
                contentType(ContentType.Application.Json)
                setBody(graphqlBody("""
                    mutation {
                        loginUser(email: "not-an-email", password: "Password1") {
                            success token error
                        }
                    }
                """.trimIndent()))
            }

            response.status shouldBe HttpStatusCode.OK
            val data = parseLoginUser(response.bodyAsText())
            data["success"]!!.jsonPrimitive.boolean shouldBe false
            data["token"] shouldBe JsonNull
            data["error"]!!.jsonPrimitive.content.shouldNotBeEmpty()
        }
    }
})