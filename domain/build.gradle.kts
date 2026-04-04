plugins {
    kotlin("multiplatform") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

val arrowVersion = project.extra["arrowVersion"] as String
val kotestVersion = project.extra["kotestVersion"] as String
val mockkVersion = project.extra["mockkVersion"] as String

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
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

kover {
    reports {
        filters {
            excludes {
                // исключаем сгенерированный код
                packages("*.generated")
            }
        }
        total {
            xml { onCheck = true }
            html { onCheck = true }
            verify {
                rule {
                    minBound(60) // минимальный порог покрытия 60%
                }
            }
        }
    }
}

ktlint {
    version.set("1.3.1")
    android.set(false)
    outputToConsole.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}
