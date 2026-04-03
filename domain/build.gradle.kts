plugins {
    kotlin("multiplatform") version "2.3.0"
}
val arrowVersion = project.extra["arrowVersion"] as String
val kotestVersion = project.extra["kotestVersion"] as String
val mockkVersion = project.extra["mockkVersion"] as String
kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform() // вот это было пропущено
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("io.arrow-kt:arrow-core:$arrowVersion")
        }
        jvmTest.dependencies {
            implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
            implementation("io.kotest:kotest-assertions-core:$kotestVersion")
            implementation("io.kotest.extensions:kotest-assertions-arrow:2.0.0")
            implementation("io.mockk:mockk:$mockkVersion")
        }
    }
}
