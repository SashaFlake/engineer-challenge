plugins {
    kotlin("multiplatform") version "2.3.0"
}
val arrow_version = project.extra["arrow_version"] as String
val kotest_version = project.extra["kotest_version"] as String
val mockk_version = project.extra["mockk_version"] as String
kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()  // вот это было пропущено
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("io.arrow-kt:arrow-core:${arrow_version}")
        }
        jvmTest.dependencies {
            implementation("io.kotest:kotest-runner-junit5:$kotest_version")
            implementation("io.kotest:kotest-assertions-core:$kotest_version")
            implementation("io.kotest.extensions:kotest-assertions-arrow:2.0.0")
            implementation("io.mockk:mockk:$mockk_version")
        }
    }
}
