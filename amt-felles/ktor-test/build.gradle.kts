repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    api(project(":amt-lib:lib:testing"))
    implementation(project(":amt-felles:ktor"))

    implementation(libs.kafka.clients)
    implementation(libs.logback.classic)

    implementation(libs.testcontainers.postgresql)

    implementation(libs.bundles.database)

    implementation(libs.caffeine)

    api(libs.bundles.kotest)

    api(libs.junit.jupiter.params)
    api(libs.kotlin.test.junit5)
    api(libs.mockk)
}

kotlin {
    jvmToolchain(21)
}

ktlint {
    version = libs.versions.ktlint.cli.version
}
