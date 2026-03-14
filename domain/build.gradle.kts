plugins {
    kotlin("multiplatform") version "2.3.0"
}
val arrow_version = project.extra["arrow_version"] as String
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("io.arrow-kt:arrow-core:${arrow_version}")
        }
    }
}
