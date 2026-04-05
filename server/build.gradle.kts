val kotlinVersion: String by project
val logbackVersion: String by project
val prometheusVersion: String by project
val arrowVersion: String by project
val koinVersion: String by project
val kotestVersion: String by project
val graphqlKotlinVersion: String by project
val lettuceVersion: String by project

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

configurations.all {
    resolutionStrategy {
        force("io.insert-koin:koin-core:$koinVersion")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":domain"))
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-auto-head-response")
    implementation("io.ktor:ktor-server-caching-headers")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-forwarded-header")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-csrf")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-metrics-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation("com.expediagroup:graphql-kotlin-ktor-server:$graphqlKotlinVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.1")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("io.ktor:ktor-serialization-jackson")
    testImplementation("io.insert-koin:koin-test:$koinVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}
